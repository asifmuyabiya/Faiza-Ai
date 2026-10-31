package com.faiza.ai

import android.view.KeyEvent
import com.faiza.ai.assist.HeadsetDoublePress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTriggersTest {

    private val HOOK = KeyEvent.KEYCODE_HEADSETHOOK
    private val DOWN = KeyEvent.ACTION_DOWN
    private val UP = KeyEvent.ACTION_UP

    @Test fun singlePressGoesToMusic() {
        val d = HeadsetDoublePress()
        var fired = 0
        assertFalse(d.onKey(HOOK, DOWN, 1000) { fired++ })
        assertFalse(d.onKey(HOOK, UP, 1100) { fired++ })
        assertEquals(0, fired)
    }

    @Test fun doublePressCallsFaizaOnceAndSwallowsSecondPress() {
        val d = HeadsetDoublePress()
        var fired = 0
        assertFalse(d.onKey(HOOK, DOWN, 1000) { fired++ })
        assertFalse(d.onKey(HOOK, UP, 1080) { fired++ })
        assertTrue(d.onKey(HOOK, DOWN, 1300) { fired++ })
        assertTrue(d.onKey(HOOK, UP, 1380) { fired++ })
        assertEquals(1, fired)
        // A third press right after starts over (no double trigger).
        assertFalse(d.onKey(HOOK, DOWN, 1500) { fired++ })
        assertEquals(1, fired)
    }

    @Test fun slowPressesDontTrigger() {
        val d = HeadsetDoublePress()
        var fired = 0
        d.onKey(HOOK, DOWN, 1000) { fired++ }
        d.onKey(HOOK, UP, 1100) { fired++ }
        assertFalse(d.onKey(HOOK, DOWN, 2000) { fired++ })
        assertEquals(0, fired)
    }

    @Test fun otherKeysIgnored() {
        val d = HeadsetDoublePress()
        var fired = 0
        assertFalse(d.onKey(KeyEvent.KEYCODE_VOLUME_UP, DOWN, 1000) { fired++ })
        assertFalse(d.onKey(KeyEvent.KEYCODE_VOLUME_UP, DOWN, 1100) { fired++ })
        assertEquals(0, fired)
        assertTrue(d.onKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, DOWN, 2000) { fired++ }.not())
        assertTrue(d.onKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, DOWN, 2200) { fired++ })
        assertEquals(1, fired)
    }
}
