package com.faiza.ai.ai.live

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lifts soft speech before it goes to Gemini (owner: "আস্তে বললে শোনে না"). Near-silence is left alone so room
 * hiss isn't turned into "speech", and loud peaks are softly limited instead of clipping. Pure, so it is unit-tested.
 */
object MicBoost {

    /** Chunks quieter than this (RMS of 16-bit PCM) are background noise: not boosted. */
    const val NOISE_FLOOR = 90.0

    /** Settings choice → gain. 0 = off, 1 = soft voices, 2 = whispers. */
    fun gainFor(level: Int): Float = when (level) {
        1 -> 2.0f
        2 -> 3.2f
        else -> 1f
    }

    fun apply(pcm: ByteArray, length: Int, gain: Float): ByteArray {
        val out = pcm.copyOf(length)
        val samples = length / 2
        if (samples == 0 || gain <= 1f) return out
        var sum = 0.0
        for (i in 0 until samples) {
            val v = sample(out, i)
            sum += v.toDouble() * v
        }
        if (sqrt(sum / samples) < NOISE_FLOOR) return out
        for (i in 0 until samples) {
            val boosted = sample(out, i) * gain
            val limited = limit(boosted)
            val s = limited.toInt()
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Linear up to ~70% of full scale, then bends smoothly towards 32767 (no harsh clipping). */
    internal fun limit(x: Float): Float {
        val knee = 23_000f
        val max = 32_767f
        val a = abs(x)
        if (a <= knee) return x
        val over = a - knee
        val room = max - knee
        val y = knee + room * (over / (over + room))
        return if (x < 0) -y else y
    }

    private fun sample(b: ByteArray, i: Int): Int = (b[2 * i].toInt() and 0xFF) or (b[2 * i + 1].toInt() shl 8)
}
