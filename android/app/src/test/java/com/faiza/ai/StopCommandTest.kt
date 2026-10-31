package com.faiza.ai

import com.faiza.ai.ai.live.EchoText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "থামো থামো, এটা না" while Faiza talks must stop her; her own words must not. */
class StopCommandTest {
    private val said = EchoText.words("আজ ঢাকায় আবহাওয়া রোদ ঝলমলে থাকবে, বিকালে একটু বৃষ্টি হতে পারে").toSet()

    private fun stop(text: String, her: Set<String> = said) = EchoText.isStopCommand(EchoText.words(text), her)

    @Test fun banglaStopWordsStopHer() {
        assertTrue(stop("থামো থামো এটা না"))
        assertTrue(stop("থামেন"))
        assertTrue(stop("এই থামুন তো"))
        assertTrue(stop("চুপ করো"))
        assertTrue(stop("দাঁড়াও অন্যটা বলো"))
        assertTrue(stop("স্টপ"))
        assertTrue(stop("ওয়েট"))
    }

    @Test fun englishAndBanglishStopHer() {
        assertTrue(stop("stop stop"))
        assertTrue(stop("Wait, not this one"))
        assertTrue(stop("thamo"))
    }

    @Test fun stopWordMixedWithHerEchoStillCounts() {
        assertTrue(stop("বিকালে একটু বৃষ্টি থামো"))
    }

    @Test fun herOwnEchoNeverStopsHer() {
        assertFalse(stop("বিকালে একটু বৃষ্টি হতে পারে"))
        assertFalse(stop(""))
        // She is reading a text that itself says "থামো": its echo must not stop her.
        val reading = EchoText.words("গাড়ি থামো বলল পুলিশ").toSet()
        assertFalse(stop("গাড়ি থামো", reading))
    }

    @Test fun ordinaryTalkIsNotAStop() {
        assertFalse(stop("আরো বলো"))
        assertFalse(stop("ঠিক আছে চালিয়ে যাও"))
    }
}
