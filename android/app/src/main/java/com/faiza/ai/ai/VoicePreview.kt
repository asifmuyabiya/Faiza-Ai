package com.faiza.ai.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.service.FloatingOrbService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Lets the user hear a voice + speaking style before choosing it. Uses the same Gemini Live model as the
 * conversation (so it shares the quota that already works); the separate text-to-speech model is only a backup
 * — on free keys it often has no quota at all.
 */
object VoicePreview {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private const val LIVE_URL =
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    private const val FALLBACK_TTS_MODEL = "gemini-2.5-flash-preview-tts"
    private const val RATE = 24_000
    private const val LIVE_TIMEOUT_MS = 25_000L
    private val JSON = "application/json; charset=utf-8".toMediaType()

    @Volatile private var ttsModel: String? = null
    private var track: AudioTrack? = null

    class PreviewException(message: String) : Exception(message)

    /** Speaks [text] in [voice], following [direction] (e.g. "Say sleepily"). Throws [PreviewException]. */
    suspend fun play(voice: String, direction: String, text: String) {
        val prefs = FaizaApp.instance.prefs
        val key = prefs.geminiApiKey?.trim().orEmpty()
        if (key.isEmpty()) throw PreviewException("Add your Gemini API key first (Settings → AI engine).")

        val pcm = withContext(Dispatchers.IO) {
            val live = runCatching { viaLive(key, prefs.liveModel, voice, direction, text) }
            live.getOrNull()?.takeIf { it.size > 4_800 } // at least 0.1 s of sound
                ?: runCatching { viaTts(key, voice, "$direction: $text") }.getOrElse { e ->
                    throw PreviewException(friendly(live.exceptionOrNull()?.message ?: e.message))
                }
        }
        withContext(Dispatchers.Main) { playPcm(pcm) }
    }

    private fun friendly(msg: String?): String {
        val m = msg.orEmpty()
        return when {
            m.contains("quota", true) || m.contains("exhausted", true) || m.contains("429") ->
                "Your Gemini key has used up its free limit for now. Try the preview again in a few minutes."
            m.isBlank() -> "Couldn't play the sample — check the internet."
            else -> "Couldn't play the sample: ${m.take(120)}"
        }
    }

    fun stop() {
        track?.runCatching { stop(); release() }
        track = null
        FloatingOrbService.instance?.setMicMuted(false)
    }

    // ===== Live model (same as the conversation) =====

    private suspend fun viaLive(key: String, model: String, voice: String, direction: String, text: String): ByteArray {
        val audio = ByteArrayOutputStream()
        var failure: String? = null
        val result = withTimeoutOrNull(LIVE_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val ws = AIProviders.http.newWebSocket(
                    Request.Builder().url("$LIVE_URL?key=$key").build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(setup(model, voice, direction).toString())
                        }

                        override fun onMessage(webSocket: WebSocket, text2: String) = handle(webSocket, text2)
                        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = handle(webSocket, bytes.utf8())

                        private fun handle(webSocket: WebSocket, raw: String) {
                            val msg = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return
                            if (msg.has("setupComplete")) {
                                webSocket.send(JsonObject().apply {
                                    add("clientContent", JsonObject().apply {
                                        add("turns", JsonArray().apply {
                                            add(JsonObject().apply {
                                                addProperty("role", "user")
                                                add("parts", JsonArray().apply {
                                                    add(JsonObject().apply { addProperty("text", text) })
                                                })
                                            })
                                        })
                                        addProperty("turnComplete", true)
                                    })
                                }.toString())
                                return
                            }
                            val content = msg.getAsJsonObject("serverContent") ?: return
                            content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { p ->
                                p.asJsonObject.getAsJsonObject("inlineData")?.get("data")?.asString?.let {
                                    synchronized(audio) { audio.write(Base64.decode(it, Base64.DEFAULT)) }
                                }
                            }
                            if (content.get("turnComplete")?.asBoolean == true) {
                                webSocket.close(1000, "done")
                                if (cont.isActive) cont.resume(true)
                            }
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            if (code != 1000) failure = reason.ifBlank { "closed ($code)" }
                            webSocket.close(1000, null)
                            if (cont.isActive) cont.resume(code == 1000)
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            failure = t.message ?: "connection failed"
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                )
                cont.invokeOnCancellation { ws.cancel() }
            }
        }
        if (result != true) throw PreviewException(failure ?: "the sample took too long")
        return synchronized(audio) { audio.toByteArray() }
    }

    private fun setup(model: String, voice: String, direction: String) = JsonObject().apply {
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
                    add(JsonObject().apply {
                        addProperty(
                            "text",
                            "You are a voice actor recording a short voice sample. When you get a line, speak EXACTLY that " +
                                    "line, word for word, in its own language, and nothing else — no greeting, no comment. " +
                                    "Delivery: $direction."
                        )
                    })
                })
            })
        })
    }

    // ===== Text-to-speech model (backup) =====

    /** The newest text-to-speech model this key can use (names change over time). */
    private fun model(key: String): String {
        ttsModel?.let { return it }
        val found = runCatching {
            val req = Request.Builder().url("$BASE/models?pageSize=1000").header("x-goog-api-key", key).get().build()
            AIProviders.http.newCall(req).execute().use { r ->
                JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonArray("models")
                    ?.map { it.asJsonObject.get("name").asString.removePrefix("models/") }
                    ?.filter { it.contains("tts") && it.contains("flash") }
                    ?.maxOrNull()
            }
        }.getOrNull()
        return (found ?: FALLBACK_TTS_MODEL).also { ttsModel = it }
    }

    private fun viaTts(key: String, voice: String, prompt: String): ByteArray {
        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", prompt) }) })
                })
            })
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply { add("AUDIO") })
                add("speechConfig", JsonObject().apply {
                    add("voiceConfig", JsonObject().apply {
                        add("prebuiltVoiceConfig", JsonObject().apply { addProperty("voiceName", voice) })
                    })
                })
            })
        }
        val req = Request.Builder()
            .url("$BASE/models/${model(key)}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()
        AIProviders.http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            if (!r.isSuccessful) {
                if (r.code == 404) ttsModel = null
                throw PreviewException(json?.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${r.code}")
            }
            val data = json?.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                ?.firstNotNullOfOrNull { it.asJsonObject.getAsJsonObject("inlineData")?.get("data")?.asString }
                ?: throw PreviewException("no audio came back")
            return Base64.decode(data, Base64.DEFAULT)
        }
    }

    // ===== Playback =====

    private suspend fun playPcm(pcm: ByteArray) {
        stop()
        // Faiza's own conversation mustn't hear the sample and answer it.
        FloatingOrbService.instance?.setMicMuted(true)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(2))
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
        // Un-mute shortly after the sample ends (a new sample or closing the dialog also un-mutes).
        val durationMs = pcm.size * 1000L / (RATE * 2)
        delay(durationMs + 400)
        if (track === t) FloatingOrbService.instance?.setMicMuted(false)
    }
}
