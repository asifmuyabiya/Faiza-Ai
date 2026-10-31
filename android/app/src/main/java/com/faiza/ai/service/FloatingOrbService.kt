package com.faiza.ai.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.FaizaAgent
import com.faiza.ai.ai.Persona
import com.faiza.ai.ui.orb.EdgeGlowView
import com.faiza.ai.ui.orb.OrbDesign
import com.faiza.ai.ui.orb.OrbMode
import com.faiza.ai.ui.orb.OrbTheme
import com.faiza.ai.ui.orb.OrbView
import com.faiza.ai.ai.guardian.OwnerGuard
import com.faiza.ai.ai.guardian.OwnerLock
import com.faiza.ai.ai.guardian.SpeakerVerifier
import com.faiza.ai.ai.guardian.VoicePrint
import com.faiza.ai.ai.guardian.VoiceProfile
import com.faiza.ai.ai.live.GeminiLiveSession
import com.faiza.ai.ai.wake.SpeakerModelFiles
import android.os.SystemClock
import com.faiza.ai.ai.memory.MemoryStore
import com.faiza.ai.ai.macros.MacroCenter
import com.faiza.ai.ai.macros.MacroRecorder
import com.faiza.ai.ai.wake.DownloadableModel
import com.faiza.ai.ai.wake.WakeModel
import com.faiza.ai.ai.wake.WakeWordEngine
import com.faiza.ai.ai.provider.AIProviderException
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ToolCall
import com.faiza.ai.ai.provider.ToolSpec
import java.io.IOException
import com.faiza.ai.ai.Phrases
import com.faiza.ai.ui.DashboardActivity
import com.faiza.ai.util.PrefsManager
import com.faiza.ai.util.QuietMode
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * The heart of Faiza AI: a draggable floating orb that listens for "Hey Faiza",
 * sends commands to Gemini, speaks the reply in the chosen persona's voice,
 * and announces calls/messages when Proactive Mode is on.
 */
