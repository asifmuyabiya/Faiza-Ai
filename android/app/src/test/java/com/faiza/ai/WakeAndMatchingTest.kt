package com.faiza.ai

import com.faiza.ai.ai.Phrases
import com.faiza.ai.ai.skills.ContactFinder
import com.faiza.ai.service.FaizaAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeAndMatchingTest {

    @Test
    fun wakeWordAccepted() {
        listOf(
            "hey maya", "hi maya", "ai hi maya", "hey maia", "okay maya", "hello faiza",
            "hey faiza", "hi fiza", "maya", "Hey Maya!", "হে মায়া", "हाय माया", "हे फ़ाइज़ा",
            // Offline recogniser near-misses of the name
            "hey faisa", "hey feza", "hey phaiza", "faiza", "hey moya", "hey maiya"
        ).forEach { assertTrue("should wake: $it", Phrases.isWakeWord(it)) }
    }

    @Test
    fun ownerWakePhrasesAccepted() {
        listOf(
            "wake up faiza", "Wake up Faiza!", "hi faiza", "hey faiza", "online faiza", "faiza online",
            "faiza wake up", "suno faiza", "oi faiza", "হাই ফাইজা", "অনলাইন ফাইজা", "ওয়েক আপ ফাইজা"
        ).forEach { assertTrue("should wake: $it", Phrases.isWakeWord(it)) }
        val names = listOf("Jarvis")
        assertTrue(Phrases.isWakeWord("wake up jarvis", names))
        assertTrue(Phrases.isWakeWord("jarvis online", names))
    }

    @Test
    fun wakeWordsInsideSentencesIgnored() {
        listOf(
            "i will wake up early tomorrow", "the server is online now", "turn on the light",
            "my sister faiza went online yesterday evening"
        ).forEach { assertFalse("should NOT wake: $it", Phrases.isWakeWord(it)) }
    }

    @Test
    fun ordinaryTalkIgnored() {
        listOf(
            "", "what time is it", "the maya calendar was very old and interesting",
            "i told my friend maya about the movie yesterday", "hey there",
            // Common words that must NOT trigger the fuzzy name match
            "faster", "famous", "visa", "mama", "matter", "master", "this is a test"
        ).forEach { assertFalse("should NOT wake: $it", Phrases.isWakeWord(it)) }
    }

    @Test
    fun customAssistantNameWakes() {
        val names = listOf("Jarvis")
        assertTrue(Phrases.isWakeWord("hey jarvis", names))
        assertTrue(Phrases.isWakeWord("Jarvis", names))
        assertFalse(Phrases.isWakeWord("i watched a movie about jarvis and tony yesterday", names))
        assertFalse(Phrases.isWakeWord("hey jarvis", emptyList()))
    }

    @Test
    fun normalizeKeepsBengaliMarks() {
        assertEquals("চাঁদ", FaizaAccessibilityService.normalize("চাঁদ"))
        assertEquals("chandmia", FaizaAccessibilityService.normalize("Chand Mia!"))
    }

    @Test
    fun contactQueriesDeduplicated() {
        assertEquals(
            listOf("চাঁদ", "Chand", "Chaand"),
            ContactFinder.queries("চাঁদ", "Chand, chand, Chaand, চাঁদ")
        )
    }

    @Test
    fun numbersDetected() {
        assertTrue(ContactFinder.looksLikeNumber("+880 1712-345678"))
        assertFalse(ContactFinder.looksLikeNumber("Chand"))
    }
}
