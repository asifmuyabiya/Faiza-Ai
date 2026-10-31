package com.faiza.ai

import com.faiza.ai.ai.live.EchoText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoTurnTest {
    private val said = EchoText.words("আজ ঢাকায় আবহাওয়া রোদ ঝলমলে থাকবে, বিকালে একটু বৃষ্টি হতে পারে, ঠিক আছে").toSet()

    @Test fun herOwnWordsAreEcho() {
        assertTrue(EchoText.isEchoTurn(EchoText.words("বিকালে একটু বৃষ্টি হতে পারে"), said, minWords = 4))
        assertTrue(EchoText.isEchoTurn(EchoText.words("রোদ ঝলমলে থাকবে"), said))
    }

    @Test fun theUserIsNeverTakenForEcho() {
        assertFalse(EchoText.isEchoTurn(EchoText.words("থামো আমার একটা কথা শোনো"), said, minWords = 4))
        // A short real answer that repeats her words must not be muted.
        assertFalse(EchoText.isEchoTurn(EchoText.words("ঠিক আছে"), said, minWords = 4))
        assertFalse(EchoText.isEchoTurn(emptyList(), said))
    }

    @Test fun mixedSpeechCountsAsUser() {
        assertFalse(EchoText.isEchoTurn(EchoText.words("বৃষ্টি হলে ছাতা নেব কিনা বলো"), said, minWords = 4))
    }
}
