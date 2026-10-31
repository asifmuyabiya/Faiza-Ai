package com.faiza.ai

import com.faiza.ai.ai.voice.VoiceNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNotesTest {

    @Test fun detectsVoiceMessageNotifications() {
        assertTrue(VoiceNotes.isVoiceText("🎤 Voice message (0:12)"))
        assertTrue(VoiceNotes.isVoiceText("ভয়েস মেসেজ (০:৪৫)"))
        assertTrue(VoiceNotes.isVoiceText("Voice message"))
        assertFalse(VoiceNotes.isVoiceText("কেমন আছো?"))
    }

    @Test fun senderNames() {
        assertEquals("Ammu", VoiceNotes.cleanSender("Family Group: Ammu"))
        assertEquals("Karim", VoiceNotes.cleanSender("Karim (3 messages)"))
        assertEquals("আম্মু", VoiceNotes.cleanSender("আম্মু"))
    }

    @Test fun matchesFilesToClosestNotification() {
        val t = 1_000_000_000L
        val files = listOf("a.opus" to t + 5_000, "b.opus" to t + 600_000, "c.opus" to t - 400_000)
        val notes = listOf(VoiceNotes.Heard("Ammu", "WhatsApp", t), VoiceNotes.Heard("Karim", "WhatsApp", t + 590_000))
        val m = VoiceNotes.match(files, notes)
        assertEquals("Ammu", m["a.opus"]?.sender)
        assertEquals("Karim", m["b.opus"]?.sender)
        assertEquals(null, m["c.opus"]) // too far from any notification
    }

    @Test fun oneFilePerNotification() {
        val t = 5_000_000L
        val files = listOf("x.opus" to t + 1_000, "y.opus" to t + 2_000)
        val m = VoiceNotes.match(files, listOf(VoiceNotes.Heard("A", "WhatsApp", t)))
        assertEquals(1, m.size)
        assertEquals("A", m["x.opus"]?.sender)
    }
}
