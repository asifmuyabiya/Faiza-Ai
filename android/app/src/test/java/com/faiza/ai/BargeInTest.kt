package com.faiza.ai

import com.faiza.ai.ai.live.BargeInDetector
import com.faiza.ai.ai.live.BargeInDetector.Action
import com.faiza.ai.ai.live.EchoText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

/** Talking over Faiza: she must stop for the user (even softly) but never for her own echo. */
class BargeInTest {

    private val rnd = Random(42)

    /** Her voice level moves between soft and loud syllables, like real speech. */
    private fun playing(i: Int) = 1500.0 + 2500.0 * ((i % 7) / 6.0)

    /** Echo left after the phone's echo canceller: ~4% of her level, jittering ±40%. */
    private fun echo(p: Double) = p * 0.04 * (0.6 + 0.8 * rnd.nextDouble())

    private fun mix(a: Double, b: Double) = sqrt(a * a + b * b)

    /** Warm up like a real session: some room noise, then a few seconds of her talking. */
    private fun warmedUp(): Pair<BargeInDetector, Long> {
        val d = BargeInDetector(hardwareEchoCanceller = true)
        repeat(50) { d.onQuietChunk(60.0 + rnd.nextDouble() * 20) }
        var t = 0L
        repeat(60) { i ->
            val p = playing(i)
            assertEquals("echo must never be sent", Action.HOLD, d.onEchoChunk(echo(p), p, t))
            t += 100
        }
        return d to t
    }

    @Test fun ownEchoNeverInterrupts() {
        val (d, start) = warmedUp()
        var t = start
        repeat(600) { i -> // one minute of her talking
            val p = playing(i)
            assertEquals(Action.HOLD, d.onEchoChunk(echo(p), p, t))
            t += 100
        }
    }

    @Test fun echoFromFirstWordNeverInterrupts() {
        // Brand-new session, nothing learned yet: the safe default must hold her echo back too.
        val d = BargeInDetector(hardwareEchoCanceller = true)
        var t = 0L
        repeat(100) { i ->
            val p = playing(i)
            assertEquals(Action.HOLD, d.onEchoChunk(echo(p), p, t))
            t += 100
        }
    }

    @Test fun softVoiceIsSentToGeminiQuickly() {
        val (d, start) = warmedUp()
        var t = start
        val actions = (0 until 4).map { i ->
            val p = playing(i)
            d.onEchoChunk(mix(echo(p), 650.0), p, t).also { t += 100 }
        }
        val first = actions.indexOfFirst { it == Action.FORWARD_START || it == Action.INTERRUPT }
        assertTrue("soft voice must be heard within ~300 ms, got $actions", first in 0..2)
    }

    @Test fun normalVoiceStopsHerImmediately() {
        val (d, start) = warmedUp()
        var t = start
        val actions = (0 until 3).map { i ->
            val p = playing(i)
            d.onEchoChunk(mix(echo(p), 2200.0), p, t).also { t += 100 }
        }
        assertTrue("normal voice must stop her within ~200 ms, got $actions", Action.INTERRUPT in actions)
    }

    @Test fun shortPausesBetweenWordsKeepForwarding() {
        val (d, start) = warmedUp()
        var t = start
        val p = 3000.0
        d.onEchoChunk(480.0, p, t); t += 100
        assertEquals(Action.FORWARD_START, d.onEchoChunk(480.0, p, t)); t += 100
        // 500 ms pause inside the sentence: still sending.
        repeat(5) { assertEquals(Action.FORWARD, d.onEchoChunk(echo(p), p, t)); t += 100 }
        // Long silence: stops sending again.
        repeat(5) { d.onEchoChunk(echo(p), p, t); t += 100 }
        assertEquals(Action.HOLD, d.onEchoChunk(echo(p), p, t))
    }

    @Test fun unheardSoftSpeechDoesNotMakeHerDeafer() {
        // The old bug: quiet speech below the threshold was learned as "echo", raising the threshold.
        val (d, start) = warmedUp()
        val before = d.softThreshold(3000.0)
        var t = start
        repeat(300) { i ->
            val p = playing(i)
            val level = if (i % 3 == 0) mix(echo(p), 230.0) else echo(p) // murmurs she can't hear
            d.onEchoChunk(level, p, t)
            t += 100
        }
        val after = d.softThreshold(3000.0)
        assertTrue("threshold rose from $before to $after", after <= before * 1.25)
    }

    @Test fun echoConfirmedByTranscriptMakesItStricter() {
        val (d, _) = warmedUp()
        val before = d.softThreshold(3000.0)
        d.onEchoConfirmed()
        assertTrue(d.softThreshold(3000.0) > before)
    }

    @Test fun transcriptOfHerOwnWordsIsEcho() {
        val said = EchoText.words("Sure! The weather in Dhaka today is sunny with a high of 31 degrees").toSet()
        assertFalse(EchoText.isUserSpeech(EchoText.words("weather in Dhaka today"), said))
        assertTrue(EchoText.isUserSpeech(EchoText.words("wait stop"), said))
        assertTrue(EchoText.isUserSpeech(EchoText.words("one minute Faiza"), said))
    }

    @Test fun banglaInterruptionIsRecognised() {
        val said = EchoText.words("আজ ঢাকার আবহাওয়া রৌদ্রোজ্জ্বল থাকবে").toSet()
        assertTrue(EchoText.isUserSpeech(EchoText.words("থামো শোনো"), said))
        assertFalse(EchoText.isUserSpeech(EchoText.words("ঢাকার আবহাওয়া"), said))
    }

    @Test fun tinyFragmentsAreIgnored() {
        assertFalse(EchoText.isUserSpeech(EchoText.words("a"), emptySet()))
        assertFalse(EchoText.isUserSpeech(emptyList(), emptySet()))
    }

    @Test
    fun callModeHearsTheQuieterVoice() {
        // On the call loudspeaker the phone's processing leaves the user's voice at ~100 RMS while she talks.
        val normal = com.faiza.ai.ai.live.BargeInDetector(hardwareEchoCanceller = true)
        val call = com.faiza.ai.ai.live.BargeInDetector(hardwareEchoCanceller = true, callMode = true)
        repeat(80) { normal.onQuietChunk(10.0); call.onQuietChunk(10.0) }
        val playing = 300.0
        org.junit.Assert.assertTrue(normal.softThreshold(playing) > 110.0)
        org.junit.Assert.assertTrue(call.softThreshold(playing) < 110.0)
        org.junit.Assert.assertTrue(call.loudThreshold(playing) < normal.loudThreshold(playing))
    }
}
