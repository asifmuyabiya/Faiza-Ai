package com.faiza.ai.ai.guardian

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.faiza.ai.ai.wake.VoskRuntime
import kotlinx.coroutines.delay
import org.vosk.Recognizer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Live Voice Guardian: listens to the same microphone audio Faiza hears, cuts it into utterances
 * and checks each one's voice print against the allowed voices. Runs fully on the phone.
 */
class SpeakerVerifier(
    private val context: Context,
    private val allowed: List<VoiceProfile>,
    private val thresholdPercent: Int,
    private val onVerdict: (Verdict) -> Unit
) {
    data class Verdict(
        /** True = an allowed voice (or too short to judge). */
        val allowed: Boolean,
        val reliable: Boolean,
        val bestName: String?,
        val percent: Int,
        val at: Long = SystemClock.elapsedRealtime()
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: Recognizer? = null

    @Volatile private var closed = false
    @Volatile var lastVerdict: Verdict? = null
        private set

    /** Feed microphone audio (16 kHz mono PCM16). Cheap: copies and hands off to a worker thread. */
    fun feed(data: ByteArray, length: Int) {
        if (closed || allowed.isEmpty()) return
        val copy = data.copyOf(minOf(length, data.size))
        try {
            worker.execute { process(copy) }
        } catch (_: RejectedExecutionException) {
        }
    }

    /** Waits (up to [timeoutMs]) for a verdict on speech that started after [sinceElapsed]. */
    suspend fun awaitVerdictAfter(sinceElapsed: Long, timeoutMs: Long): Verdict? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val v = lastVerdict
            if (v != null && v.at >= sinceElapsed) return v
            delay(100)
        }
        return lastVerdict?.takeIf { it.at >= sinceElapsed }
    }

    fun close() {
        closed = true
        try {
            worker.execute {
                runCatching { recognizer?.close() }
                recognizer = null
            }
        } catch (_: RejectedExecutionException) {
        }
        worker.shutdown()
    }

    private fun process(chunk: ByteArray) {
        if (closed) return
        val rec = recognizer ?: runCatching {
            Recognizer(VoskRuntime.speechModel(context), VoicePrint.SAMPLE_RATE, VoskRuntime.speakerModel(context))
        }.getOrNull()?.also { recognizer = it } ?: return
        if (!rec.acceptWaveForm(chunk, chunk.size)) return
        val print = VoicePrint.parse(rec.result) ?: return
        val verdict = judge(print)
        lastVerdict = verdict
        main.post { if (!closed) onVerdict(verdict) }
    }

    private fun judge(print: VoicePrint.Print): Verdict {
        if (print.frames < VoicePrint.MIN_RELIABLE_FRAMES) {
            // Too short to judge fairly ("ok", "yes"): never block the owner on a guess.
            return Verdict(allowed = true, reliable = false, bestName = null, percent = 0)
        }
        val best = VoicePrint.bestMatch(VoicePrint.normalize(print.vector), allowed)
        val ok = best != null && best.percent >= thresholdPercent
        return Verdict(allowed = ok, reliable = true, bestName = best?.profile?.name, percent = best?.percent ?: 0)
    }
}
