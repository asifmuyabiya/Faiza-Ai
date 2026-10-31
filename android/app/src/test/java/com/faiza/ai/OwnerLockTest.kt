package com.faiza.ai

import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.guardian.OwnerLock
import com.faiza.ai.ai.live.MicBoost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerLockTest {

    private val code = "Mavia shashwata pochonder"

    @Test
    fun codeMatchesAcrossScriptsAndSpelling() {
        assertTrue(OwnerLock.matches("mavia shashwata pochonder", code))
        assertTrue(OwnerLock.matches("Faiza, Mavia Shashwata Pochonder!", code))
        assertTrue(OwnerLock.matches("মাভিয়া শাশ্বত পছন্দের", code))
        assertTrue(OwnerLock.matches("माविया शाश्वत पछोंदेर", code))
        assertTrue(OwnerLock.matches("mabia sasbata posonder", code)) // sloppy transcription
        assertFalse(OwnerLock.matches("open youtube", code))
        assertFalse(OwnerLock.matches("মাভিয়া", code)) // only part of it
        assertFalse(OwnerLock.matches("what is the secret code", code))
        assertFalse(OwnerLock.matches("anything", "ab")) // too short to ever match
    }

    @Test
    fun skeletonMergesSimilarSounds() {
        assertEquals(OwnerLock.skeleton("shashwata"), OwnerLock.skeleton("শাশ্বত"))
        assertEquals(OwnerLock.skeleton("Mavia"), OwnerLock.skeleton("মাভিয়া"))
        assertEquals(1, OwnerLock.levenshtein("kitten", "sitten"))
    }

    @Test
    fun whenTheCodeIsNeeded() {
        val now = 10_000_000L
        assertFalse(OwnerLock.needsCode(false, code, OwnerLock.ASK_UNLOCK, 0, 0, 0))
        assertFalse(OwnerLock.needsCode(true, "", OwnerLock.ASK_UNLOCK, 0, 0, 0))
        assertTrue(OwnerLock.needsCode(true, code, OwnerLock.ASK_UNLOCK, 0, 0, 0))
        // Verified, then the screen went off → ask again.
        assertTrue(OwnerLock.needsCode(true, code, OwnerLock.ASK_UNLOCK, now - 100, now, 0))
        assertFalse(OwnerLock.needsCode(true, code, OwnerLock.ASK_UNLOCK, now, now - 100, 0))
        // Daily: only yesterday's verification expires.
        assertFalse(OwnerLock.needsCode(true, code, OwnerLock.ASK_DAILY, now, now + 5, now - 1000))
        assertTrue(OwnerLock.needsCode(true, code, OwnerLock.ASK_DAILY, now - 2000, 0, now - 1000))
    }

    @Test
    fun strikesAndLines() {
        assertEquals(1, OwnerLock.nextStrike(0, 0, 1000))
        assertEquals(2, OwnerLock.nextStrike(1, 1000, 5000))
        assertEquals(1, OwnerLock.nextStrike(1, 1000, 1000 + OwnerLock.STRIKE_WINDOW_MS + 1))
        PhraseBase.values().forEach { b ->
            assertTrue(OwnerLock.askLine(b).isNotBlank())
            assertTrue(OwnerLock.lockLine(b, true).length > OwnerLock.lockLine(b, false).length)
        }
        val prompt = OwnerLock.guardPrompt("Faiza", "Asif", "Bangla")
        assertFalse(prompt.contains("Mavia"))
    }

    @Test
    fun micBoostLiftsSpeechNotSilence() {
        fun pcm(vararg v: Int) = ByteArray(v.size * 2).also { b ->
            v.forEachIndexed { i, s -> b[2 * i] = (s and 0xFF).toByte(); b[2 * i + 1] = ((s shr 8) and 0xFF).toByte() }
        }
        fun read(b: ByteArray, i: Int) = (b[2 * i].toInt() and 0xFF) or (b[2 * i + 1].toInt() shl 8)
        val quiet = pcm(20, -20, 30, -30)
        assertTrue(MicBoost.apply(quiet, quiet.size, 3f).contentEquals(quiet))
        val soft = pcm(1000, -1000, 1500, -1500)
        val out = MicBoost.apply(soft, soft.size, 2f)
        assertEquals(2000, read(out, 0))
        assertEquals(-3000, read(out, 3))
        val loud = pcm(30000, -30000)
        val lim = MicBoost.apply(loud, loud.size, 3f)
        assertTrue(read(lim, 0) in 23001..32767)
        assertTrue(read(lim, 1) in -32767..-23001)
        assertEquals(1f, MicBoost.gainFor(0))
    }
}
