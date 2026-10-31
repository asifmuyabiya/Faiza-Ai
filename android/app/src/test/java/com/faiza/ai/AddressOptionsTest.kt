package com.faiza.ai

import com.faiza.ai.ai.AddressOptions
import com.faiza.ai.ai.PhraseBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressOptionsTest {

    @Test
    fun listsFollowGender() {
        assertTrue("Sir" in AddressOptions.forGender("male"))
        assertFalse("Ma'am" in AddressOptions.forGender("male"))
        assertTrue("Ma'am" in AddressOptions.forGender("female"))
        assertFalse("Sir" in AddressOptions.forGender("female"))
        listOf("male", "female", "").forEach { g ->
            val l = AddressOptions.forGender(g)
            assertTrue(listOf("Boss", "Jaan", "Jaanu").all { it in l })
        }
    }

    @Test
    fun promptUsesTheChosenWordAndGender() {
        val p = AddressOptions.promptLine("Asif", "male", "Jaanu", PhraseBase.BENGALI)
        assertTrue(p.contains("জানু"))
        assertTrue(p.contains("MALE"))
        assertTrue(p.contains("Asif"))
        val f = AddressOptions.promptLine(null, "female", "Ma'am", PhraseBase.HINDI)
        assertTrue(f.contains("मैम") && f.contains("FEMALE"))
        assertEquals("", AddressOptions.promptLine("Asif", "", "", PhraseBase.ENGLISH))
    }

    @Test
    fun fixedLinesUseTheUsersWord() {
        assertEquals("জান, আপনি কি সত্যিই?", AddressOptions.personalize("বস, আপনি কি সত্যিই?", "Jaan", PhraseBase.BENGALI))
        assertEquals("Sir, careful!", AddressOptions.personalize("Boss, careful!", "Sir", PhraseBase.ENGLISH))
        assertEquals("Boss, careful!", AddressOptions.personalize("Boss, careful!", "", PhraseBase.ENGLISH))
        assertEquals("Shona, done", AddressOptions.personalize("Boss, done", "Shona", PhraseBase.ENGLISH))
    }
}
