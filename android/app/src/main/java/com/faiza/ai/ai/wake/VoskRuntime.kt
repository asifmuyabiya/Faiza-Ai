package com.faiza.ai.ai.wake

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.SpeakerModel

/**
 * Loads each Vosk model once and shares it (the wake word engine and Voice Guardian both use the
 * speech model; loading it twice would waste ~100 MB of RAM). Call from a background thread.
 */
object VoskRuntime {

    private val lock = Any()
    private var speech: Model? = null
    private var speaker: SpeakerModel? = null
    @Volatile private var logConfigured = false

    /** @throws java.io.IOException if the model files are broken (caller should re-download). */
    fun speechModel(ctx: Context): Model = synchronized(lock) {
        configureLog()
        speech ?: Model(WakeModel.dir(ctx).absolutePath).also { speech = it }
    }

    /** @throws java.io.IOException if the model files are broken (caller should re-download). */
    fun speakerModel(ctx: Context): SpeakerModel = synchronized(lock) {
        configureLog()
        speaker ?: SpeakerModel(SpeakerModelFiles.dir(ctx).absolutePath).also { speaker = it }
    }

    /** Drops a cached model (before its files are deleted / re-downloaded). */
    fun forget(modelName: String) = synchronized(lock) {
        if (modelName == WakeModel.name) {
            runCatching { speech?.close() }
            speech = null
        }
        if (modelName == SpeakerModelFiles.name) {
            runCatching { speaker?.close() }
            speaker = null
        }
    }

    private fun configureLog() {
        if (!logConfigured) {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            logConfigured = true
        }
    }
}
