package com.faiza.ai

import com.faiza.ai.ai.guardian.VoicePrint
import com.faiza.ai.ai.guardian.VoiceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianMathTest {

    @Test
    fun identicalVoicesMatch100() {
        val v = floatArrayOf(0.2f, -0.5f, 0.8f, 0.1f)
        assertEquals(100, VoicePrint.percent(v, v))
    }

    @Test
    fun oppositeOrUnrelatedVoicesScoreLow() {
        assertEquals(0, VoicePrint.percent(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)))
        assertEquals(0, VoicePrint.percent(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)))
    }

    @Test
    fun mismatchedOrEmptyVectorsAreSafe() {
        assertEquals(0, VoicePrint.percent(floatArrayOf(1f, 2f), floatArrayOf(1f)))
        assertEquals(0, VoicePrint.percent(floatArrayOf(), floatArrayOf()))
        assertEquals(0, VoicePrint.percent(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)))
    }

    @Test
    fun combineWeightsByFramesAndNormalises() {
        val a = VoicePrint.Print(floatArrayOf(1f, 0f), 300)
        val b = VoicePrint.Print(floatArrayOf(0f, 1f), 100)
        val c = VoicePrint.combine(listOf(a, b))!!
        assertEquals(400, c.frames)
        val len = Math.sqrt((c.vector[0] * c.vector[0] + c.vector[1] * c.vector[1]).toDouble())
        assertEquals(1.0, len, 1e-4)
        assertTrue(c.vector[0] > c.vector[1]) // the longer sample counts more
        assertNull(VoicePrint.combine(emptyList()))
    }

    @Test
    fun parsesVoskSpeakerResult() {
        val p = VoicePrint.parse("""{"text":"hello","spk":[0.1,-0.2,0.3],"spk_frames":152}""")
        assertNotNull(p)
        assertEquals(152, p!!.frames)
        assertEquals(3, p.vector.size)
        assertNull(VoicePrint.parse("""{"text":"hi"}"""))
        assertNull(VoicePrint.parse("not json"))
        assertNull(VoicePrint.parse(null))
    }

    @Test
    fun bestMatchPicksClosestVoice() {
        val owner = VoiceProfile(name = "Rahim", role = VoiceProfile.ROLE_OWNER, vector = listOf(1f, 0f, 0f))
        val mom = VoiceProfile(name = "Ammu", role = VoiceProfile.ROLE_FAMILY, vector = listOf(0f, 1f, 0f))
        val m = VoicePrint.bestMatch(floatArrayOf(0.9f, 0.2f, 0f), listOf(owner, mom))!!
        assertEquals("Rahim", m.profile.name)
        assertTrue(m.percent in 90..100)
        assertNull(VoicePrint.bestMatch(floatArrayOf(1f), emptyList()))
    }
}
