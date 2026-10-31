package com.faiza.ai

import com.faiza.ai.ai.calls.CallAssist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallAssistTest {

    @Test fun banglaLines() {
        assertEquals("বস, আম্মু ফোন দিছে", CallAssist.announceLine(true, "বস", "আম্মু", null, "01712345678"))
        assertEquals("বস, আম্মু WhatsApp-এ কল দিছে", CallAssist.announceLine(true, "বস", "আম্মু", "WhatsApp", null))
        assertEquals("বস, একটা অচেনা নাম্বার থেকে কল আসছে", CallAssist.announceLine(true, "বস", null, null, null))
        assertEquals("বস, imo-এ একটা কল আসছে", CallAssist.announceLine(true, "বস", null, "imo", null))
        assertEquals("বস, 0171 2345 678 নাম্বার থেকে কল আসছে", CallAssist.announceLine(true, "বস", null, null, "01712345678"))
    }

    @Test fun englishLines() {
        assertEquals("Boss, Mom is calling", CallAssist.announceLine(false, "Boss", "Mom", null, null))
        assertEquals("Boss, Mom is calling on WhatsApp", CallAssist.announceLine(false, "Boss", "Mom", "WhatsApp", null))
        assertEquals("Boss, a call from an unknown number", CallAssist.announceLine(false, "Boss", null, null, null))
    }

    @Test fun numbers() {
        assertTrue(CallAssist.looksLikeNumber("+880 1712-345678"))
        assertFalse(CallAssist.looksLikeNumber("Ammu"))
        assertFalse(CallAssist.looksLikeNumber("Room 12"))
        assertEquals("+880 1712 3456 78", CallAssist.spaced("+880 1712-345678"))
    }

    @Test fun nothingRingingByDefault() {
        CallAssist.ended()
        assertEquals(null, CallAssist.ringing)
    }
}
