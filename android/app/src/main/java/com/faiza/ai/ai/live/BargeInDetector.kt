package com.faiza.ai.ai.live

import kotlin.math.max

/**
 * Decides, while Faiza is talking, whether the microphone hears the user or just Faiza's own echo.
 *
 * Two levels:
 *  - "Maybe the user" (soft voice above the echo): the audio is forwarded to Gemini, which hears it,
 *    transcribes it and stops her if it really is speech — so even a quiet "wait" works.
 *  - "Clearly the user" (well above any echo): Faiza is silenced on the phone immediately.
 *
 * The echo level is learned from moments that are certainly only echo, so the user's own unheard
 * speech can never push the threshold up (that feedback loop made her deaf to interruptions).
 * Pure logic, no Android, so it is unit-tested.
 */
class BargeInDetector(hardwareEchoCanceller: Boolean, callMode: Boolean = false) {

    enum class Action {
        /** Probably echo: keep it as pre-roll, don't send. */
        HOLD,
        /** The user may be starting to talk: send the pre-roll and this chunk to Gemini. */
        FORWARD_START,
        /** Still (maybe) talking: keep sending. */
        FORWARD,
        /** Clearly the user: silence Faiza now and send everything. */
        INTERRUPT
    }

    companion object {
        const val MIN_SOFT_RMS = 150.0        // quietest level we ever treat as possible speech
        const val MIN_LOUD_RMS = 380.0        // quietest level for an instant local interruption
        const val SOFT_ECHO_MARGIN = 1.6     // "maybe user" = this many times the worst expected echo
        const val LOUD_ECHO_MARGIN = 3.0      // "clearly user"
        const val SOFT_CHUNKS = 2             // ~200 ms
        const val LOUD_CHUNKS = 2             // ~200 ms
        const val HANGOVER_MS = 700L          // keep forwarding through short pauses between words
        const val LEARN_MIN_PLAYING = 300.0
        private const val WINDOW = 80         // ~8 s of echo samples
        private const val MIN_SAMPLES = 8
    }

    private val defaultRatio = if (callMode && hardwareEchoCanceller) 0.03 else if (hardwareEchoCanceller) 0.12 else 0.35

    /**
     * Call loudspeaker mode: the phone's call processing (echo canceller + noise suppression) makes the user's voice
     * much quieter while Faiza talks, so the absolute floors must be lower or she never hears them.
     */
    private val minSoft = if (callMode) 70.0 else MIN_SOFT_RMS
    private val minLoud = if (callMode) 220.0 else MIN_LOUD_RMS
    private val ratios = ArrayDeque<Double>()
    /** Raised when Gemini's transcript proves forwarded audio was only Faiza's echo. */
    private var ratioBoost = 1.0

    @Volatile var noiseFloor = 100.0
        private set

    private var softCount = 0
    private var loudCount = 0
    private var forwarding = false
    private var lastSoftAt = 0L

    /** Worst-case echo-to-speaker ratio (90th percentile of recent echo-only moments). */
    @Synchronized
    fun echoRatio(): Double {
        val base = if (ratios.size < MIN_SAMPLES) defaultRatio
        else ratios.sorted()[((ratios.size - 1) * 0.9).toInt()]
        return (base * ratioBoost).coerceIn(0.005, 1.5)
    }

    /** Usual echo-to-speaker ratio (median), including any boost from confirmed echo. */
    @Synchronized
    private fun typicalRatio(): Double {
        val base = if (ratios.size < MIN_SAMPLES) defaultRatio else ratios.sorted()[(ratios.size - 1) / 2]
        return (base * ratioBoost).coerceIn(0.005, 1.5)
    }

    fun softThreshold(playing: Double): Double =
        max(minSoft, max(noiseFloor * 2.5 + 40.0, echoRatio() * playing * SOFT_ECHO_MARGIN + 60.0))

    fun loudThreshold(playing: Double): Double =
        max(minLoud, max(echoRatio() * playing * LOUD_ECHO_MARGIN + 150.0, softThreshold(playing) * 1.6))

    /** Faiza is silent: track the room's background noise (follows quiet moments, ignores speech). */
    @Synchronized
    fun onQuietChunk(level: Double) {
        noiseFloor = if (level < noiseFloor) noiseFloor * 0.9 + level * 0.1
        else noiseFloor * 0.998 + level * 0.002
        noiseFloor = noiseFloor.coerceIn(10.0, 2_000.0)
        resetTurn()
    }

