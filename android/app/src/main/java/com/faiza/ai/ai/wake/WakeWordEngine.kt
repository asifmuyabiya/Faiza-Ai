package com.faiza.ai.ai.wake

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.Phrases
import com.google.gson.JsonParser
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Always-on, offline "Hey Maya / Hey Faiza" detector (like "Hey Siri").
 * Runs Vosk on the device — nothing is sent to the internet while it waits.
 */
class WakeWordEngine(
    private val context: Context,
    private val onWake: () -> Unit,
    /** Called (on the main thread) when the engine can't listen, so the caller can use a fallback. */
    private val onUnavailable: () -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var service: SpeechService? = null
    private var recognizer: Recognizer? = null

    /** The owner wants us listening (cleared by [stop]); internal restarts never override it. */
    @Volatile private var wanted = false
    @Volatile private var running = false
    @Volatile private var released = false

    /** False if the speech engine can't run on this phone; the caller then uses its fallback. */
    @Volatile var available = true
        private set

    val isListening: Boolean get() = running

    fun start() {
        wanted = true
        launch()
    }

    /** Releases the microphone (call before anything else needs it). */
    fun stop() {
        wanted = false
        halt()
    }

    fun release() {
        released = true
        stop()
        worker.shutdown()
    }

    private fun launch() {
        if (running || released || !wanted || !available || !WakeModel.isReady(context)) return
        running = true
        worker.execute {
            try {
                val recognizer = Recognizer(VoskRuntime.speechModel(context), SAMPLE_RATE)
                val speech = SpeechService(recognizer, SAMPLE_RATE)
                main.post {
                    if (!running || !wanted || released) {
                        runCatching { speech.shutdown() }
                        runCatching { recognizer.close() }
                        return@post
                    }
                    service = speech
                    this.recognizer = recognizer
                    if (!speech.startListening(listener)) {
                        halt()
                    }
                }
            } catch (e: IOException) {
                // Broken/incomplete model files (or mic busy): re-download model next time.
                running = false
                if (e.message?.contains("model", ignoreCase = true) == true) WakeModel.reset(context)
                main.post { if (wanted && !released) onUnavailable() }
            } catch (t: Throwable) {
                // Native library can't run on this phone: the caller falls back to Android's recognizer.
                running = false
                available = false
                main.post { if (wanted && !released) onUnavailable() }
            }
        }
    }

    private fun halt() {
        running = false
        service?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        service = null
        // The recognizer thread has stopped once shutdown() returns, so its native memory can go.
        recognizer?.let { r -> runCatching { r.close() } }
        recognizer = null
    }

    private fun check(json: String?, key: String) {
        if (!running || json.isNullOrBlank()) return
        val text = runCatching { JsonParser.parseString(json).asJsonObject.get(key)?.asString }.getOrNull()
        if (text.isNullOrBlank()) return
        val custom = listOfNotNull(FaizaApp.instance.prefs.assistantNameSetting.takeIf { it.isNotBlank() })
        if (Phrases.isWakeWord(text, custom)) {
            stop()
            onWake()
        }
    }

    private val listener = object : org.vosk.android.RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = check(hypothesis, "partial")
        override fun onResult(hypothesis: String?) = check(hypothesis, "text")
        override fun onFinalResult(hypothesis: String?) = check(hypothesis, "text")
        override fun onError(exception: Exception?) = restartSoon()
        override fun onTimeout() = restartSoon()
    }

    private fun restartSoon() {
        if (!running) return
        halt()
        main.postDelayed({ launch() }, 1_000)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000f
    }
}
