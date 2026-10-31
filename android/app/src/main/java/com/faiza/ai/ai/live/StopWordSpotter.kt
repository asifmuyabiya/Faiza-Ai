package com.faiza.ai.ai.live

import android.content.Context
import com.faiza.ai.ai.wake.VoskRuntime
import com.faiza.ai.ai.wake.WakeModel
import com.google.gson.JsonParser
import org.vosk.Recognizer

/**
 * "Stop!" while Faiza talks, heard ON THE PHONE (owner: "ওর কথার মাঝখান দিয়ে কথা বললে থেমে আমার কথা শুনবে").
 * Some phones (Honor) bury the user's voice under her loudspeaker, so the AI never hears it. This small offline
 * recogniser listens only for a few stop words ("stop", "wait", "Faiza", "listen"…) in the same microphone audio,
 * with a confidence check, and ignores words she herself just said.
 */
class StopWordSpotter private constructor(private val recognizer: Recognizer) {

    companion object {
        /** Words the offline (Indian English) model knows; Bangla speakers say these too ("ওয়েট", "স্টপ"). */
        private const val GRAMMAR =
            "[\"stop\", \"wait\", \"listen\", \"hold on\", \"one minute\", \"faiza\", \"fiza\", \"hey faiza\", \"okay stop\", \"[unk]\"]"

        val STOP_WORDS = setOf("stop", "wait", "listen", "hold", "minute", "faiza", "fiza")
        const val MIN_CONF = 0.72

        /** Null when the offline model isn't downloaded (then only the AI's own detection and taps work). */
        fun create(ctx: Context): StopWordSpotter? = runCatching {
            if (!WakeModel.isReady(ctx)) return null
            val r = Recognizer(VoskRuntime.speechModel(ctx), 16_000f, GRAMMAR)
            r.setWords(true)
            StopWordSpotter(r)
        }.getOrNull()

        /**
         * Pure check of one Vosk result (tested): a stop word heard with enough confidence that she herself did not
         * just say (her words come from her transcript, e.g. "faiza", "ফাইজা", "stop").
         */
        fun isStop(resultJson: String, herRecentWords: Set<String>): Boolean = runCatching {
            val o = JsonParser.parseString(resultJson).asJsonObject
            val words = o.getAsJsonArray("result") ?: return false
            words.any { el ->
                val w = el.asJsonObject
                val word = w.get("word")?.asString?.lowercase().orEmpty()
                val conf = w.get("conf")?.asDouble ?: 0.0
                word in STOP_WORDS && conf >= MIN_CONF && !saidByHer(word, herRecentWords)
            }
        }.getOrDefault(false)

        private fun saidByHer(word: String, her: Set<String>): Boolean = when (word) {
            "faiza", "fiza" -> her.any { it == "faiza" || it == "ফাইজা" || it == "fiza" }
            "stop" -> her.any { it == "stop" || it == "স্টপ" }
            "wait" -> her.any { it == "wait" || it == "ওয়েট" || it == "ওয়েট" }
            "listen" -> her.any { it == "listen" || it == "লিসেন" }
            else -> her.contains(word)
        }
    }

    /** Feed 100 ms of mic audio; true = the user said a stop word. */
    fun feed(pcm: ByteArray, len: Int, herRecentWords: Set<String>): Boolean {
        val done = runCatching { recognizer.acceptWaveForm(pcm, len) }.getOrDefault(false)
        if (!done) return false
        return isStop(recognizer.result, herRecentWords)
    }

    fun reset() = runCatching { recognizer.reset() }

    fun close() = runCatching { recognizer.close() }
}
