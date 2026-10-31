package com.faiza.ai.ai.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import com.faiza.ai.ai.provider.ToolCall
import com.faiza.ai.ai.provider.ToolSpec
import com.faiza.ai.ai.provider.geminiToolsJson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Voice-to-voice conversation with the Gemini Live API: streams the microphone (16 kHz PCM)
 * over a WebSocket and plays the model's spoken reply (24 kHz PCM). Tool calls are executed
 * through [Listener.onToolCall]. Reconnects transparently when Google recycles the connection.
 */
class GeminiLiveSession(
    context: Context,
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val systemPrompt: String,
    private val tools: List<ToolSpec>,
    private val muteMicWhileSpeaking: Boolean,
    private val listener: Listener,
    /** Receives exactly the microphone audio that is sent to Gemini (used by Voice Guardian). */
    private val audioTap: ((ByteArray, Int) -> Unit)? = null,
    /** Screen-recording mode: always use the loudspeaker so a recording can pick her voice up. */
    private val forceSpeaker: Boolean = false,
    /** Boost her voice a little (+6 dB) on the loudspeaker. */
    private val louderVoice: Boolean = true,
    /**
     * Talk through the CALL loudspeaker (like a WhatsApp call on speaker) instead of the media stream: her volume is
     * separate from videos/music, and the phone's echo canceller works properly, so she hears you over herself.
     */
    private val callSpeaker: Boolean = false,
    /** Extra microphone gain for soft speech (1 = off). */
    private val micGain: Float = 1f,
    /**
     * "Stop when I talk" (owner's switch): on the call speaker / a headset the mic stays open the whole time she
     * speaks, and nothing in here is allowed to switch that off by itself.
     */
    private val bargeIn: Boolean = true,
    /**
     * Gaming mode: keep the conversation short (a picture every second fills it fast and slows every answer) and
     * don't let game sounds in the mic count as the user talking.
     */
    private val gamingFast: Boolean = false,
    /**
     * Owner: "WhatsApp/imo ভিডিও কলের মতো লাউডস্পিকারে কথা বলবে". Without a headset her voice always goes to the
     * loudspeaker — never the small ear speaker at the top of the phone.
     */
    private val loudspeaker: Boolean = true
) {
    @Volatile private var useFastCompression = gamingFast

    /** Loudspeaker with the mic open the whole time she talks (WhatsApp-call style). */
    @Volatile private var loudDuplex = false
    /** When the mic was last transcribed (to tell a real interruption from her own echo). */
    @Volatile private var lastInputAt = 0L

    /** In a game the mic is often taken by the game's voice chat: check for it back only now and then. */
    private val micReopenMs get() = if (gamingFast) 6_000L else MIC_REOPEN_MS

    /** Current output usage of [track] (media, or voice-call while another app's call/voice chat is on). */
    @Volatile private var trackUsage = AudioAttributes.USAGE_MEDIA

    /**
     * Gaming: when the game turns its voice chat on, the phone switches to "call" audio and media sound can go
     * quiet or vanish on some phones. Then she follows the call route; back to media when the voice chat is off.
     */
    fun followOtherAppCall(otherCallActive: Boolean) {
        if (stopped || routedBluetooth || routedCallSpeaker || callStreamOnly) return
        val want = if (otherCallActive) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        if (want == trackUsage) return
        val old = track
        track = buildTrack(want)
        trackUsage = want
        old?.runCatching { pause(); flush(); release() }
    }

    enum class State { CONNECTING, LISTENING, SPEAKING, WORKING, MIC_BUSY, CLOSED }

    interface Listener {
        fun onState(state: State)
        fun onUserSpeech(text: String)
        fun onModelSpeech(text: String)
        suspend fun onToolCall(call: ToolCall): String
        fun onFatalError(message: String)

        /** Another app is holding the microphone (or it was taken away); Faiza keeps trying to get it back. */
        fun onMicBlocked(blocked: Boolean) {}
    }

    companion object {
        private const val URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val IN_RATE = 16_000
        private const val OUT_RATE = 24_000
        private const val CHUNK_BYTES = 3_200 // 100 ms of 16 kHz mono PCM16
        private const val MAX_RECONNECTS = 8
        private const val MAX_BACKOFF_MS = 5_000L

        // Microphone taken by another app (Android feeds us silence instead of failing).
        private const val MIC_CHECK_MS = 500L
        private const val MIC_REOPEN_MS = 2_000L
        private const val ZERO_SILENCE_MS = 3_000L

        // Barge-in (talking over Faiza): see BargeInDetector. While she talks we know how loud she is
        // at each moment, so mic audio above her expected echo is the user.
        private const val ECHO_TAIL_MS = 350L          // speaker/room echo right after playback ends
        private const val PREROLL_CHUNKS = 3           // ~300 ms kept so the first syllable isn't lost
        private const val BARGE_SUPPRESS_MS = 600L     // drop the interrupted turn's trailing audio
        private const val OUTPUT_WORDS_MS = 20_000L    // her recent words, to recognise her own echo
        private const val OUT_SEG_BYTES = 2_400        // 50 ms of 24 kHz PCM16 per output level sample
        private const val ECHO_LOOKBACK_MS = 450L      // playback-to-mic latency window
        private const val STOP_HOLD_MS = 8_000L        // after "থামো", never auto-resume the old answer

        private val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + com.faiza.ai.util.CrashReporter.guard)
    private val main = Handler(Looper.getMainLooper())

    private val appContext: Context = context.applicationContext

    @Volatile private var socket: WebSocket? = null
    /** Bumped for every new connection so late callbacks from an older socket are ignored. */
    @Volatile private var generation = 0
    @Volatile private var micBlocked = false
    @Volatile private var setupDone = false
    @Volatile private var stopped = false
    @Volatile private var toolRunning = false
    @Volatile private var playEndAt = 0L
    @Volatile private var bargeSuppressUntil = 0L

    /** Loudness of what Faiza is playing, on the playback timeline. */
    private class OutSegment(val start: Long, val end: Long, val rms: Double)
    private val outSegments = ArrayDeque<OutSegment>()
    @Volatile private var barge = BargeInDetector(hardwareEchoCanceller = true)
    private var bargeAssumesAec = true

    /** What Faiza said recently (for telling her echo apart from the user in transcripts). */
    private class SpokenWord(val at: Long, val word: String)
    private val recentOutputWords = ArrayDeque<SpokenWord>()
    /** Mic transcript collected while Faiza is talking. */
    private val overTalkText = StringBuilder()
    /** Sensitive voice detection on Google's side; dropped automatically if a model rejects it. */
    @Volatile private var useVadConfig = true
    private var resumeHandle: String? = null
    private var reconnects = 0
    private var pendingGreeting: String? = null
    private val pendingTexts = mutableListOf<String>()
    @Volatile private var videoSupported = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerOn = false

    private var recorder: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var hardwareEchoActive = false

    /**
     * Call speaker or headset: the phone's echo canceller removes her voice, so the mic streams the whole time like a
     * WhatsApp call and Gemini hears the user even mid-sentence. Switched off for the session if her own echo keeps
     * interrupting her.
     */
    @Volatile private var fullDuplex = false
    private var selfInterrupts = 0
    @Volatile private var track: AudioTrack? = null
    private var loudness: android.media.audiofx.LoudnessEnhancer? = null
    private val playback = Channel<ByteArray>(Channel.UNLIMITED)
    private var lastState: State? = null

    val isSpeaking: Boolean get() = SystemClock.elapsedRealtime() < playEndAt

    /** Speaker output lags the queue, and the room echoes a little after Faiza stops. */
    private val echoActive: Boolean get() = SystemClock.elapsedRealtime() < playEndAt + ECHO_TAIL_MS

    /** [greeting] is sent as text once the session is ready so the model speaks first. */
    fun start(greeting: String?) {
        pendingGreeting = greeting
        emit(State.CONNECTING)
        if (appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            listener.onFatalError("Microphone permission is off. Allow it in Faiza AI → Settings → Permissions.")
            stop()
            return
        }
        startCallAudioRouting()
        startPlayback()
        // Not in games (game sound would trip it; gaming has its own echo guard).
        if (bargeIn && !gamingFast) scope.launch(Dispatchers.IO) { spotter = StopWordSpotter.create(appContext) }
        startMicLoop()
        connect()
        scope.launch {
            while (isActive) {
                if (setupDone) {
                    emit(
                        when {
                            micBlocked -> State.MIC_BUSY
                            toolRunning -> State.WORKING
                            isSpeaking -> State.SPEAKING
                            else -> State.LISTENING
                        }
                    )
                }
                delay(150)
            }
        }
    }

    /**
     * Stops whatever Faiza is saying and makes her respond to [instruction] right away
     * (e.g. a Voice Guardian warning). Uses clientContent, which interrupts the current reply.
     */
    fun interruptWith(instruction: String) {
        if (!setupDone) return
        bargeSuppressUntil = 0L
        flushPlayback()
        socket?.send(JsonObject().apply {
            add("clientContent", JsonObject().apply {
                add("turns", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", instruction) })
                        })
                    })
                })
                addProperty("turnComplete", true)
            })
        }.toString())
    }

    /**
     * The user cut in (stop word heard on the phone, orb tapped, volume-down pressed): silence her NOW, stop the
     * answer on the server too, and listen — same conversation, nothing forgotten.
     */
    fun userInterrupt() {
        if (!setupDone || stopped) return
        barge.resetTurn()
        interruptWith(
            "[THE USER INTERRUPTED YOU] Stop the previous answer right now. Say only 1–3 words like \"হ্যাঁ, বলুন\" / " +
                    "\"জি?\" in their language, then listen. When they finish, answer what they said. If they say " +
                    "\"continue / আগেরটা বলো / তারপর\", continue the earlier answer from where you stopped."
        )
    }

    /** Hears "stop / wait / Faiza…" on the phone while she talks (null if the offline model isn't there). */
    @Volatile private var spotter: StopWordSpotter? = null
    private var spotterUsed = false

    /** Ask the model to say something (e.g. a notification or a typed question) in its own voice. */
    fun sendText(text: String) {
        if (!setupDone) {
            // Still connecting: deliver it as soon as the session is ready.
            synchronized(pendingTexts) { if (pendingTexts.size < 5) pendingTexts += text }
            return
        }
        socket?.send(JsonObject().apply {
            add("realtimeInput", JsonObject().apply { addProperty("text", text) })
        }.toString())
    }

    /** Scan mode (video 30:51): one camera frame (JPEG) so Faiza can see what the camera sees. */
    fun sendImage(jpeg: ByteArray) {
        if (!setupDone || stopped || !videoSupported) return
        val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        socket?.send("{\"realtimeInput\":{\"video\":{\"mimeType\":\"image/jpeg\",\"data\":\"$b64\"}}}")
    }

    val isReady: Boolean get() = setupDone && !stopped

    /** True while a voice sample plays: the mic keeps running but nothing is sent. */
    @Volatile var micMuted = false

    /** False once the model has refused camera frames. */
    val acceptsImages: Boolean get() = videoSupported

    fun stop() {
        if (stopped) return
        stopped = true
        socket?.close(1000, "bye")
        socket = null
        releaseRecorder()
        playback.close()
        loudness?.runCatching { release() }
        loudness = null
        track?.runCatching { pause(); flush(); release() }
        track = null
        stopCallAudioRouting()
        emit(State.CLOSED)
        scope.cancel()
    }

    // ========== Connection ==========

    @Synchronized
    private fun connect() {
        if (stopped) return
        setupDone = false
        val gen = ++generation
        val request = Request.Builder().url("$URL?key=$apiKey").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation || stopped) {
                    webSocket.close(1000, null)
                    return
                }
                webSocket.send(setupMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen == generation) handle(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (gen == generation) handle(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (gen == generation) onDisconnected(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen == generation) onDisconnected(null, t.message ?: "connection failed")
            }
        })
    }

    private fun onDisconnected(code: Int?, reason: String) {
        if (stopped) return
        setupDone = false
        val lower = reason.lowercase()
        // Only problems that retrying can't fix end the conversation. Everything else (network drops,
        // server restarts, idle timeouts) reconnects and resumes the same session.
        if (useFastCompression && (code == 1007 || code == 1008 || lower.contains("invalid")) &&
            listOf("compression", "trigger", "target").any { lower.contains(it) }
        ) {
            useFastCompression = false
            scope.launch { delay(300); if (!stopped) connect() }
            return
        }
        if (useVadConfig && (code == 1007 || code == 1008 || lower.contains("invalid")) &&
            listOf("realtime_input", "realtimeinput", "activity_detection", "activitydetection",
                "activity_handling", "activityhandling", "sensitivity").any { lower.contains(it) }
        ) {
            // This model doesn't accept the voice-detection settings: reconnect without them.
            useVadConfig = false
            scope.launch { delay(300); if (!stopped) connect() }
            return
        }
        if (videoSupported && (code == 1007 || code == 1008 || lower.contains("invalid")) &&
            (lower.contains("video") || lower.contains("image"))
        ) {
            // This model can't take camera frames: keep talking, just without Scan pictures.
            videoSupported = false
            scope.launch { delay(300); if (!stopped) connect() }
            return
        }
        val fatal = code == 1007 || code == 1008 ||
                listOf("api key", "not found", "is not supported", "permission denied", "quota", "exhausted")
                    .any { lower.contains(it) }
        if (fatal || reconnects >= MAX_RECONNECTS) {
            val message = reason.ifBlank { "Connection closed ($code)" }
            main.post { listener.onFatalError(message) }
            stop()
            return
        }
        reconnects++
        emit(State.CONNECTING)
        scope.launch {
            delay(minOf(MAX_BACKOFF_MS, 1000L * reconnects))
            if (!stopped) connect()
        }
    }

    private fun setupMessage(): JsonObject = JsonObject().apply {
        add("setup", JsonObject().apply {
            addProperty("model", "models/$model")
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply { add("AUDIO") })
                add("speechConfig", JsonObject().apply {
                    add("voiceConfig", JsonObject().apply {
                        add("prebuiltVoiceConfig", JsonObject().apply { addProperty("voiceName", voice) })
                    })
                })
            })
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemPrompt) })
                })
            })
            if (tools.isNotEmpty()) add("tools", geminiToolsJson(tools))
            if (useVadConfig) add("realtimeInputConfig", JsonObject().apply {
                add("automaticActivityDetection", JsonObject().apply {
                    // Hear even a soft "wait" / "শোনো" and let it interrupt her.
                    addProperty("startOfSpeechSensitivity", if (gamingFast || loudDuplex) "START_SENSITIVITY_LOW" else "START_SENSITIVITY_HIGH")
                    addProperty("prefixPaddingMs", 100)
                    addProperty("silenceDurationMs", 600)
                })
                addProperty("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
            })
            add("inputAudioTranscription", JsonObject())
            add("outputAudioTranscription", JsonObject())
            add("sessionResumption", JsonObject().apply { resumeHandle?.let { addProperty("handle", it) } })
            add("contextWindowCompression", JsonObject().apply {
                if (useFastCompression) {
                    // ~25 pictures of game + chat, then the oldest are dropped: answers stay fast all match long.
                    addProperty("triggerTokens", "12000")
                    add("slidingWindow", JsonObject().apply { addProperty("targetTokens", "5000") })
                } else add("slidingWindow", JsonObject())
            })
        })
    }

    private fun handle(text: String) {
        val msg = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return

        when {
            msg.has("setupComplete") -> {
                setupDone = true
                reconnects = 0
                pendingGreeting?.let { sendText(it) }
                pendingGreeting = null
                val queued = synchronized(pendingTexts) { pendingTexts.toList().also { pendingTexts.clear() } }
                queued.forEach { sendText(it) }
            }
            msg.has("serverContent") -> handleServerContent(msg.getAsJsonObject("serverContent"))
            msg.has("toolCall") -> handleToolCall(msg.getAsJsonObject("toolCall"))
            msg.has("sessionResumptionUpdate") -> {
                val update = msg.getAsJsonObject("sessionResumptionUpdate")
                if (update.get("resumable")?.asBoolean == true) {
                    update.get("newHandle")?.asString?.takeIf { it.isNotEmpty() }?.let { resumeHandle = it }
                }
            }
            msg.has("goAway") -> {
                // Google is about to recycle this connection: open a new one that resumes the session.
                val old = socket
                connect()
                old?.close(1000, "reconnecting")
            }
        }
    }

    /** What the mic transcribed since her last answer: if it's only her own words, she must not answer it. */
    private val heardSinceTurn = StringBuilder()
    /** This answer is a reply to her own echo: don't play it. */
    @Volatile private var muteThisTurn = false
    /** Audio of the current answer has started arriving. */
    @Volatile private var turnStarted = false
    @Volatile private var echoResumes = 0

    private fun handleServerContent(content: JsonObject) {
        content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { el ->
            val data = el.asJsonObject.getAsJsonObject("inlineData")?.get("data")?.asString ?: return@forEach
            if (!turnStarted) {
                turnStarted = true
                // A new answer is starting: was the "user" she's answering just her own voice from the speaker?
                val heard = synchronized(heardSinceTurn) { EchoText.words(heardSinceTurn.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                // 4+ words, so a short real answer like "ঠিক আছে" (which she may have said too) is never muted.
                muteThisTurn = !muteMicWhileSpeaking && EchoText.isEchoTurn(heard, spoken, minWords = 4)
            }
            if (muteThisTurn) return@forEach
            enqueueAudio(Base64.decode(data, Base64.DEFAULT))
        }
        if (content.get("turnComplete")?.asBoolean == true || content.get("generationComplete")?.asBoolean == true) {
            if (content.get("turnComplete")?.asBoolean == true) {
                turnStarted = false
                muteThisTurn = false
                synchronized(heardSinceTurn) { heardSinceTurn.setLength(0) }
            }
        }
        content.getAsJsonObject("outputTranscription")?.get("text")?.asString
            ?.takeIf { it.isNotBlank() }?.let { text ->
                rememberOutputWords(text)
                main.post { listener.onModelSpeech(text) }
            }
        content.getAsJsonObject("inputTranscription")?.get("text")?.asString
            ?.takeIf { it.isNotBlank() }?.let { text ->
                synchronized(heardSinceTurn) { heardSinceTurn.append(' ').append(text) }
                lastInputAt = SystemClock.elapsedRealtime()
                checkOverTalk(text)
                main.post { listener.onUserSpeech(text) }
            }
        if (content.get("interrupted")?.asBoolean == true) {
            if (fullDuplex && !bargeIn) {
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                if (heard.size >= 2 && EchoText.echoShare(heard, spoken) >= 0.8 && ++selfInterrupts >= 2) {
                    // Her echo keeps getting through on this phone: go back to filtering it out.
                    fullDuplex = false
                }
            }
            bargeSuppressUntil = 0L
            flushPlayback()
            barge.resetTurn()
            turnStarted = false
            muteThisTurn = false
            // Stopped by her OWN voice from the speaker (not the user)? Wait for the transcript, then let her go on.
            if (!muteMicWhileSpeaking && echoResumes < 3 && !recentlyStoppedByUser()) scope.launch {
                val interruptedAt = SystemClock.elapsedRealtime()
                delay(if (loudDuplex) 1600 else 900)
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                val userSaidSomething = lastInputAt > interruptedAt - 1_500 && !EchoText.isEchoTurn(heard, spoken)
                val echo = EchoText.isEchoTurn(heard, spoken) || (loudDuplex && !userSaidSomething && heard.isEmpty())
                // A stop word (even one transcribed late) means the user really stopped her: don't carry on.
                val told = recentlyStoppedByUser() || EchoText.isStopCommand(heard, spoken)
                if (echo && !told && !isSpeaking && !stopped) resumeAfterEcho()
                synchronized(overTalkText) { overTalkText.setLength(0) }
            } else synchronized(overTalkText) { overTalkText.setLength(0) }
        }
        if (content.get("turnComplete")?.asBoolean == true) echoResumes = 0
    }

    private fun rememberOutputWords(text: String) {
        val now = SystemClock.elapsedRealtime()
        synchronized(recentOutputWords) {
            EchoText.words(text).forEach { recentOutputWords.addLast(SpokenWord(now, it)) }
            while (recentOutputWords.isNotEmpty() && recentOutputWords.first().at < now - OUTPUT_WORDS_MS) {
                recentOutputWords.removeFirst()
            }
        }
    }

    /**
     * Gemini transcribed the mic while Faiza was talking. Her own echo comes back as her own words;
     * anything else is the user — stop her right away, even if they spoke softly. (Gemini often has
     * already sent the whole answer, so without this the phone kept playing it to the end.)
     */
    private fun checkOverTalk(text: String) {
        if (!echoActive || muteMicWhileSpeaking) {
            synchronized(overTalkText) { overTalkText.setLength(0) }
            return
        }
        val heard = synchronized(overTalkText) {
            overTalkText.append(' ').append(text)
            EchoText.words(overTalkText.toString())
        }
        val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
        if (bargeIn && EchoText.isStopCommand(heard, spoken)) {
            // "থামো / stop / wait": stop for sure — on the phone AND on the server — and never auto-continue.
            synchronized(overTalkText) { overTalkText.setLength(0) }
            stopByUser()
        } else if (EchoText.isUserSpeech(heard, spoken)) {
            synchronized(overTalkText) { overTalkText.setLength(0) }
            silenceForBargeIn()
            // If no answer to the user follows, it was only her mis-heard echo: don't leave her stuck in silence.
            if (!muteMicWhileSpeaking) scope.launch {
                delay(3_000)
                if (!isSpeaking && !toolRunning && !recentlyStoppedByUser() && !stopped) resumeAfterEcho()
            }
        } else if (heard.size >= 3 && EchoText.echoShare(heard, spoken) >= 0.8) {
            // Only her echo got through: be a little stricter from now on.
            synchronized(overTalkText) { overTalkText.setLength(0) }
            barge.onEchoConfirmed()
        }
    }

    private fun handleToolCall(toolCall: JsonObject) {
        val calls = toolCall.getAsJsonArray("functionCalls") ?: return
        scope.launch {
            toolRunning = true
            val responses = JsonArray()
            for (el in calls) {
                val fc = el.asJsonObject
                val call = ToolCall(
                    id = fc.get("id")?.asString.orEmpty(),
                    name = fc.get("name").asString,
                    args = fc.getAsJsonObject("args") ?: JsonObject()
                )
                val result = withContext(Dispatchers.Main) { listener.onToolCall(call) }
                responses.add(JsonObject().apply {
                    if (call.id.isNotEmpty()) addProperty("id", call.id)
                    addProperty("name", call.name)
                    add("response", JsonObject().apply { addProperty("result", result) })
                })
            }
            toolRunning = false
            socket?.send(JsonObject().apply {
                add("toolResponse", JsonObject().apply { add("functionResponses", responses) })
            }.toString())
        }
    }

    // ========== Audio ==========

    @SuppressLint("MissingPermission")
    private fun createRecorder(): AudioRecord? {
        releaseRecorder()
        val minBuf = AudioRecord.getMinBufferSize(IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, CHUNK_BYTES * 4)
            )
        } catch (_: Exception) {
            return null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        // Hardware echo cancellation removes Faiza's own voice from the mic, so you can interrupt
        // her by speaking normally. When it's active we trust it and Gemini's own barge-in detection.
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
            hardwareEchoActive = echoCanceler?.enabled == true
        }
        if (!hardwareEchoActive && bargeAssumesAec) {
            bargeAssumesAec = false
            barge = BargeInDetector(hardwareEchoCanceller = false, callMode = routedCallSpeaker)
            // No echo-canceller effect reported. In call mode the phone still cancels echo itself, so with
            // "Stop when I talk" on the mic stays open; otherwise fall back to filtering.
            if (routedCallSpeaker && !bargeIn) fullDuplex = false
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
        }
        // Soft voices: the phone's own gain control lifts quiet speech before our boost.
        if (micGain > 1f && android.media.audiofx.AutomaticGainControl.isAvailable()) {
            runCatching { android.media.audiofx.AutomaticGainControl.create(rec.audioSessionId)?.enabled = true }
        }
        return try {
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                rec.release()
                null
            } else {
                recorder = rec
                rec
            }
        } catch (_: Exception) {
            rec.runCatching { release() }
            null
        }
    }

    private fun releaseRecorder() {
        recorder?.runCatching { stop() }
        recorder?.runCatching { release() }
        recorder = null
        echoCanceler?.runCatching { release() }
        echoCanceler = null
        noiseSuppressor?.runCatching { release() }
        noiseSuppressor = null
    }

    private fun setMicBlocked(blocked: Boolean) {
        if (micBlocked == blocked) return
        micBlocked = blocked
        main.post { if (!stopped) listener.onMicBlocked(blocked) }
    }

    /** Android 10+ tells us directly when another app has taken the microphone from us. */
    private fun silencedByAndroid(rec: AudioRecord): Boolean =
        Build.VERSION.SDK_INT >= 29 &&
                runCatching { rec.activeRecordingConfiguration?.isClientSilenced == true }.getOrDefault(false)

    private fun isAllZero(data: ByteArray, length: Int): Boolean {
        for (i in 0 until length) if (data[i].toInt() != 0) return false
        return true
    }

    /**
     * Reads the microphone and streams it. If another app grabs the mic (Android then gives us silence),
     * we say so, keep the conversation connected, and re-open the mic every 2 seconds until it's free.
     */
    private fun startMicLoop() {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(CHUNK_BYTES)
            val held = ArrayDeque<ByteArray>()
            var rec: AudioRecord? = createRecorder()
            var zeroSince = 0L
            var lastCheck = 0L
            var silencedCache = false
            var lastReopen = SystemClock.elapsedRealtime()

            while (isActive && !stopped) {
                val now = SystemClock.elapsedRealtime()
                val r = rec
                if (r == null) {
                    // No microphone right now (busy or error): wait and try again.
                    setMicBlocked(true)
                    delay(micReopenMs)
                    if (!stopped) rec = createRecorder()
                    lastReopen = SystemClock.elapsedRealtime()
                    continue
                }
                val n = r.read(buffer, 0, buffer.size)
                if (n < 0) { // dead object / invalid operation: start over
                    releaseRecorder()
                    rec = null
                    continue
                }
                if (n == 0) {
                    delay(20)
                    continue
                }

                // Is somebody else listening to the microphone instead of us?
                val taken = if (Build.VERSION.SDK_INT >= 29) {
                    if (now - lastCheck >= MIC_CHECK_MS) {
                        lastCheck = now
                        silencedCache = silencedByAndroid(r)
                    }
                    silencedCache
                } else if (isAllZero(buffer, n)) {
                    if (zeroSince == 0L) zeroSince = now
                    now - zeroSince > ZERO_SILENCE_MS
                } else {
                    zeroSince = 0L
                    false
                }
                if (taken) {
                    setMicBlocked(true)
                    held.clear()
                    if (now - lastReopen >= micReopenMs) {
                        lastReopen = now
                        zeroSince = 0L
                        rec = createRecorder()
                    }
                    continue
                }
                setMicBlocked(false)

                if (!setupDone) continue
                if (toolRunning || micMuted) { held.clear(); continue }

                val level = rms(buffer, 0, n)
                // Stop words while she talks: decided here on the phone, whatever the loudspeaker does to the mic.
                if (echoActive && bargeIn && !muteMicWhileSpeaking) {
                    val sp = spotter
                    if (sp != null) {
                        spotterUsed = true
                        val her = synchronized(recentOutputWords) {
                            val since = SystemClock.elapsedRealtime() - 6_000
                            recentOutputWords.filter { it.at >= since }.mapTo(HashSet()) { it.word }
                        }
                        if (sp.feed(buffer, n, her)) {
                            sp.reset()
                            main.post { userInterrupt() }
                            held.clear()
                            continue
                        }
                    }
                } else if (spotterUsed) {
                    spotterUsed = false
                    spotter?.reset()
                }
                if (echoActive) {
                    // Echo Guard / screen recording: user chose never to be heard while Faiza talks.
                    if (muteMicWhileSpeaking) { held.clear(); continue }

                    val t = SystemClock.elapsedRealtime()
                    if (fullDuplex) {
                        // Loudspeaker: her own echo is loud too, so loudness can't tell who's talking — the AI decides.
                        val action = barge.onEchoChunk(level, outputLevel(t), t)
                        if (!loudDuplex && action == BargeInDetector.Action.INTERRUPT) localBargeIn()
                        while (held.isNotEmpty()) { val c = held.removeFirst(); sendAudio(c, c.size) }
                        sendAudio(buffer, n)
                        continue
                    }
                    when (barge.onEchoChunk(level, outputLevel(t), t)) {
                        BargeInDetector.Action.INTERRUPT -> {
                            // Clearly the user: cut her off NOW, then forward the speech.
                            localBargeIn()
                            while (held.isNotEmpty()) { val c = held.removeFirst(); sendAudio(c, c.size) }
                            sendAudio(buffer, n)
                        }
                        BargeInDetector.Action.FORWARD_START -> {
                            // Maybe the user (soft voice): let Gemini hear it; it stops her if it's speech.
                            while (held.isNotEmpty()) { val c = held.removeFirst(); sendAudio(c, c.size) }
                            sendAudio(buffer, n)
                        }
                        BargeInDetector.Action.FORWARD -> sendAudio(buffer, n)
                        BargeInDetector.Action.HOLD -> {
                            held.addLast(buffer.copyOf(n))
                            while (held.size > PREROLL_CHUNKS) held.removeFirst()
                        }
                    }
                    continue
                }

                // Faiza is silent: listen normally.
                barge.onQuietChunk(level)
                held.clear()
                sendAudio(buffer, n)
            }
            spotter?.close()
            spotter = null
        }
    }

    /** When the user last told her to stop (a stop word): no "it was only echo, go on" for a while after this. */
    @Volatile private var userStoppedAt = 0L

    private fun recentlyStoppedByUser(): Boolean =
        userStoppedAt > 0L && SystemClock.elapsedRealtime() - userStoppedAt < STOP_HOLD_MS

    /** The user said a stop word: cut her off now, cancel the answer on the server, and listen. */
    private fun stopByUser() {
        userStoppedAt = SystemClock.elapsedRealtime()
        silenceForBargeIn()
        main.post { userInterrupt() }
    }

    @Volatile private var lastResumeAt = 0L

    /** She was stopped by a sound that turned out not to be the user: go on from where she stopped. */
    private fun resumeAfterEcho() {
        val now = SystemClock.elapsedRealtime()
        if (echoResumes >= 3 || now - lastResumeAt < 3_000) return
        lastResumeAt = now
        echoResumes++
        synchronized(heardSinceTurn) { heardSinceTurn.setLength(0) }
        sendText("[ECHO] That sound was only your own voice from the loudspeaker, not the user. Continue your " +
                "answer exactly from where you stopped — don't repeat what you already said, don't mention this.")
    }

    /**
     * The phone itself heard a voice clearly above her echo: stop her at once. If no words from the user follow
     * (it was only a noise or her own echo), she carries on — she never gets stuck in silence.
     */
    private fun localBargeIn() {
        if (!echoActive) return
        silenceForBargeIn()
        if (muteMicWhileSpeaking) return
        val at = SystemClock.elapsedRealtime()
        scope.launch {
            delay(2_200)
            val userSpoke = lastInputAt >= at - 300
            if (!userSpoke && !recentlyStoppedByUser() && !isSpeaking && !stopped) resumeAfterEcho()
        }
    }

    /** User interrupted: silence Faiza at once and drop the rest of the turn she was speaking. */
    private fun silenceForBargeIn() {
        if (!echoActive) return
        bargeSuppressUntil = SystemClock.elapsedRealtime() + BARGE_SUPPRESS_MS
        flushPlayback()
        barge.resetTurn()
    }

    private fun sendAudio(data: ByteArray, length: Int) {
        audioTap?.let { tap -> runCatching { tap(data, minOf(length, data.size)) } }
        val out = if (micGain > 1f) MicBoost.apply(data, minOf(length, data.size), micGain) else data
        val b64 = Base64.encodeToString(out, 0, minOf(length, out.size), Base64.NO_WRAP)
        socket?.send(
            "{\"realtimeInput\":{\"audio\":{\"mimeType\":\"audio/pcm;rate=$IN_RATE\",\"data\":\"$b64\"}}}"
        )
    }

    private fun rms(data: ByteArray, offset: Int, length: Int): Double {
        var sum = 0.0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
            sum += sample.toDouble() * sample
            i += 2
        }
        val count = length / 2
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    /**
     * "Call mode" (like a WhatsApp call on speaker): the phone's echo canceller then removes
     * Faiza's own voice from the microphone. Uses the loudspeaker unless a headset is connected.
     */
    @Suppress("DEPRECATION")
    /** True while Faiza's voice and mic are routed to a Bluetooth headset. */
    private var routedBluetooth = false

    /**
     * Bluetooth headset connected (MAYA "Bluetooth" permission): talk through it, like a phone call.
     * Without one, audio stays exactly as it was (speaker + phone mic) - barge-in is tuned for that.
     */
    /** True while she talks through the call loudspeaker ([callSpeaker]). */
    private var routedCallSpeaker = false

    /**
     * Her voice on the CALL volume (separate from videos) but without switching the phone into call mode. Used when
     * "Stop when I talk" is on: on Honor/Huawei (and others) call mode squashes the user's voice to near-silence while
     * she speaks, so she could never be interrupted. Echo is then filtered by BargeInDetector, as before.
     */
    private var callStreamOnly = false

    /** The call volume is often low (it's only used for calls): lift it so she's clearly heard. */
    private fun liftCallVolume() = runCatching {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val want = (max * 0.8f).toInt().coerceAtLeast(1)
        if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) < want) {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, want, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun startCallAudioRouting() {
        val headset = bluetoothHeadset()
        if (headset == null) {
            // Owner: "ফোন কলে লাউডস্পিকারের কথা অপর পাশের লোক শোনে না, কিন্তু আমার আস্তে কথাও শোনে". That is the
            // phone's CALL echo canceller: it only works when her voice plays on the call path. So with "stop when I
            // talk" on, the loudspeaker now uses real call mode (like a WhatsApp call on speaker) instead of media.
            val callLoud = loudspeaker && bargeIn && !muteMicWhileSpeaking && !forceSpeaker && !headsetConnected()
            if (callLoud && enterCallMode()) return
            if (loudspeaker && !forceSpeaker && !headsetConnected()) {
                loudDuplex = bargeIn && !muteMicWhileSpeaking
                fullDuplex = loudDuplex
                return
            }
            if (!loudspeaker && callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {
                liftCallVolume()
                callStreamOnly = true
                return
            }
            // Loudspeaker (owner: "WhatsApp/imo ভিডিও কলের মতো") or call speaker: call mode on the LOUD speaker. Screen
            // recording only captures the media stream, and earphones take the sound by themselves.
            if (callSpeaker && !forceSpeaker && !headsetConnected()) runCatching {
                previousAudioMode = audioManager.mode
                previousSpeakerOn = audioManager.isSpeakerphoneOn
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val speaker = if (Build.VERSION.SDK_INT >= 31)
                    audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                else null
                if (speaker != null) audioManager.setCommunicationDevice(speaker) else audioManager.isSpeakerphoneOn = true
                // The call volume is often low (it's only used for calls): lift it so she's clearly heard.
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val want = (max * 0.8f).toInt().coerceAtLeast(1)
                if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) < want) {
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, want, 0)
                }
                routedCallSpeaker = true
                fullDuplex = !muteMicWhileSpeaking
                barge = BargeInDetector(hardwareEchoCanceller = true, callMode = true)
            }
            return
        }
        runCatching {
            previousAudioMode = audioManager.mode
            previousSpeakerOn = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                audioManager.setCommunicationDevice(headset)
            } else {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
            routedBluetooth = true
            fullDuplex = !muteMicWhileSpeaking
        }
    }

    /**
     * Call mode on the LOUD speaker: her voice on the voice-call stream, the mic on VOICE_COMMUNICATION with the
     * phone's echo canceller — so her own voice is removed from the mic and even a soft word from the user gets through.
     */
    @Suppress("DEPRECATION")
    private fun enterCallMode(): Boolean = runCatching {
        previousAudioMode = audioManager.mode
        previousSpeakerOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val speaker = if (Build.VERSION.SDK_INT >= 31)
            audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        else null
        if (speaker != null) audioManager.setCommunicationDevice(speaker) else audioManager.isSpeakerphoneOn = true
        // Loud like a video call on speaker: the call volume is usually set low.
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val want = (max * 0.9f).toInt().coerceAtLeast(1)
        if (audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) < want) {
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, want, 0)
        }
        routedCallSpeaker = true
        loudDuplex = false
        fullDuplex = true
        barge = BargeInDetector(hardwareEchoCanceller = true, callMode = true)
        true
    }.getOrElse {
        runCatching { audioManager.mode = previousAudioMode }
        false
    }

    @Suppress("DEPRECATION")
    private fun stopCallAudioRouting() {
        if (routedCallSpeaker) {
            routedCallSpeaker = false
            runCatching {
                if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
                audioManager.isSpeakerphoneOn = previousSpeakerOn
                audioManager.mode = previousAudioMode
            }
            return
        }
        if (!routedBluetooth) return
        routedBluetooth = false
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = previousAudioMode
        }
    }

    private fun bluetoothHeadset(): AudioDeviceInfo? = runCatching {
        if (forceSpeaker) return@runCatching null
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        }
    }.getOrNull()

    private fun headsetConnected(): Boolean {
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
    }

    private fun startPlayback() {
        // On the phone's own speaker she plays on the MEDIA stream: loud like a WhatsApp call on speaker, and the
        // volume buttons work. (The call stream, without a real call, is quiet and stuck at the call volume.)
        // A Bluetooth headset keeps the call stream, which is what carries audio to it.
        val usage = if (routedBluetooth || routedCallSpeaker || callStreamOnly) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA
        trackUsage = usage
        track = buildTrack(usage)
        scope.launch(Dispatchers.IO) {
            for (chunk in playback) {
                if (stopped) break
                // Read [track] each time: gaming may swap it when the game's voice chat starts/stops.
                track?.runCatching { write(chunk, 0, chunk.size) }
            }
        }
    }

    private fun buildTrack(usage: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUT_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, OUT_RATE)) // ~0.5 s
            .build()
        // No headset: tell Android to use the loudspeaker for her voice, whatever the stream.
        if (loudspeaker && !routedBluetooth && !headsetConnected()) runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { t.setPreferredDevice(it) }
        }
        t.play()
        loudness?.runCatching { release() }
        if (louderVoice && !routedBluetooth) {
            loudness = runCatching {
                android.media.audiofx.LoudnessEnhancer(t.audioSessionId).apply {
                    setTargetGain(600) // +6 dB
                    enabled = true
                }
            }.getOrNull()
        }
        return t
    }

    private fun enqueueAudio(pcm: ByteArray) {
        // Right after a barge-in, Gemini may still send the tail of the old turn — ignore it.
        if (SystemClock.elapsedRealtime() < bargeSuppressUntil) return
        val durationMs = pcm.size * 1000L / (OUT_RATE * 2)
        val now = SystemClock.elapsedRealtime()
        val start = maxOf(playEndAt, now)
        synchronized(outSegments) {
            var offset = 0
            var t = start
            while (offset < pcm.size) {
                val len = minOf(OUT_SEG_BYTES, pcm.size - offset)
                val dur = len * 1000L / (OUT_RATE * 2)
                outSegments.addLast(OutSegment(t, t + dur, rms(pcm, offset, len)))
                t += dur
                offset += len
            }
            while (outSegments.isNotEmpty() && outSegments.first().end < now - 2_000) outSegments.removeFirst()
        }
        playEndAt = start + durationMs
        playback.trySend(pcm)
    }

    /** How loud her voice is at this moment, 0..1 (drives the edge glow while she speaks). */
    fun voiceLevel(): Float {
        val now = SystemClock.elapsedRealtime()
        val rms = synchronized(outSegments) { outSegments.firstOrNull { now in it.start..it.end }?.rms ?: 0.0 }
        return (rms / 5_000.0).toFloat().coerceIn(0f, 1f)
    }

    /** Loudest thing Faiza played in the window that could be echoing into the mic right now. */
    private fun outputLevel(now: Long): Double = synchronized(outSegments) {
        val from = now - ECHO_LOOKBACK_MS
        var max = 0.0
        for (seg in outSegments) if (seg.end >= from && seg.start <= now && seg.rms > max) max = seg.rms
        max
    }

    private fun flushPlayback() {
        synchronized(outSegments) { outSegments.clear() }
        while (playback.tryReceive().isSuccess) { /* drop queued audio */ }
        track?.runCatching { pause(); flush(); play() }
        playEndAt = 0L
    }

    private fun emit(state: State) {
        if (state == lastState) return
        lastState = state
        main.post { listener.onState(state) }
    }
}
