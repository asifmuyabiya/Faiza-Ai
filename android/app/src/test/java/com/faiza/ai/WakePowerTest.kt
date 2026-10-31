package com.faiza.ai

import com.faiza.ai.ai.wake.WakePower
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePowerTest {
    @Test fun rules() {
        assertFalse(WakePower.allowScreenOff(screenOnly = true, smart = false, batteryPct = 100, charging = true))
        assertTrue(WakePower.allowScreenOff(false, smart = false, batteryPct = 5, charging = false))
        assertTrue(WakePower.allowScreenOff(false, true, 30, false))
        assertFalse(WakePower.allowScreenOff(false, true, 29, false))
        assertTrue(WakePower.allowScreenOff(false, true, 10, charging = true))
    }
}
