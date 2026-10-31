package com.faiza.ai

import com.faiza.ai.ai.Closeness
import com.faiza.ai.ai.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosenessTest {

    @Test
    fun levelsMapBothWays() {
        assertEquals(Closeness.FRIENDLY, Closeness.from(0))
        assertEquals(Closeness.ROMANTIC, Closeness.from(1))
        assertEquals(Closeness.INTENSE, Closeness.from(2))
        assertEquals(Closeness.FRIENDLY, Closeness.from(99))
    }

    @Test
    fun ageGate() {
        assertTrue(Closeness.isAdult(2000, now = 2026))
        assertTrue(Closeness.isAdult(2008, now = 2026)) // exactly 18
        assertFalse(Closeness.isAdult(2010, now = 2026))
        assertFalse(Closeness.isAdult(0, now = 2026))
        assertFalse(Closeness.isAdult(3000, now = 2026))
    }

    @Test
    fun promptsCarryHardLimitsAndNoMinors() {
        Closeness.values().forEach { c ->
            val p = Closeness.prompt(c, "Faiza")
            assertTrue(p.isNotBlank())
        }
        assertTrue(Closeness.HARD_LIMITS.contains("under 18"))
        assertTrue(Closeness.HARD_LIMITS.lowercase().contains("filter"))
        // Friendly must not flirt; Romantic/Intense are girlfriend-style.
        assertTrue(Closeness.prompt(Closeness.FRIENDLY, "Faiza").contains("No pet names"))
        assertTrue(Closeness.prompt(Closeness.ROMANTIC, "Faiza").lowercase().contains("girlfriend"))
    }

    @Test
    fun jarvisPersonaExists() {
        val j = Persona.from("jarvis")
        assertEquals("Jarvis", j.displayName)
        assertTrue(j.isPro)
    }
}
