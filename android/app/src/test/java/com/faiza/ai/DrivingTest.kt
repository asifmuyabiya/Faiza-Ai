package com.faiza.ai

import com.faiza.ai.ai.driving.Driving
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingTest {

    @Test fun messageLines() {
        assertEquals("আম্মু WhatsApp-এ লিখেছে: কখন আসবি?", Driving.messageLine(true, "আম্মু", "WhatsApp", "কখন   আসবি?"))
        val long = Driving.messageLine(false, "Karim", "SMS", "word ".repeat(100))
        assertTrue(long.endsWith("…")); assertTrue(long.length < 240)
    }

    @Test fun speedWarningNeedsFourSecondsOverAndThenWaits() {
        val w = Driving.SpeedWatch({ 80 })
        assertFalse(w.onSpeed(95.0, 0))
        assertFalse(w.onSpeed(95.0, 2_000))
        assertTrue(w.onSpeed(95.0, 4_500))
        assertFalse(w.onSpeed(95.0, 10_000)) // just warned
        assertFalse(w.onSpeed(70.0, 20_000)) // back under: resets
        assertFalse(w.onSpeed(96.0, 60_000))
        assertTrue(w.onSpeed(96.0, 65_000))
    }

    @Test fun smallOverAndOffAreIgnored() {
        assertFalse(Driving.SpeedWatch({ 80 }).let { it.onSpeed(82.0, 0); it.onSpeed(82.0, 10_000) })
        assertFalse(Driving.SpeedWatch({ 0 }).let { it.onSpeed(150.0, 0); it.onSpeed(150.0, 10_000) })
    }
}
