package com.faiza.ai

import com.faiza.ai.ai.live.StopWordSpotter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWordTest {
    private fun res(word: String, conf: Double) = """{"result":[{"conf":$conf,"end":1.0,"start":0.5,"word":"$word"}],"text":"$word"}"""

    @Test fun confidentStopWordsStopHer() {
        assertTrue(StopWordSpotter.isStop(res("stop", 0.95), emptySet()))
        assertTrue(StopWordSpotter.isStop(res("wait", 0.8), setOf("আজ", "বৃষ্টি")))
        assertTrue(StopWordSpotter.isStop(res("faiza", 0.9), emptySet()))
    }

    @Test fun unsureOrUnknownDoesNot() {
        assertFalse(StopWordSpotter.isStop(res("stop", 0.4), emptySet()))
        assertFalse(StopWordSpotter.isStop(res("[unk]", 1.0), emptySet()))
        assertFalse(StopWordSpotter.isStop("""{"text":""}""", emptySet()))
        assertFalse(StopWordSpotter.isStop("not json", emptySet()))
    }

    @Test fun herOwnWordsDoNot() {
        // She just said "ফাইজা" / "স্টপ" herself: that's her voice from the speaker.
        assertFalse(StopWordSpotter.isStop(res("faiza", 0.99), setOf("আমি", "ফাইজা")))
        assertFalse(StopWordSpotter.isStop(res("stop", 0.99), setOf("স্টপ")))
    }
}