    /** One ~100 ms microphone chunk while Faiza's voice may be coming out of the speaker. */
    @Synchronized
    fun onEchoChunk(level: Double, playing: Double, now: Long): Action {
        val soft = softThreshold(playing)
        val loud = loudThreshold(playing)

        loudCount = if (level > loud) loudCount + 1 else 0
        if (level > soft) {
            softCount++
            lastSoftAt = now
        } else softCount = 0

        if (loudCount >= LOUD_CHUNKS) {
            resetTurn()
            return Action.INTERRUPT
        }
        if (forwarding) {
            if (now - lastSoftAt <= HANGOVER_MS) return Action.FORWARD
            forwarding = false
        }
        if (softCount >= SOFT_CHUNKS) {
            forwarding = true
            return Action.FORWARD_START
        }
        // Certainly echo (close to what we expect): learn from it. Anything between the expected echo
        // and the soft threshold might be a quiet user, so it is never learned.
        // Judged against the TYPICAL echo (median), not the worst case, so quiet speech can't creep in.
        if (playing > LEARN_MIN_PLAYING && softCount == 0) {
            if (level <= typicalRatio() * playing * 1.6 + 20.0) {
                ratios.addLast((level / playing).coerceIn(0.002, 1.5))
                while (ratios.size > WINDOW) ratios.removeFirst()
                // Echo is lower than we feared: let the boost relax again.
                if (ratioBoost > 1.0) ratioBoost = max(1.0, ratioBoost * 0.995)
            }
        }
        return Action.HOLD
    }

    /** Gemini transcribed forwarded audio as Faiza's own words: echo is stronger than we thought. */
    @Synchronized
    fun onEchoConfirmed() {
        ratioBoost = (ratioBoost * 1.35).coerceAtMost(6.0)
        resetTurn()
    }

    @Synchronized
    fun resetTurn() {
        softCount = 0
        loudCount = 0
        forwarding = false
    }
}

/** Is a (partial) transcript of the mic just Faiza's own recent words coming back? */
object EchoText {
    private val SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun words(text: String): List<String> =
        text.lowercase().split(SPLIT).filter { it.isNotEmpty() }

    /** Share of [heard] words that Faiza herself just said (0.0–1.0). */
    fun echoShare(heard: List<String>, recentOutput: Set<String>): Double {
        if (heard.isEmpty()) return 1.0
        return heard.count { it in recentOutput }.toDouble() / heard.size
    }

    /** Everything the mic "heard" was Faiza's own recent words: an echo, not the user. */
    fun isEchoTurn(heard: List<String>, recentOutput: Set<String>, minWords: Int = 3): Boolean =
        heard.size >= minWords && echoShare(heard, recentOutput) >= 0.75

    /** True when the mic transcript is the user talking over Faiza, not her echo. */
    fun isUserSpeech(heard: List<String>, recentOutput: Set<String>): Boolean {
        val letters = heard.sumOf { it.length }
        return heard.isNotEmpty() && letters >= 3 && echoShare(heard, recentOutput) < 0.5
    }

    /** Bangla stop stems, matched inside a word ("থামো", "থামেন", "থামুন", "চুপ কর", "দাঁড়াও", "স্টপ", "ওয়েট"…). */
    private val STOP_STEMS = listOf("থাম", "চুপ", "দাঁড়া", "দাড়া", "স্টপ", "ওয়েট", "ওয়েট", "ওয়েইট")
        .map { nfc(it) }

    /** Whole words in English / Banglish. */
    private val STOP_WORDS = setOf("stop", "wait", "enough", "shut", "thamo", "thamen", "thamun", "tham", "chup", "ruko")

    private fun nfc(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)

    private fun isStopWord(word: String): Boolean {
        val w = nfc(word)
        return w in STOP_WORDS || STOP_STEMS.any { w.contains(it) }
    }

    /**
     * Owner: "থামো থামো, এটা না" — a stop word in the mic transcript that Faiza did not just say herself.
     * Her own recent words are excluded, so reading out a text that contains "থামো" can't stop her.
     */
    fun isStopCommand(heard: List<String>, recentOutput: Set<String>): Boolean {
        val her = recentOutput.mapTo(HashSet()) { nfc(it) }
        return heard.any { isStopWord(it) && nfc(it) !in her }
    }
}
