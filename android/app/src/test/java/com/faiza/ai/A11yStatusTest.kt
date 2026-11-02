package com.faiza.ai

import com.faiza.ai.service.A11yStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner: "Accessibility চালু করার পরেও বলে স্ক্রিন দেখতে পাচ্ছি না, চালু করো" — the answer must match the real state. */
class A11yStatusTest {
    @Test fun onButStoppedNeverSaysTurnItOn() {
        val m = A11yStatus.message(A11yStatus.State.ON_NOT_RUNNING)
        assertTrue(m.contains("IS switched on"))
        assertTrue(m.contains("OFF once and then ON"))
        assertTrue(m.contains("Do NOT tell the user to turn it on"))
    }

    @Test fun offAsksToTurnOn() {
        val m = A11yStatus.message(A11yStatus.State.OFF)
        assertTrue(m.contains("switched OFF"))
        assertFalse(m.contains("OFF once"))
    }

    @Test fun runningIsOk() = assertEquals("OK", A11yStatus.message(A11yStatus.State.RUNNING))
}
