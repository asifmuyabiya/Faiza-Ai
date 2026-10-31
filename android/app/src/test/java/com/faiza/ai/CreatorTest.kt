package com.faiza.ai

import com.faiza.ai.ai.HeartLines
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CreatorTest {

    @Test fun alwaysNamesTheCreator() {
        for (user in listOf("Asif", "Sumaiya", "")) {
            val p = HeartLines.promptSection(user, romantic = false, girlfriendMode = false, random = Random(1))
            assertTrue(p.contains("Mohammad Asif Muaviya"))
            assertTrue(p.contains("মোহাম্মদ আসিফ মুয়াবিয়া"))
            assertTrue(p.contains("ভালোবাসা দিয়ে, একটু একটু করে"))
            assertTrue(p.contains("Never say Google"))
        }
    }

    @Test fun onlyTheCreatorIsToldHeMadeHer() {
        val him = HeartLines.promptSection("Asif Muaviya", romantic = true, girlfriendMode = false, random = Random(1))
        assertTrue(him.contains("IS Mohammad Asif Muaviya"))
        assertTrue(him.contains("romantically"))
        val wife = HeartLines.promptSection("Sumaiya", romantic = true, girlfriendMode = false, random = Random(1))
        assertTrue(wife.contains("may be someone else"))
        assertFalse(wife.contains("romantically"))
    }

    @Test fun recognisesHisName() {
        listOf("Asif", "asif muaviya", "Mohammad Asif", "আসিফ").forEach { assertTrue(it, HeartLines.isCreator(it)) }
        listOf("Sumaiya", "", "Rahim").forEach { assertFalse(it, HeartLines.isCreator(it)) }
    }

    @Test fun familyEditionIsOpen() {
        assertTrue(FaizaApp.UNLOCK_ALL_FOR_TESTING)
    }
}
