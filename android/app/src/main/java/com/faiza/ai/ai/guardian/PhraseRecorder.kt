package com.faiza.ai.ai.guardian

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** Records one short phrase (16 kHz mono PCM16) for voice enrolment / testing. */
class PhraseRecorder(private val onAutoStop: () -> Unit) {

    companion object {
        const val MIN_MS = 2_500L
        const val MAX_MS = 5_500L
        private const val RATE = 16_000
        /** Below this loudness there's no real voice in the recording. */
        private const val MIN_SPEECH_RMS = 250.0
    }

    sealed class Result {
        class Ok(val pcm: ByteArray, val durationMs: Long) : Result()
        class TooShort(val durationMs: Long) : Result()
        object TooQuiet : Result()
        object Failed : Result()
    }

    private val main = Handler(Looper.getMainLooper())
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val buffer = ByteArrayOutputStream()
    @Volatile private var running = false

    val isRecording get() = running

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 6_400)
            )
        } catch (_: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        synchronized(buffer) { buffer.reset() }
        record = r
        running = true
        r.startRecording()
        thread = Thread {
            val chunk = ByteArray(3_200)
            val maxBytes = (MAX_MS * RATE * 2 / 1000).toInt()
            while (running) {
                val n = r.read(chunk, 0, chunk.size)
                if (n > 0) {
                    val size = synchronized(buffer) {
                        buffer.write(chunk, 0, n)
                        buffer.size()
                    }
                    if (size >= maxBytes) {
                        main.post { if (running) onAutoStop() }
                        break
                    }
                }
            }
        }.also { it.start() }
        return true
    }

    fun stop(): Result {
        if (!running && record == null) return Result.Failed
        running = false
        thread?.join(500)
        thread = null
        record?.runCatching { stop(); release() }
        record = null
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        val duration = pcm.size * 1000L / (RATE * 2)
        return when {
            pcm.isEmpty() -> Result.Failed
            duration < MIN_MS -> Result.TooShort(duration)
            rms(pcm) < MIN_SPEECH_RMS -> Result.TooQuiet
            else -> Result.Ok(pcm, duration)
        }
    }

    fun cancel() {
        running = false
        thread?.join(300)
        thread = null
        record?.runCatching { stop(); release() }
        record = null
    }

    private fun rms(pcm: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < pcm.size) {
            val s = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            sum += s.toDouble() * s
            i += 2
        }
        return sqrt(sum / maxOf(1, pcm.size / 2))
    }
}
