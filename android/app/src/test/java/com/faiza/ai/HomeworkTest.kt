package com.faiza.ai

import com.faiza.ai.ai.vision.Homework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkTest {
    private val reply = "QUESTION: 12 ও 18 এর লসাগু কত?\nSTEPS:\n1. 12 = 2×2×3\n2. 18 = 2×3×3\n3. লসাগু = 2×2×3×3 = 36\nANSWER: 36\nSAY: উত্তর ৩৬। ধাপগুলো কার্ডে দেখো।"

    @Test fun splitsSpokenPartAndCard() {
        assertEquals("উত্তর ৩৬। ধাপগুলো কার্ডে দেখো।", Homework.spoken(reply))
        val card = Homework.card(reply)
        assertTrue(card.contains("ANSWER: 36"))
        assertFalse(card.contains("SAY:"))
    }

    @Test fun fallsBackToAnswerLine() {
        assertEquals("36", Homework.spoken("QUESTION: x\nANSWER: 36"))
    }

    @Test fun modesChangeTheTeacher() {
        assertTrue(Homework.system(Homework.Mode.HINT, "class 5", "Bangla").contains("Do NOT give the answer"))
        assertTrue(Homework.system(Homework.Mode.CHECK, null, "Bangla").contains("mistake"))
        assertTrue(Homework.system(Homework.Mode.EXPLAIN, "class 8", "Bangla").contains("(class 8)"))
        assertEquals(Homework.Mode.EXPLAIN, Homework.Mode.from(null))
    }
}
