package com.faiza.ai

import com.faiza.ai.ui.windows.CodeTyping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live code window types steadily and finishes any reply in about 10 seconds. */
class CodeTypingTest {
    @Test fun nothingToType() = assertEquals(0, CodeTyping.charsPerTick(0))

    @Test fun shortCodeTypesSlowlyButNeverStalls() {
        assertEquals(3, CodeTyping.charsPerTick(100))
        assertEquals(2, CodeTyping.charsPerTick(2))
    }

    @Test fun bigRepliesFinishInAboutTenSeconds() {
        var left = 30_000
        var ticks = 0
        while (left > 0) { left -= CodeTyping.charsPerTick(left); ticks++ }
        val seconds = ticks * CodeTyping.TICK_MS / 1000.0
        assertTrue("took $seconds s", seconds in 3.0..40.0)
    }

    @Test fun neverTooFastPerFrame() = assertTrue(CodeTyping.charsPerTick(5_000_000) <= 400)
}