class FloatingOrbService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isRunning = false
            private set

        /** The running service (for screens that need the microphone briefly, e.g. voice enrolment). */
        @Volatile
        var instance: FloatingOrbService? = null
            private set

        const val ACTION_TOGGLE_QUIET = "com.faiza.ai.TOGGLE_QUIET"

        /** The assistant button started the service: listen right away (video 7:56). */
        @Volatile
        var pendingAssist = false

        private const val CINEMATIC_GREETING =
            "The user just turned you on. Greet them like a movie AI booting up (JARVIS style), one or two short " +
                    "sentences, e.g. \"All systems online. Good to see you, sir.\" — in your persona and language."

        private const val ASSIST_GREETING =
            "The user pressed the assistant button to talk to you. Say a very short \"Yes?\" (in your persona) and listen."

        /** Text typed on the dashboard before Faiza was running; answered (by voice) as soon as she starts. */
        @Volatile
        var pendingTypedText: String? = null

        private const val PROACTIVE_AFTER_IDLE_MS = 60_000L
        private const val PROACTIVE_GAP_MS = 75_000L
        private const val PROACTIVE_MAX_NUDGES = 2
        private const val STRIKE_WINDOW_MS = 3 * 60_000L

        private const val PROACTIVE_NUDGE =
            "[PROACTIVE MODE — the user has been quiet for a while. Start a short, natural conversation yourself " +
                    "(1–2 sentences): ask about their day, or bring up something you remember about them, a plan or " +
                    "person they mentioned, or their YouTube channel. Don't repeat a topic you already raised.]"

        private val GO_TO_SLEEP = ToolSpec(
            "go_to_sleep",
            "End the conversation and stop listening, when the user says goodbye or asks you to sleep."
        )
    }

    private lateinit var windowManager: WindowManager
    private lateinit var orbView: View
    private lateinit var orbArt: OrbView
    private lateinit var avatarArt: com.faiza.ai.ui.orb.AvatarView
    private var edgeGlow: EdgeGlowView? = null
    /** "● REC  notes · 3 steps" while a macro is being recorded (video 36:10). */
    private var recPill: TextView? = null
    private var lastOrbState = "idle"
    private var topPackage = ""
    private var homePackageName: String? = null
    private lateinit var params: WindowManager.LayoutParams

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var agent: FaizaAgent? = null
    private var announcer: ProactiveAnnouncer? = null

    private val prefs by lazy { FaizaApp.instance.prefs }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + com.faiza.ai.util.CrashReporter.guard)
    private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()
    private var wakeRestartJob: Job? = null

    // Offline "Hey Maya" engine; Android's recognizer is only a fallback.
    private var wakeEngine: WakeWordEngine? = null
    private var wakeEngineFailed = false

    private var isActiveListening = false
    private var isProcessing = false

    // Live (voice-to-voice) mode
    private var live: GeminiLiveSession? = null
    private var liveWakeLock: PowerManager.WakeLock? = null
    private var micBusyToastShown = false

    // Voice Guardian & proactive chat
    private var verifier: SpeakerVerifier? = null
    private var strangerStrikes = 0

    // Owner lock: this conversation is with someone who hasn't said the secret code yet.
    private var guardMode = false
    private val guardHeard = StringBuilder()
    private var guardJudge: kotlinx.coroutines.Job? = null
    private var lastStrikeAt = 0L
    private var blockToolsUntil = 0L
    private var lastUserSpeechStart = 0L
    private var proactiveNudges = 0
    private var lastNudgeAt = 0L

    /** True while another screen (voice enrolment) is using the microphone. */
    private var micPaused = false
    private var liveTicker: Job? = null
    /** Keeps a confirmed task plan moving when the AI stops after a step. */
    private var planWatch: Job? = null
    private var lastUserActivityAt = 0L

    private val persona get() = Persona.from(prefs.selectedPersona)
    private val language get() = AppLanguage.from(prefs.language)

    // Dragging
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsManager.KEY_SELECTED_PERSONA, PrefsManager.KEY_LANGUAGE -> {
                applyVoiceSettings()
                if (live != null) restartLive()
                else if (!isActiveListening && !isProcessing) updateOrbState("idle")
            }
            // Who she is / how she talks changed: start the live chat again with the new instructions.
            PrefsManager.KEY_ASSISTANT_NAME, PrefsManager.KEY_GIRLFRIEND_MODE, PrefsManager.KEY_MEMORY_ENABLED,
            PrefsManager.KEY_INCOGNITO, PrefsManager.KEY_USER_NAME,
            PrefsManager.KEY_VOICE_GUARDIAN_ENABLED, PrefsManager.KEY_VG_MODE, PrefsManager.KEY_VG_THRESHOLD,
            PrefsManager.KEY_VG_PROFILES -> if (live != null) restartLive()
            PrefsManager.KEY_FLOATING_ORB_DESIGN, PrefsManager.KEY_ORB_THEME, PrefsManager.KEY_ORB_SIZE -> applyOrbAppearance()
            PrefsManager.KEY_ORB_ENABLED, PrefsManager.KEY_ORB_HIDE_IN_APPS -> updateOrbVisibility()
            PrefsManager.KEY_ORB_EDGE_GLOW -> updateOrbState(lastOrbState)
            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice", "loudspeaker" -> if (live != null) restartLive()
            PrefsManager.KEY_QUIET_MODE -> applyQuietMode()
            PrefsManager.KEY_SPEAKING_STYLE, PrefsManager.KEY_NARRATE -> {
                applyVoiceSettings()
                if (live != null) restartLive()
            }
            PrefsManager.KEY_LIVE_ENABLED, PrefsManager.KEY_LIVE_MODEL -> {
                if (live != null) {
                    stopLive()
                    if (liveOk()) startLive(null) else resumeIdle()
                }
            }
            PrefsManager.KEY_GEMINI_API_KEY, PrefsManager.KEY_AI_PROVIDER, PrefsManager.KEY_CUSTOM_API_URL ->
                rebuildAgent()
            PrefsManager.KEY_PROACTIVE_MODE, PrefsManager.KEY_CALL_ANNOUNCEMENT -> announcer?.refresh()
            PrefsManager.KEY_WAKE_WORD_ENABLED -> {
                if (prefs.isWakeWordEnabled) {
                    WakeModel.ensureDownloaded(this)
                    startListeningForWakeWord()
                } else {
                    wakeEngine?.stop()
                    if (!isActiveListening && !isProcessing) {
                        wakeRestartJob?.cancel()
                        speechRecognizer?.cancel()
                    }
                }
            }
            else -> when {
                key == null -> Unit
                key.startsWith(PrefsManager.KEY_API_KEY_PREFIX) || key.startsWith(PrefsManager.KEY_MODEL_PREFIX) ->
                    rebuildAgent()
                key.startsWith(PrefsManager.KEY_LIVE_VOICE_PREFIX) -> if (live != null) restartLive()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        startAsForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        rebuildAgent()
        createFloatingOrb()
        scope.launch { MacroCenter.recorder.state.collect { showRecordingPill(it) } }
        scope.launch { com.faiza.ai.ai.macros.MacroRunner.progress.collect { showMacroPill(it) } }
        topPackage = FaizaAccessibilityService.instance?.topPackage.orEmpty()
        FaizaAccessibilityService.foregroundListener = { pkg ->
            topPackage = pkg
            updateOrbVisibility()
            runCatching { onForegroundForGaming(pkg) }
        }
        updateOrbVisibility()

        announcer = ProactiveAnnouncer(
            context = this,
            isBusy = { isActiveListening || isProcessing },
            speak = { announce(it) },
            onCall = { onIncomingCall(it) },
            onCallEnded = { offhook -> onPhoneCallEnded(offhook) },
            onDrivingMessage = { line, app, who -> onDrivingMessage(line, app, who) }
        ).also { it.refresh() }

        prefs.registerListener(prefsListener)
        startLifeModes()
        EventTriggers.start(this)
        runCatching { com.faiza.ai.ai.prayer.PrayerCenter.schedule(this) }
        com.faiza.ai.ai.guardian.TouchGuard.start(this)

        wakeEngine = WakeWordEngine(
            context = this,
            onWake = { onWakeWordDetected() },
            onUnavailable = {
                wakeEngineFailed = true
                startListeningForWakeWord()
            }
        )
        if (prefs.isWakeWordEnabled) WakeModel.ensureDownloaded(this)
        scope.launch {
            WakeModel.state.collect { state ->
                if (state is DownloadableModel.State.Ready) {
                    wakeEngineFailed = false
                    // Switch from the fallback to the offline engine as soon as the model arrives.
                    if (live == null && !isActiveListening && !isProcessing && prefs.isWakeWordEnabled) {
                        startListeningForWakeWord()
                    }
                }
            }
        }

        val typed = pendingTypedText.also { pendingTypedText = null }
        val assist = pendingAssist.also { pendingAssist = false }
        if (QuietMode.manualActive() && typed == null && !assist) {
            updateOrbState("idle") // Quiet mode: no greeting, no listening until she's tapped.
        } else if (liveOk()) {
            startLive(typed ?: if (assist) ASSIST_GREETING
            else if (prefs.isCinematicStartup) CINEMATIC_GREETING
            else "The user just turned you on. Greet them warmly in one short sentence, in your persona.")
        } else {
            if (prefs.isWakeWordEnabled) startListeningForWakeWord()
            when {
                typed != null -> processCommand(typed)
                assist -> onWakeWordDetected()
            }
        }
    }

    // ========== TYPED TEXT & SCAN MODE (video 30:51) ==========

    /** The user typed to Faiza (dashboard box): she answers out loud, like a spoken request. */
    fun handleTyped(text: String) {
        lastUserActivityAt = System.currentTimeMillis()
        val session = live
        if (session != null && guardMode) {
            guardAttempt(text)
            return
        }
        when {
            session != null -> session.sendText(text)
            liveOk() && !micPaused -> startLive(text)
            else -> processCommand(text)
        }
    }

    /** A voice sample is playing in Settings: don't let the live conversation hear it. */
    fun setMicMuted(muted: Boolean) {
        live?.micMuted = muted
    }

    /** Quiet mode switched: go silent (after finishing the current sentence) or come back online. */
    private fun applyQuietMode() {
        if (QuietMode.manualActive()) {
            if (live != null) sleepLive() else {
                wakeEngine?.stop()
                wakeRestartJob?.cancel()
                speechRecognizer?.cancel()
                isActiveListening = false
                updateOrbState("idle")
            }
        } else if (live == null && !isActiveListening && !isProcessing) {
            resumeIdle()
        }
        refreshNotification()
    }

    // ========== LIFE MODES (Driving, Sleeping, Prayer…) ==========

    private val lifeModeReceiver = com.faiza.ai.ai.modes.LifeModeReceiver()

    /** Screen off / phone unlocked: Owner lock asks for the code again ("after each unlock"). */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) OwnerGuard.onScreenEvent()
            // Screen off (or battery/charger changed while off): keep listening only if allowed — rest the mic otherwise.
            if (live != null || isProcessing || isActiveListening) return
            if (wakeAllowedNow()) startListeningForWakeWord()
            else {
                wakeEngine?.stop()
                wakeRestartJob?.cancel()
                speechRecognizer?.cancel()
            }
        }
    }

    /** Calls and car Bluetooth while she runs (the manifest receiver covers the rest), plus a minute check. */
    private fun startLifeModes() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(lifeModeReceiver, filter, RECEIVER_EXPORTED)
            else registerReceiver(lifeModeReceiver, filter)
        }
        runCatching {
            registerReceiver(screenReceiver, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            })
        }
        scope.launch {
            while (true) {
                runCatching {
                    com.faiza.ai.ai.modes.ModeCenter.refreshPrayerTimes(this@FloatingOrbService)
                    com.faiza.ai.ai.modes.ModeCenter.checkTransition(this@FloatingOrbService)
                }
                runCatching { com.faiza.ai.ai.web.WeatherAlerts.tick(this@FloatingOrbService) }
                kotlinx.coroutines.delay(30_000)
            }
        }
    }

    /** Results from helpers, collected for a moment so several finishing together become one message. */
    private val doneResults = mutableListOf<String>()
    private var doneJob: Job? = null

    /** A helper finished: tell her (when she isn't mid-sentence), so she can tell the user and carry on. */
    private fun deliverTaskResult(tool: String, result: String) {
        synchronized(doneResults) { doneResults += "$tool → ${result.take(400)}" }
        if (doneJob?.isActive == true) return
        doneJob = scope.launch {
            delay(600)
            var waited = 0
            while (live?.isSpeaking == true && waited < 8_000) { delay(200); waited += 200 }
            val batch = synchronized(doneResults) { doneResults.toList().also { doneResults.clear() } }
            if (batch.isEmpty()) return@launch
            lastUserActivityAt = System.currentTimeMillis()
            live?.sendText(
                "[TASK DONE] " + batch.joinToString("\n") + "\nTell the user the outcome in ONE short sentence (say " +
                        "\"done\" only if it says OK; if ERROR / NOT SENT, say so honestly). If a task plan is running, " +
                        "call step_done and continue with the next step."
            )
        }
    }

    // ---------------- Calls ----------------

    /** A live chat started just to announce a call (ended again once the call is handled). */
    private var callLive = false
    /** We stopped the live chat so the phone call can have the microphone. */
    private var stoppedForCall = false
    /** A phone call is in progress: don't (re)open the live chat. */
    @Volatile private var inPhoneCall = false

    private fun onIncomingCall(line: String) {
        if (guardMode || OwnerGuard.needsCode() || !QuietMode.canSpeakUnprompted(this)) return
        val ask = "[INCOMING CALL] Say exactly: \"$line\" — then ask in a few words what to do (ধরব, কেটে দেব, নাকি " +
                "\"পরে কল দিচ্ছি\" মেসেজ পাঠাব?). When the user answers, use the incoming_call tool. If they say nothing, stay quiet."
        val session = live
        when {
            session != null -> session.interruptWith(ask)
            liveOk() && !micPaused -> { callLive = true; startLive(ask) }
            else -> speak(line)
        }
    }

    /** The user told her to answer / decline / reply. */
    fun onCallHandled(answered: Boolean) {
        scope.launch {
            if (answered) {
                // The call needs the microphone right away.
                delay(600)
                if (callLive || com.faiza.ai.ai.game.GameMode.active.value) { stopLive(endConversation = false); stoppedForCall = true }
                callLive = false
            } else if (callLive) {
                callLive = false
                delay(2500)
                sleepLive()
            }
        }
    }

    private fun onPhoneCallEnded(offhook: Boolean) {
        if (offhook) {
            inPhoneCall = true
            if (live != null && (callLive || com.faiza.ai.ai.game.GameMode.active.value)) { stopLive(endConversation = false); stoppedForCall = true }
            callLive = false
            return
        }
        inPhoneCall = false
        if (callLive) { callLive = false; sleepLive() }
        else if (stoppedForCall && live == null && !com.faiza.ai.ai.game.GameMode.active.value) resumeIdle()
        stoppedForCall = false
    }

    /** Driving: read the message out and offer to reply by voice. */
    private fun onDrivingMessage(line: String, app: String, who: String) {
        if (guardMode || OwnerGuard.needsCode() || !QuietMode.canSpeakUnprompted(this)) return
        val ask = "[DRIVING — new message] Say exactly: \"$line\". Then ask in a few words if they want to reply. If they " +
                "dictate a reply, read it back once in a few words and send it to $who on $app with your messaging tool " +
                "(confirmed=true after they agree). Keep everything very short — they are driving."
        val session = live
        when {
            session != null -> session.sendText(ask)
            liveOk() && !micPaused -> { callLive = true; startLive(ask) }
            else -> speak(line)
        }
    }

    // ---------------- Gaming mode ----------------

    private var gameLoop: Job? = null
    private var gameLeaveJob: Job? = null
    @Volatile private var lastModelSpeechAt = 0L
    @Volatile private var lastUserSpeechAt = 0L

    /** Voice/switch "on": start now if a game is on screen, otherwise wait for one. */
    fun requestGaming(): String {
        val gm = com.faiza.ai.ai.game.GameMode
        if (Build.VERSION.SDK_INT < 30) return "ERROR: gaming mode needs Android 11 or newer."
        val a = FaizaAccessibilityService.instance
            ?: return "ERROR: Accessibility Service is off — gaming mode needs it to see the screen. Ask the user to turn it on in Faiza settings."
        val pkg = a.foregroundPackage
        // Asked by the user: any app on screen counts (some games aren't marked as games), except the home screen.
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        if (gm.isGame(this, pkg) || (pkg.isNotBlank() && pkg != packageName && pkg != home && pkg != "com.android.systemui")) {
            scope.launch { delay(700); startGaming(pkg) }
            return "Starting gaming mode for ${gm.nameOf(this, pkg)} now. Say nothing more; the game commentary begins in a moment."
        }
        gm.armed = true
        return "Gaming mode is ready and will start by itself as soon as the user opens a game. Tell them in one short sentence."
    }

    private fun startGaming(pkg: String) {
        val gm = com.faiza.ai.ai.game.GameMode
        if (FaizaAccessibilityService.instance == null || Build.VERSION.SDK_INT < 30) return
        gm.begin(this, pkg)
        restartGamingSession()
        gameLoop?.cancel()
        gameLoop = scope.launch { runGameLoop() }
    }

    /** New live session with the gaming prompt (talk level / jokes changed, or the mode just started). */
    fun restartGamingSession() {
        scope.launch {
            if (live != null) { delay(1200); stopLive(endConversation = false); delay(400) }
            if (liveOk() && !micPaused) startLive(com.faiza.ai.ai.game.GameMode.startGreeting())
        }
    }

    fun stopGaming(sayBye: Boolean) {
        val gm = com.faiza.ai.ai.game.GameMode
        val wasActive = gm.active.value
        gm.end()
        gameLoop?.cancel(); gameLoop = null
        gameLeaveJob?.cancel(); gameLeaveJob = null
        if (!wasActive) return
        scope.launch {
            val report = com.faiza.ai.ai.game.GameStats.endNote(gm.gameName.ifBlank { "the game" })
            if (sayBye) live?.sendText("[GAME END] The user left the game. Gaming mode is off. " +
                    (report ?: "In ONE short line say well played."))
            delay(if (sayBye) (if (report != null) 12_000 else 4500) else 3500)
            sleepLive()
        }
    }

    /** One look + one tip, without the mode. */
    suspend fun gameGuideOnce(): String {
        val a = FaizaAccessibilityService.instance ?: return "ERROR: Accessibility Service is off."
        val s = live ?: return "No live voice chat — use look_at_screen to see the game instead."
        if (!s.acceptsImages) return "This voice model can't see images; use look_at_screen instead."
        val b64 = a.screenshotJpeg(maxSide = 768, quality = 55, fast = true) ?: return "ERROR: couldn't capture the screen."
        s.sendImage(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP))
        return "A picture of the user's game screen was just sent to you. Look at it and give ONE short, concrete tip " +
                "(enemies, where to go, what to do next) in the user's language, like a coach."
    }

    private fun onForegroundForGaming(pkg: String) {
        val gm = com.faiza.ai.ai.game.GameMode
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.isBlank()) return
        if (gm.active.value) {
            if (pkg == gm.gamePkg) { gameLeaveJob?.cancel(); gameLeaveJob = null; return }
            if (!prefs.gamingAutoOff || gameLeaveJob != null) return
            gameLeaveJob = scope.launch {
                delay(8_000)
                gameLeaveJob = null
                if (FaizaAccessibilityService.instance?.foregroundPackage != gm.gamePkg) stopGaming(sayBye = true)
            }
            return
        }
        if (!prefs.gamingEnabled || !gm.isGame(this, pkg)) return
        if (gm.armed || prefs.gamingAutoStart) { startGaming(pkg); return }
        if (prefs.gamingAsk && gm.asked.add(pkg) && QuietMode.canSpeakUnprompted(this) && !OwnerGuard.needsCode()) {
            val line = gm.offerLine(gm.nameOf(this, pkg))
            val s = live
            if (s != null) s.sendText(line) else if (liveOk() && !micPaused) startLive(line)
        }
    }

    /** About one screen picture a second, and a "your turn" nudge whenever she's been quiet for the talk level's gap. */
    private suspend fun runGameLoop() {
        val gm = com.faiza.ai.ai.game.GameMode
        var lastFrame = 0L
        var lastTick = SystemClock.elapsedRealtime()
        var lastRouteCheck = 0L
        var missing = 0
        while (gm.active.value) {
            lastUserActivityAt = System.currentTimeMillis() // playing isn't idle
            val session = live
            if (session == null) {
                if (inPhoneCall) { delay(1000); continue }
                missing++
                if (missing % 12 == 4 && liveOk() && !micPaused) startLive(gm.startGreeting())
                delay(500); continue
            }
            missing = 0
            if (!session.isReady) { delay(300); continue }
            if (!session.acceptsImages) {
                session.sendText("[GAME END] Tell the user in one short sentence that this voice model can't see the screen, so gaming mode can't work with it.")
                gm.end(); delay(4000); sleepLive(); return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastRouteCheck >= 2_000) {
                lastRouteCheck = now
                val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                session.followOtherAppCall(am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION)
            }
            val talk = gm.talk
            val q = gm.quality
            val callDue = !gm.quiet && !session.isSpeaking &&
                    now - lastTick >= talk.tickMs &&
                    now - lastModelSpeechAt >= talk.afterSpeechMs &&
                    now - lastUserSpeechAt >= 2_500
            if (callDue || now - lastFrame >= q.frameMs) {
                lastFrame = now
                // Right before a call: the whole screen + a zoom of the middle (far enemies, road ahead).
                FaizaAccessibilityService.instance?.gameShots(q.maxSide, q.jpeg, zoom = callDue && q.zoom)
                    ?.forEach { session.sendImage(it) }
            }
            if (callDue) {
                lastTick = SystemClock.elapsedRealtime()
                session.sendText(gm.TICK)
            }
            delay(120)
        }
    }

    // ---------------- Camera window (floating mini app) ----------------

    private var cameraWatch: Job? = null

    /** Android only lets a background service use the camera with the "camera" foreground type. */
    fun cameraWindowOpening() {
        if (Build.VERSION.SDK_INT < 30) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val mic = if (foregroundHasMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val special = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching {
            startForeground(NOTIFICATION_ID, createNotification("${prefs.assistantName(persona)} is looking"),
                mic or special or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        }
    }

    fun cameraWindowClosed() {
        cameraWatch?.cancel(); cameraWatch = null
        if (Build.VERSION.SDK_INT < 30) return
        val mic = if (foregroundHasMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val special = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        if (mic or special != 0) runCatching {
            startForeground(NOTIFICATION_ID, createNotification("${prefs.assistantName(persona)} is ready"), mic or special)
        }
    }

    /** Sends the camera window's picture to the live conversation. False = nothing to send / no live chat. */
    fun lookThroughCamera(): Boolean {
        val w = com.faiza.ai.ui.windows.WindowCenter.get(com.faiza.ai.ui.windows.WindowKind.CAMERA)
                as? com.faiza.ai.ui.windows.CameraWindow ?: return false
        val jpeg = w.frameJpeg() ?: return false
        val session = live
        if (session == null || !session.acceptsImages) {
            if (liveOk() && !micPaused) startLive("[CAMERA] The user opened your camera window to show you something. Ask what they want you to look at.")
            return false
        }
        session.sendImage(jpeg)
        return true
    }

    /** Keep looking: a fresh picture every ~2 s while the camera window is open. */
    fun setCameraWatch(on: Boolean) {
        cameraWatch?.cancel(); cameraWatch = null
        if (!on) return
        cameraWatch = scope.launch {
            repeat(150) { // about 5 minutes at most
                if (com.faiza.ai.ui.windows.WindowCenter.get(com.faiza.ai.ui.windows.WindowKind.CAMERA) == null) return@launch
                lookThroughCamera()
                delay(2000)
            }
        }
    }

    /** The research agent finished: let the live conversation tell the user (the notification is already shown). */
    fun deliverResearch(instruction: String) {
        live?.sendText("[RESEARCH RESULT] $instruction")
    }

    /** A "tell me in N seconds" timer finished: say it (the notification is already shown). */
    fun deliverTimer(text: String) {
        if (QuietMode.manualActive() || OwnerGuard.needsCode()) return
        val session = live
        when {
            session != null -> session.sendText("[TIMER] The timer the user asked for is done. Tell them now, exactly: \"$text\"")
            liveOk() && !micPaused -> startLive("[TIMER] The timer the user asked for is done. Say exactly: \"$text\"")
            else -> speak(text)
        }
    }

    /** Delete guard: say a warning right now. False = couldn't speak (quiet mode etc.), show a notification instead. */
    fun warnUser(text: String): Boolean {
        if (!QuietMode.canSpeakUnprompted(this) || OwnerGuard.needsCode()) return false
        val session = live
        if (session != null) session.interruptWith("[DELETE GUARD] Say exactly, urgently and caring: \"$text\"")
        else speak(text)
        return true
    }

    /** The avatar picture / switch changed in Settings. */
    fun onAvatarChanged() {
        runCatching { applyOrbAppearance() }
    }

    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */
    fun onAudioSettingsChanged() {
        if (live != null && !guardMode) restartLive()
    }

    /** A Life mode started or ended: update the notification and the dashboard. */
    fun onLifeModeChanged() {
        refreshNotification()
    }

    /** A Life mode ended by itself: tell the user who called (if she may speak). */
    fun onLifeModeEnded(summary: String) {
        refreshNotification()
        announce(summary)
    }

    private fun refreshNotification() {
        val mode = runCatching { com.faiza.ai.ai.modes.ModeCenter.current(this) }.getOrNull()
        val text = if (mode != null) "${mode.emoji} ${mode.name} mode — calls get a polite text"
        else if (QuietMode.manualActive()) "Quiet mode — she won't speak or listen on her own"
        else "${prefs.assistantName(persona)} is ready"
        runCatching {
            getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(text))
        }
    }

    /** Assistant button (power / home long-press, headset): start listening now. */
    fun activateFromAssist() {
        lastUserActivityAt = System.currentTimeMillis()
        when {
            live != null -> Unit // already listening
            micPaused -> Unit
            liveOk() -> startLive(ASSIST_GREETING)
            isProcessing || isActiveListening -> Unit
            else -> onWakeWordDetected()
        }
    }

    /** Scan mode wants Faiza listening and looking: start the live conversation if needed. */
    fun ensureLive(): Boolean {
        if (live == null && liveOk() && !micPaused) startLive(null)
        return live != null
    }

    val isLiveReady: Boolean get() = live?.isReady == true

    val canSeeCamera: Boolean get() = live?.acceptsImages != false

    /** One camera frame from Scan mode. */
    fun sendCameraFrame(jpeg: ByteArray) {
        lastUserActivityAt = System.currentTimeMillis() // looking together isn't "idle"
        live?.sendImage(jpeg)
    }

    fun sendInstruction(text: String) {
        live?.sendText(text)
    }

    /** Touch Guard: someone touched the phone while away — warn out loud and sound the siren. */
    fun touchGuardAlert(line: String) {
        com.faiza.ai.ai.guardian.TouchGuard.siren(this)
        val session = live
        if (session != null) session.interruptWith("[TOUCH GUARD] Say exactly, loud and stern: \"$line\"")
        else speak(line)
    }

    /** Event triggers: a short line Faiza says on her own (charger, battery, etc.). Honours quiet/silent choices. */
    fun announceEvent(text: String) {
        if (guardMode || OwnerGuard.needsCode()) return
        // Respect quiet mode / silent unless the user allowed "Speak on silent too".
        if (!prefs.isSpeakOnSilent && !QuietMode.canSpeakUnprompted(this)) return
        val session = live
        if (session != null) { session.sendText("[Phone event — mention it to the user in one short sentence]: $text"); return }
        if (!prefs.isSpeakWhileAsleep) return
        speak(text)
    }

    private fun announce(text: String) {
        // Quiet mode / phone on silent: never speak up by herself (the notification is still there).
        if (!QuietMode.canSpeakUnprompted(this)) return
        // Owner lock: never read the boss's notifications to someone who hasn't said the code.
        if (guardMode || OwnerGuard.needsCode()) return
        val session = live
        if (session != null) {
            session.sendText("[Phone notification — tell the user briefly, in your own words]: $text")
        } else {
            speak(text)
        }
    }

    // ========== LIVE MODE ==========

    private fun startLive(greeting: String?) {
        if (live != null || micPaused) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Faiza needs the microphone. Open Faiza AI → Settings → Phone access.", Toast.LENGTH_LONG).show()
            return
        }
        val key = prefs.geminiApiKey ?: return

        // Make sure Android knows we are a microphone service, and keep the CPU awake so the
        // conversation doesn't drop when the screen is off or another app is in front.
        runCatching { startAsForeground() }
        micBusyToastShown = false
        liveWakeLock = runCatching {
            (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "faiza:live")
                .apply { setReferenceCounted(false); acquire(30 * 60_000L) }
        }.getOrNull()

        // The live session needs the microphone to itself.
        wakeEngine?.stop()
        wakeRestartJob?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        utteranceCallbacks.clear()
        isActiveListening = false

        val gaming = com.faiza.ai.ai.game.GameMode.active.value
        // Gaming: a short prompt and only the tools a player needs — every extra token is delay on every call.
        val skills = if (gaming) agent?.allSkills().orEmpty().filter { it.id in com.faiza.ai.ai.game.GameMode.GAMING_TOOLS }
        else agent?.currentSkills().orEmpty()
        val tools = skills.map { it.spec } + GO_TO_SLEEP
        val basePrompt = if (gaming) com.faiza.ai.ai.game.GameMode.compactPersona(prefs.assistantName(persona), language.promptName)
        else agent?.liveSystemPrompt()
        val prompt = (basePrompt ?: "You are ${prefs.assistantName(persona)}, a friendly phone assistant.") +
                "\n\nLIVE VOICE CALL: you hear the user directly and answer with your voice. " +
                "When the user says goodbye, \"go to sleep\", \"ঘুমাও\", \"so jao\" or similar, say a short goodbye and call go_to_sleep." +
                (com.faiza.ai.ai.game.GameMode.promptAddon()?.let { "\n\n$it" } ?: "")

        // Owner lock: someone who hasn't said the secret code gets a guard with no tools and no knowledge of the code.
        guardMode = OwnerGuard.needsCode()
        guardHeard.setLength(0)
        guardJudge?.cancel()
        var openingText = greeting
        if (guardMode && greeting != null && !greeting.startsWith("The user just") && greeting != ASSIST_GREETING &&
            greeting != CINEMATIC_GREETING && !greeting.startsWith("[")
        ) {
            // They typed something: that's their first attempt.
            guardHeard.append(greeting)
            openingText = null
        }
        val liveTools = if (guardMode) emptyList() else tools
        val livePrompt = if (guardMode) OwnerLock.guardPrompt(prefs.assistantName(persona), ownerName(), language.promptName) else prompt
        val planNote = if (guardMode) null else com.faiza.ai.ai.skills.TaskPlan.resumeNote()
        val liveGreeting = when {
            guardMode -> "[GUARD] Say exactly: \"${OwnerLock.askLine(OwnerGuard.base)}\""
            planNote != null && (openingText == null || openingText.startsWith("The user just") ||
                    openingText == ASSIST_GREETING || openingText == CINEMATIC_GREETING) -> planNote
            else -> openingText
        }

        lastUserActivityAt = System.currentTimeMillis()
        proactiveNudges = 0
        lastNudgeAt = 0L
        strangerStrikes = 0
        blockToolsUntil = 0L
        verifier = guardianProfiles()?.let { allowed ->
            SpeakerVerifier(this, allowed, prefs.voiceGuardianThreshold) { v -> onGuardianVerdict(v) }
        }
        val tap = verifier?.let { v -> { data: ByteArray, len: Int -> v.feed(data, len) } }
        live = GeminiLiveSession(
            context = this,
            apiKey = key,
            model = prefs.liveModel,
            voice = prefs.liveVoiceFor(persona.id, persona.liveVoice),
            systemPrompt = livePrompt,
            tools = liveTools,
            // Gaming echo guard: game sound and her own voice must not cut her off mid-call.
            muteMicWhileSpeaking = !prefs.isBargeIn || prefs.isScreenRecordingMode || (gaming && prefs.gamingEchoGuard),
            listener = liveListener,
            audioTap = tap,
            forceSpeaker = prefs.isScreenRecordingMode,
            louderVoice = prefs.isLouderVoice,
            callSpeaker = prefs.isCallSpeaker,
            micGain = com.faiza.ai.ai.live.MicBoost.gainFor(prefs.micBoostLevel),
            bargeIn = prefs.isBargeIn,
            gamingFast = gaming,
            loudspeaker = prefs.isLoudspeaker
        ).also { it.start(liveGreeting) }
        if (guardMode && guardHeard.isNotEmpty()) scheduleGuardJudge(2_500)
        startLiveTicker()
    }

    // Live transcript pieces, saved to memory one whole sentence-turn at a time.
    private val userTranscript = StringBuilder()
    private val modelTranscript = StringBuilder()

    private fun flushTranscript(which: StringBuilder, who: String) {
        val text = which.toString().trim()
        which.setLength(0)
        if (text.isNotEmpty()) runCatching { MemoryStore.logLine(who, text) }
    }

    private fun stopLive(endConversation: Boolean = true) {
        guardJudge?.cancel()
        guardHeard.setLength(0)
        if (endConversation && live != null && !guardMode) OwnerGuard.onConversationEnded()
        flushTranscript(userTranscript, "user")
        flushTranscript(modelTranscript, "assistant")
        liveTicker?.cancel()
        liveTicker = null
        planWatch?.cancel()
        planWatch = null
        live?.stop()
        live = null
        verifier?.close()
        verifier = null
        liveWakeLock?.runCatching { if (isHeld) release() }
        liveWakeLock = null
    }

    // ========== VOICE GUARDIAN ==========

    /** Allowed voices when Voice Guardian should run, else null (off / everyone / not set up yet). */
    private fun guardianProfiles(): List<VoiceProfile>? {
        if (!prefs.isVoiceGuardianEnabled) return null
        val mode = prefs.voiceGuardianMode
        if (mode == PrefsManager.VG_MODE_EVERYONE) return null
        if (!WakeModel.isReady(this) || !SpeakerModelFiles.isReady(this)) return null
        val all = VoicePrint.load(prefs)
        if (all.none { it.isOwner }) return null
        return if (mode == PrefsManager.VG_MODE_FAMILY) all else all.filter { it.isOwner }
    }

    private fun ownerName(): String =
        VoicePrint.load(prefs).firstOrNull { it.isOwner }?.name?.takeIf { it.isNotBlank() }
            ?: prefs.userName?.takeIf { it.isNotBlank() } ?: "the owner"

    private fun onGuardianVerdict(v: SpeakerVerifier.Verdict) {
        if (!v.reliable || live == null) return
        if (guardMode) {
            // Voice Guardian recognised the boss's own voice: no code needed.
            if (v.allowed && prefs.voiceGuardianMode == PrefsManager.VG_MODE_OWNER) ownerUnlocked()
            return
        }
        if (v.allowed) {
            strangerStrikes = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastStrikeAt > STRIKE_WINDOW_MS) strangerStrikes = 0
        strangerStrikes++
        lastStrikeAt = now
        blockToolsUntil = now + 15_000
        val owner = ownerName()

        if (prefs.isVoiceGuardianAway && strangerStrikes >= 2) {
            live?.interruptWith("[VOICE GUARDIAN: a stranger tried again. Say only one short sentence that you are locking the phone now. Nothing else.]")
            scope.launch {
                delay(3000)
                FaizaAccessibilityService.instance?.lockScreen()
                strangerStrikes = 0
                stopLive()
                resumeIdle()
            }
        } else {
            val warn = if (prefs.isVoiceGuardianAway) " Warn them that if they try again, the phone will be locked." else ""
            live?.interruptWith(
                "[VOICE GUARDIAN: the last request came from someone who is NOT $owner (voice match ${v.percent}%). " +
                        "Do NOT do what they asked. In one short sentence, politely tell them this is $owner's phone " +
                        "and only $owner can use it.$warn]"
            )
        }
    }

    // ========== OWNER LOCK (secret code) ==========

    /** They stopped talking without saying the code: judge what they said. */
    private fun scheduleGuardJudge(afterMs: Long) {
        guardJudge?.cancel()
        guardJudge = scope.launch {
            delay(afterMs)
            val heard = guardHeard.toString().trim()
            guardHeard.setLength(0)
            if (heard.isNotEmpty() && guardMode) guardAttempt(heard)
        }
    }

    private fun guardAttempt(text: String) {
        val o = OwnerGuard.attempt(this, text, lockAfterMs = 7_000)
        when (o.kind) {
            OwnerGuard.Kind.UNLOCKED -> ownerUnlocked()
            OwnerGuard.Kind.LOCKING -> {
                live?.interruptWith("[GUARD] Say exactly, sternly and quickly: \"${o.line}\"")
                scope.launch {
                    delay(7_500)
                    stopLive()
                    resumeIdle()
                }
            }
            else -> live?.interruptWith("[GUARD] Say exactly, in a stern voice: \"${o.line}\"")
        }
    }

    /** The secret code was said (or the boss's voice recognised): a normal conversation with all her skills. */
    private fun ownerUnlocked() {
        if (!guardMode) return
        OwnerGuard.markVerified()
        guardMode = false
        stopLive(endConversation = false)
        startLive("[The owner just proved who they are. Say exactly, warmly: \"${OwnerLock.welcomeLine(OwnerGuard.base)}\"]")
    }

    // ========== MIC SHARING ==========

    /** Frees the microphone for another screen (voice enrolment / test). */
    fun pauseMic() {
        micPaused = true
        stopLive()
        wakeEngine?.stop()
        wakeRestartJob?.cancel()
        speechRecognizer?.cancel()
        isActiveListening = false
        updateOrbState("idle")
    }

    fun resumeMic() {
        if (!micPaused) return
        micPaused = false
        resumeIdle()
    }

    private fun restartLive() {
        stopLive(endConversation = false)
        if (liveOk()) startLive(null)
    }

    /** Ends the live conversation (after Faiza finishes speaking) and waits for a tap or the wake word. */
    private fun sleepLive() {
        scope.launch {
            var waited = 0
            while (live?.isSpeaking == true && waited < 8000) {
                delay(200)
                waited += 200
            }
            stopLive()
            resumeIdle()
        }
    }

    /**
     * If a confirmed plan still has tasks but she has gone quiet (no tool, not speaking, the user silent for a few
     * seconds), hand her the next task again. At most 3 reminders per step, so it can't loop.
     */
    private fun startPlanWatch() {
        planWatch?.cancel()
        planWatch = scope.launch {
            var step = -1
            var nudges = 0
            while (true) {
                delay(2_000)
                val session = live ?: break
                val plan = runCatching { com.faiza.ai.ai.skills.TaskPlan.get() }.getOrNull()
                if (plan == null || !plan.running) { step = -1; nudges = 0; continue }
                if (plan.current != step) { step = plan.current; nudges = 0 }
                val quietFor = System.currentTimeMillis() - lastUserActivityAt
                if (session.isSpeaking || quietFor < 4_500 || nudges >= 3 || com.faiza.ai.ai.skills.TaskLanes.busy) continue
                nudges++
                lastUserActivityAt = System.currentTimeMillis()
                session.sendText("[PLAN CONTINUE] The user already confirmed the whole plan — do not ask anything, just carry on.\n" +
                        plan.nextInstruction())
            }
        }
    }

    private fun startLiveTicker() {
        startPlanWatch()
        liveTicker?.cancel()
        liveTicker = scope.launch {
            while (true) {
                delay(15_000)
                if (!prefs.hasFullAccess) {
                    FaizaApp.instance.licenseManager.updateTrialUsage(0.25f)
                    if (prefs.trialMinutesUsed >= 10f) {
                        stopLive()
                        updateOrbState("idle")
                        speak(Phrases.trialEnded(language))
                        return@launch
                    }
                }
                val now = System.currentTimeMillis()
                val idleFor = now - lastUserActivityAt
                if (prefs.isProactiveChat && !guardMode && !com.faiza.ai.ai.game.GameMode.active.value && live?.isSpeaking != true && proactiveNudges < PROACTIVE_MAX_NUDGES &&
                    QuietMode.canSpeakUnprompted(this@FloatingOrbService) &&
                    idleFor > PROACTIVE_AFTER_IDLE_MS && now - lastNudgeAt > PROACTIVE_GAP_MS
                ) {
                    proactiveNudges++
                    lastNudgeAt = now
                    live?.sendText(PROACTIVE_NUDGE)
                }
                val sleepMs = prefs.liveSleepMinutes * 60_000L
                if (sleepMs > 0 && idleFor > sleepMs && live?.isSpeaking != true) {
                    sleepLive()
                    return@launch
                }
            }
        }
    }

    private val liveListener = object : GeminiLiveSession.Listener {
        override fun onState(state: GeminiLiveSession.State) {
            if (live == null && state != GeminiLiveSession.State.CLOSED) return
            when (state) {
                GeminiLiveSession.State.CONNECTING -> updateOrbState("connecting")
                GeminiLiveSession.State.LISTENING -> updateOrbState("listening")
                GeminiLiveSession.State.SPEAKING -> updateOrbState("speaking")
                GeminiLiveSession.State.WORKING -> updateOrbState("processing")
                GeminiLiveSession.State.MIC_BUSY -> updateOrbState("micbusy")
                GeminiLiveSession.State.CLOSED -> if (live == null) updateOrbState("idle")
            }
        }

        override fun onUserSpeech(text: String) {
            if (guardMode) {
                // Never log what a stranger (or the secret code) says; check for the code as the words arrive.
                modelTranscript.setLength(0)
                guardHeard.append(text)
                if (OwnerGuard.saysCode(guardHeard.toString())) ownerUnlocked() else scheduleGuardJudge(1_600)
                return
            }
            flushTranscript(modelTranscript, "assistant")
            if (userTranscript.isEmpty()) lastUserSpeechStart = SystemClock.elapsedRealtime()
            lastUserSpeechAt = SystemClock.elapsedRealtime()
            proactiveNudges = 0
            userTranscript.append(text)
            lastUserActivityAt = System.currentTimeMillis()
        }

        override fun onModelSpeech(text: String) {
            lastModelSpeechAt = SystemClock.elapsedRealtime()
            if (guardMode) return
            flushTranscript(userTranscript, "user")
            modelTranscript.append(text)
        }

        override suspend fun onToolCall(raw: ToolCall): String {
            val call = com.faiza.ai.ai.skills.ToolRouter.unwrap(raw)
            lastUserActivityAt = System.currentTimeMillis()
            if (call.name == GO_TO_SLEEP.name) {
                sleepLive()
                return "OK: going to sleep"
            }
            verifier?.let { v ->
                // Wait briefly for the voice check of what was just said before acting on it.
                val verdict = v.awaitVerdictAfter(lastUserSpeechStart - 1_500, 2_500)
                val stranger = verdict != null && verdict.reliable && !verdict.allowed
                if (stranger || SystemClock.elapsedRealtime() < blockToolsUntil) {
                    return "BLOCKED by Voice Guardian: this request did not come from ${ownerName()}'s voice. " +
                            "Do not do it. Politely say only ${ownerName()} can use this phone."
                }
            }
            val a = agent ?: return "ERROR: AI is not configured"
            // Slow, self-contained actions go to a helper; she keeps talking and hears back via [TASK DONE].
            if (com.faiza.ai.ai.skills.TaskLanes.laneOf(call.name) != null) {
                return com.faiza.ai.ai.skills.TaskLanes.submit(call, { c -> a.executeTool(c) }) { c, result ->
                    deliverTaskResult(c.name, result)
                }
            }
            // Anything that looks at / touches the screen waits until earlier phone tasks are finished.
            if (com.faiza.ai.ai.skills.TaskLanes.needsScreen(call.name)) com.faiza.ai.ai.skills.TaskLanes.awaitPhoneIdle()
            return a.executeTool(call)
        }

        override fun onMicBlocked(blocked: Boolean) {
            // In a game the game's voice chat often holds the mic: she keeps talking, no warning needed.
            if (blocked && com.faiza.ai.ai.game.GameMode.active.value) { com.faiza.ai.ai.game.GameMode.micTaken = true; return }
            if (!blocked) com.faiza.ai.ai.game.GameMode.micTaken = false
            if (blocked && !micBusyToastShown) {
                micBusyToastShown = true
                Toast.makeText(
                    this@FloatingOrbService,
                    "Another app is using the microphone. Close it and Faiza will hear you again.",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (!blocked) lastUserActivityAt = System.currentTimeMillis() // don't fall asleep right after recovering
        }

        override fun onFatalError(message: String) {
            stopLive()
            updateOrbState("idle")
            Toast.makeText(this@FloatingOrbService, "Faiza Live: $message", Toast.LENGTH_LONG).show()
            resumeIdle()
        }
    }

    @Volatile private var foregroundHasMic = false

    /**
     * Runs the service in the foreground. The "microphone" type is what lets Android keep giving
     * us the mic while another app is on screen, so we re-run this whenever the permission appears.
     */
    private fun startAsForeground() {
        val notification = createNotification("${prefs.assistantName(persona)} is ready")
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 30 && micGranted) {
            val type = if (Build.VERSION.SDK_INT >= 34)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try {
                startForeground(NOTIFICATION_ID, notification, type)
                foregroundHasMic = true
                return
            } catch (_: Exception) {
                // Android refuses the microphone type when the app isn't on screen (e.g. started at boot).
                // Keep running without it; it is added next time the app is opened.
            }
        }
        if (foregroundHasMic) return // never downgrade a service that already has the microphone type
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** The Dashboard pings us each time it opens: add the microphone type if it was missing. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundHasMic) runCatching { startAsForeground() }
        if (intent?.action == ACTION_TOGGLE_QUIET) QuietMode.set(!QuietMode.manualActive())
        return START_STICKY
    }

    private fun rebuildAgent() {
        agent = AIProviders.fromPrefs(prefs)?.let { FaizaAgent(this, it) }
    }

    // ========== ORB ==========

    private fun createFloatingOrb() {
        orbView = LayoutInflater.from(this).inflate(R.layout.floating_orb, null)
        orbArt = orbView.findViewById(R.id.orbArt)
        avatarArt = orbView.findViewById(R.id.avatarArt)
        avatarArt.voiceLevel = { live?.voiceLevel() ?: 0f }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        orbView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(orbView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onOrbTapped()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(orbView, params)
        applyOrbAppearance()
        updateOrbState("idle")
    }

    // ========== ORB LOOK (video 25:56) ==========

    /** Design, colour and size from Settings. Pro designs fall back to the free sphere without a licence. */
    private fun applyOrbAppearance() {
        val design = OrbDesign.from(prefs.floatingOrbDesign)
        val allowed = if (design.isPro && !FaizaApp.instance.licenseManager.isPro()) OrbDesign.SPHERE else design
        val theme = OrbTheme.from(prefs.orbTheme)
        orbArt.design = allowed
        orbArt.theme = theme
        val px = (76 * resources.displayMetrics.density * prefs.orbSizePercent / 100f).toInt()
        orbArt.layoutParams = LinearLayout.LayoutParams(px, px)
        // Living picture instead of the orb.
        val useAvatar = com.faiza.ai.ui.orb.AvatarStore.inUse(this)
        orbArt.visibility = if (useAvatar) View.GONE else View.VISIBLE
        avatarArt.visibility = if (useAvatar) View.VISIBLE else View.GONE
        if (useAvatar) {
            val apx = (px * 1.18f).toInt()
            avatarArt.layoutParams = LinearLayout.LayoutParams(apx, apx)
            avatarArt.theme = theme
            avatarArt.reload()
        }
        edgeGlow?.setTheme(theme)
        runCatching { windowManager.updateViewLayout(orbView, params) }
    }

    private fun homePackage(): String? {
        homePackageName?.let { return it }
        return packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            .also { homePackageName = it }
    }

    /** Shown on the home screen (and Faiza's own screens); hidden inside other apps when that option is on. */
    private fun updateOrbVisibility() {
        val insideOtherApp = prefs.isOrbHiddenInApps &&
                FaizaAccessibilityService.instance != null &&
                topPackage.isNotEmpty() &&
                topPackage != packageName &&
                topPackage != homePackage()
        // Bubble switched off / hidden in apps, but she was called ("Hey Faiza", "Wake up Faiza"…): show it while she
        // listens and talks, then hide it again. Not during a game (it would sit on top of the game).
        val summoned = lastOrbState != "idle" && !com.faiza.ai.ai.game.GameMode.active.value
        orbView.visibility = if (summoned || (prefs.isOrbEnabled && !insideOtherApp)) View.VISIBLE else View.GONE
    }

    // ========== MACRO RECORDING PILL ==========

    private fun showRecordingPill(state: MacroRecorder.State?) {
        if (state == null) {
            recPill?.let { runCatching { windowManager.removeView(it) } }
            recPill = null
            return
        }
        val density = resources.displayMetrics.density
        val pill = recPill ?: TextView(this).apply {
            setBackgroundResource(R.drawable.bg_rec_pill)
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
        }.also { p ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (40 * density).toInt()
            }
            lp.alpha = passThroughAlpha()
            if (runCatching { windowManager.addView(p, lp) }.isFailure) return
            recPill = p
        }
        val n = state.steps.size
        val text = android.text.SpannableString("●  REC  ${state.name} · $n ${if (n == 1) "step" else "steps"}")
        text.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.danger)), 0, 1, 0)
        pill.text = text
    }

    private var macroPill: TextView? = null

    /** "▶ Notes · 3/7" while a macro is playing. */
    private fun showMacroPill(p: Triple<String, Int, Int>?) {
        if (p == null) {
            macroPill?.let { runCatching { windowManager.removeView(it) } }
            macroPill = null
            return
        }
        val density = resources.displayMetrics.density
        val pill = macroPill ?: TextView(this).apply {
            setBackgroundResource(R.drawable.bg_macro_pill)
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding((14 * density).toInt(), (7 * density).toInt(), (14 * density).toInt(), (7 * density).toInt())
        }.also { v ->
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (84 * density).toInt()
            }
            lp.alpha = passThroughAlpha()
            if (runCatching { windowManager.addView(v, lp) }.isFailure) return
            macroPill = v
        }
        pill.text = "▶  ${p.first} · ${p.second}/${p.third}"
    }

    // ========== EDGE GLOW (video 27:13) ==========

    /**
     * Android 12+ blocks every touch that passes through another app's window unless that window is see-through
     * enough ("untrusted touch occlusion"). Our pass-through overlays stay just under that limit.
     */
    private fun passThroughAlpha(): Float {
        val max = if (android.os.Build.VERSION.SDK_INT >= 31)
            runCatching { getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch }
                .getOrDefault(0.8f)
        else 1f
        return (max - 0.01f).coerceIn(0.1f, 1f)
    }

    private fun ensureEdgeGlow() {
        if (!prefs.isOrbEdgeGlow) {
            removeEdgeGlow()
            return
        }
        glowRemoveJob?.cancel()
        edgeGlow?.let {
            it.setRgb(prefs.isEdgeGlowRgb)
            return
        }
        val glow = EdgeGlowView(this).apply {
            setTheme(OrbTheme.from(prefs.orbTheme))
            setRgb(prefs.isEdgeGlowRgb)
            voiceLevel = { live?.voiceLevel() ?: 0f }
        }
        if (runCatching { windowManager.addView(glow, edgeGlowParams()) }.isSuccess) edgeGlow = glow
    }

    /**
     * The whole physical screen, edge to edge on every phone: behind the status and navigation bars and into the
     * camera cut-out (by default Android shrinks overlays to the space between the bars, leaving gaps top and bottom).
     */
    @Suppress("DEPRECATION")
    private fun edgeGlowParams(): WindowManager.LayoutParams {
        val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = passThroughAlpha()
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private var glowRemoveJob: kotlinx.coroutines.Job? = null

    /** Screen rotated: the glow follows the new screen size. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeGlow?.let { g -> runCatching { windowManager.updateViewLayout(g, edgeGlowParams()) } }
    }

    private fun removeEdgeGlow() {
        edgeGlow?.let { runCatching { windowManager.removeView(it) } }
        edgeGlow = null
    }

    private fun onOrbTapped() {
        // Tapping her stops anything running by itself.
        if (com.faiza.ai.ai.skills.AutoScroll.stop()) return
        val session = live
        if (session != null) {
            // While she's talking, a tap means "stop and listen to me" — the conversation goes on.
            if (session.isSpeaking) { session.userInterrupt(); return }
            sleepLive()
            return
        }
        if (liveOk()) {
            startLive("The user tapped you to talk. Say a very short hello (2-4 words) and listen.")
            return
        }
        if (isProcessing) return
        if (isActiveListening) {
            speechRecognizer?.cancel()
            isActiveListening = false
            resumeIdle()
        } else {
            tts?.stop()
            utteranceCallbacks.clear()
            startActiveListening()
        }
    }

    private fun updateOrbState(state: String) {
        val wasIdle = lastOrbState == "idle"
        lastOrbState = state
        if (wasIdle != (state == "idle")) updateOrbVisibility()
        val status = orbView.findViewById<TextView>(R.id.orbStatus) ?: return
        val (mode, iconRes, label) = when (state) {
            "connecting" -> Triple(OrbMode.CONNECTING, R.drawable.ic_timer, "Connecting…")
            "micbusy" -> Triple(OrbMode.MIC_BUSY, R.drawable.ic_volume_off, "Mic busy…")
            "listening" -> Triple(OrbMode.LISTENING, R.drawable.ic_mic, "Listening…")
            "active-listening" -> Triple(OrbMode.LISTENING, R.drawable.ic_mic, "Speak now…")
            "processing" -> Triple(OrbMode.WORKING, R.drawable.ic_bolt, "Working…")
            "speaking" -> Triple(OrbMode.SPEAKING, R.drawable.ic_wave, "Speaking…")
            else -> Triple(OrbMode.IDLE, R.drawable.ic_wave, "Tap to talk")
        }
        orbArt.setIconRes(iconRes)
        orbArt.mode = mode
        if (::avatarArt.isInitialized) avatarArt.mode = mode
        status.text = label
        orbArt.contentDescription = label // no visible label under the orb; screen readers still hear it

        if (mode == OrbMode.IDLE) {
            // Fade out, then take the window away completely: nothing covers the screen while she's idle.
            edgeGlow?.setMode(mode)
            glowRemoveJob?.cancel()
            glowRemoveJob = scope.launch {
                delay(700)
                removeEdgeGlow()
            }
        } else {
            ensureEdgeGlow()
            edgeGlow?.setMode(mode)
        }
    }

    // ========== LISTENING ==========

    private fun recreateRecognizer(): SpeechRecognizer {
        speechRecognizer?.destroy()
        // Not SpeechRecognizer's default: as the default assistant, that would be Faiza herself.
        return com.faiza.ai.assist.RecognizerPicker.create(this).also { speechRecognizer = it }
    }

    private fun recognizerIntent(partialResults: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // No internet: use the phone's offline speech recognition if it has it.
            if (!com.faiza.ai.ai.offline.OfflineCommands.online(this@FloatingOrbService)) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun customWakeNames(): List<String> =
        listOfNotNull(prefs.assistantNameSetting.takeIf { it.isNotBlank() })

    /** Screen on: yes. Screen off: per "Screen on only" / smart battery (charging or 30%+). */
    private fun wakeAllowedNow(): Boolean {
        if ((getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return true
        val (pct, charging) = com.faiza.ai.ai.wake.WakePower.battery(this)
        return com.faiza.ai.ai.wake.WakePower.allowScreenOff(prefs.isWakeScreenOnly, prefs.isWakeSmartBattery, pct, charging)
    }

    private fun startListeningForWakeWord() {
        if (micPaused || live != null || isProcessing || isActiveListening || !prefs.isWakeWordEnabled) return
        if (QuietMode.manualActive()) return // quiet mode: no always-on listening
        if (!wakeAllowedNow()) return // screen off + battery rule: listening resumes when the screen turns on / charger
        wakeRestartJob?.cancel()

        val engine = wakeEngine
        if (engine != null && engine.available && !wakeEngineFailed && WakeModel.isReady(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = null
            engine.start()
            return
        }
        // Fallback while the offline model downloads (or if it can't run on this phone).

        val recognizer = recreateRecognizer()
        recognizer.setRecognitionListener(object : SimpleRecognitionListener() {
            override fun onResults(results: Bundle?) {
                fallbackErrors = 0
                if (Phrases.isWakeWord(firstResult(results), customWakeNames())) onWakeWordDetected()
                else restartWakeWordSoon(300)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (Phrases.isWakeWord(firstResult(partialResults), customWakeNames())) recognizer.stopListening()
            }

            // Errors (no speech, busy, no network) come back-to-back: wait longer each time, up to 20 s.
            override fun onError(error: Int) {
                fallbackErrors = (fallbackErrors + 1).coerceAtMost(6)
                restartWakeWordSoon(minOf(20_000L, 500L shl fallbackErrors))
            }
        })

        try {
            recognizer.startListening(recognizerIntent(partialResults = true))
        } catch (_: Exception) {
            restartWakeWordSoon(2000)
        }
    }

    /** Back-to-back errors of the fallback recogniser (reset when it hears something). */
    private var fallbackErrors = 0

    private fun restartWakeWordSoon(delayMs: Long) {
        wakeRestartJob?.cancel()
        wakeRestartJob = scope.launch {
            delay(delayMs)
            startListeningForWakeWord()
        }
    }

    private fun onWakeWordDetected() {
        wakeRestartJob?.cancel()
        if (liveOk()) {
            speechRecognizer?.cancel()
            startLive("The user called your name. Answer with a very short \"yes, I'm listening\" in your persona.")
            return
        }
        isActiveListening = true // block the wake loop while we reply
        updateOrbState("listening")
        speak(Phrases.wakeReply(persona, language)) {
            isActiveListening = false
            startActiveListening()
        }
    }

    private fun startActiveListening() {
        if (isProcessing || micPaused) return
        wakeEngine?.stop()
        wakeRestartJob?.cancel()
        isActiveListening = true
        updateOrbState("active-listening")

        val recognizer = recreateRecognizer()
        recognizer.setRecognitionListener(object : SimpleRecognitionListener() {
            override fun onReadyForSpeech(params: Bundle?) = updateOrbState("active-listening")
            override fun onEndOfSpeech() = updateOrbState("processing")

            override fun onResults(results: Bundle?) {
                isActiveListening = false
                val command = firstResult(results)
                if (command.isNotBlank()) processCommand(command) else resumeIdle()
            }

            override fun onError(error: Int) {
                isActiveListening = false
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    updateOrbState("speaking")
                    speak(Phrases.didntHear(language)) { resumeIdle() }
                } else {
                    resumeIdle()
                }
            }
        })

        try {
            recognizer.startListening(recognizerIntent(partialResults = false))
        } catch (_: Exception) {
            isActiveListening = false
            resumeIdle()
        }
    }

    private fun resumeIdle() {
        updateOrbState("idle")
        startListeningForWakeWord()
    }

    // ========== COMMANDS ==========

    /** Volume-down while she talks (from the accessibility service): stop and listen. True if she was talking. */
    fun interruptIfSpeaking(): Boolean {
        val s = live ?: return false
        if (!s.isSpeaking) return false
        s.userInterrupt()
        return true
    }

    /** The live voice chat needs the internet; without it, simple commands are done offline. */
    private fun liveOk(): Boolean = prefs.canUseLive() && com.faiza.ai.ai.offline.OfflineCommands.online(this)

    private fun processCommand(command: String) {
        if (!com.faiza.ai.ai.offline.OfflineCommands.online(this)) {
            val oc = com.faiza.ai.ai.offline.OfflineCommands
            val bangla = language.base == com.faiza.ai.ai.PhraseBase.BENGALI
            updateOrbState("speaking")
            scope.launch {
                val say = oc.parse(command)?.let { c -> runCatching { oc.run(this@FloatingOrbService, c, bangla) }.getOrElse { it.message ?: "Error" } }
                    ?: oc.cantLine(bangla)
                speak(say) { resumeIdle() }
            }
            return
        }
        if (!prefs.hasFullAccess && prefs.trialMinutesUsed >= 10f) {
            updateOrbState("speaking")
            speak(Phrases.trialEnded(language)) { resumeIdle() }
            return
        }

        isProcessing = true
        updateOrbState("processing")

        scope.launch {
            val currentAgent = agent
            val response = if (currentAgent == null) {
                Phrases.noApiKey(language)
            } else try {
                currentAgent.handle(command)
            } catch (e: AIProviderException) {
                when {
                    e.isAuthError -> Phrases.apiKeyInvalid(language)
                    e.isRateLimit -> Phrases.rateLimited(language)
                    else -> Phrases.genericError(language)
                }
            } catch (_: IOException) {
                Phrases.networkError(language)
            } catch (_: Exception) {
                Phrases.genericError(language)
            }

            if (!prefs.hasFullAccess) {
                FaizaApp.instance.licenseManager.updateTrialUsage(0.5f)
            }

            isProcessing = false
            updateOrbState("speaking")
            // If Faiza asked something (e.g. "Shall I send it?"), listen for the answer right away.
            val expectsAnswer = response.trimEnd().endsWith("?")
            speak(response) { if (expectsAnswer) startActiveListening() else resumeIdle() }
        }
    }

    // ========== SPEECH ==========

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ttsReady = true
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finishUtterance(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishUtterance(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance(utteranceId)
        })
        applyVoiceSettings()
        if (live == null) speak(Phrases.greeting(persona, language, prefs.assistantName(persona)))
    }

    private fun applyVoiceSettings() {
        val engine = tts ?: return
        if (!ttsReady) return
        val result = engine.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
            Toast.makeText(this, Phrases.ttsMissing(language), Toast.LENGTH_LONG).show()
        }
        val style = com.faiza.ai.ai.SpeakingStyle.from(prefs.speakingStyle)
        engine.setPitch(persona.pitch * style.pitchFactor)
        engine.setSpeechRate(persona.speechRate * style.rateFactor)
    }

    /** Speaks [text]; [onDone] runs on the main thread once speech finishes (or fails). */
    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null || !ttsReady) {
            onDone?.invoke()
            return
        }
        val id = "faiza_${System.nanoTime()}"
        if (onDone != null) utteranceCallbacks[id] = onDone
        if (engine.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) {
            utteranceCallbacks.remove(id)
            onDone?.invoke()
        }
    }

    private fun finishUtterance(id: String?) {
        val callback = id?.let { utteranceCallbacks.remove(it) } ?: return
        scope.launch { callback() }
    }

    // ========== MISC ==========

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // One tap from the notification shade: go quiet / come back (no need to open the app).
        val quiet = QuietMode.manualActive()
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingOrbService::class.java).setAction(ACTION_TOGGLE_QUIET),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FaizaApp.CHANNEL_FOREGROUND)
            .setContentTitle("Faiza AI")
            .setContentText(if (quiet) "Quiet mode — she won't speak or listen on her own" else text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(0, if (quiet) "Wake up" else "Go quiet", toggle)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        com.faiza.ai.ai.game.GameMode.end()
        runCatching { com.faiza.ai.ui.windows.WindowCenter.closeAll() }
        isRunning = false
        instance = null
        FaizaAccessibilityService.foregroundListener = null
        removeEdgeGlow()
        showRecordingPill(null)
        showMacroPill(null)
        prefs.unregisterListener(prefsListener)
        runCatching { unregisterReceiver(lifeModeReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        EventTriggers.stop(this)
        com.faiza.ai.ai.guardian.TouchGuard.stop(this)
        wakeEngine?.release()
        wakeEngine = null
        stopLive()
        announcer?.stop()
        scope.cancel()
        speechRecognizer?.destroy()
        tts?.shutdown()
        try {
            windowManager.removeView(orbView)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private abstract class SimpleRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
