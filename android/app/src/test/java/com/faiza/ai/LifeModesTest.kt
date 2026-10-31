package com.faiza.ai

import com.faiza.ai.ai.contacts.FavoriteContact
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.modes.LifeModes
import com.faiza.ai.ai.modes.ModeCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeModesTest {

    private fun eff(
        manual: String? = null, until: Long = 0L, now: Long = 1000L, car: Boolean = false, autoDrive: Boolean = true,
        game: Boolean = false, prayer: Boolean = false, sleep: Boolean = false, minute: Int = 12 * 60
    ) = LifeModes.effective(manual, until, now, car, autoDrive, game, true, prayer, true, sleep, minute, 23 * 60, 7 * 60)

    @Test
    fun whichModeIsOn() {
        assertNull(eff())
        assertEquals("meeting", eff(manual = "meeting", car = true))
        assertNull(eff(manual = "meeting", until = 500L, now = 1000L)) // expired
        assertEquals("driving", eff(car = true))
        assertNull(eff(car = true, autoDrive = false))
        assertEquals("gaming", eff(game = true))
        assertEquals("prayer", eff(prayer = true))
        assertEquals("sleeping", eff(sleep = true, minute = 2 * 60))
        assertNull(eff(sleep = true, minute = 12 * 60))
    }

    @Test
    fun timeWindows() {
        assertTrue(LifeModes.inWindow(23 * 60 + 30, 23 * 60, 7 * 60))
        assertTrue(LifeModes.inWindow(60, 23 * 60, 7 * 60))
        assertFalse(LifeModes.inWindow(8 * 60, 23 * 60, 7 * 60))
        assertTrue(LifeModes.inWindow(10 * 60, 9 * 60, 17 * 60))
        assertFalse(LifeModes.inWindow(5, 5, 5))
        val prayers = listOf(4 * 60 + 31, 13 * 60 + 5)
        assertTrue(LifeModes.inPrayerWindow(4 * 60 + 40, prayers))
        assertFalse(LifeModes.inPrayerWindow(4 * 60 + 52, prayers))
        assertTrue(LifeModes.inPrayerWindow(5, listOf(23 * 60 + 50))) // crosses midnight
        assertEquals(4 * 60 + 31, LifeModes.parseClock("04:31 (+06)"))
        assertEquals(19 * 60 + 2, LifeModes.parseClock("19:02"))
        assertNull(LifeModes.parseClock("25:00"))
    }

    @Test
    fun callDecisions() {
        assertEquals(LifeModes.CallAction.LET_RING_VIP, LifeModes.decideCall(true, null, 0L))
        assertEquals(LifeModes.CallAction.DECLINE, LifeModes.decideCall(false, null, 0L))
        assertEquals(LifeModes.CallAction.LET_RING_REPEAT, LifeModes.decideCall(false, 1_000L, 1_000L + 120_000L))
        assertEquals(LifeModes.CallAction.DECLINE, LifeModes.decideCall(false, 1_000L, 1_000L + 200_000L))
    }

    @Test
    fun politeMessages() {
        val driving = LifeModes.BUILT_IN.first { it.id == "driving" }
        val bn = LifeModes.message(driving, "bn", "Asif Mavia", "Faiza")
        assertTrue(bn.contains("Asif এখন গাড়ি চালাচ্ছেন"))
        assertTrue(bn.contains("Faiza, Asif-এর AI সহকারী"))
        assertFalse(bn.contains("{"))
        val en = LifeModes.message(driving, "en", "Asif Mavia", "Faiza")
        assertTrue(en.contains("Asif is driving") && en.contains("Asif's AI assistant"))
        val both = LifeModes.message(driving, "both", "", "Faiza")
        assertTrue(both.contains("উনি এখন") && both.contains("The person you called is driving") && both.contains("their AI"))
        assertEquals("Asif is in class. — Faiza", LifeModes.message(driving, "en", "Asif", "Faiza", "{name} is in class. — {ai}"))
        LifeModes.BUILT_IN.forEach { m ->
            assertFalse(LifeModes.message(m, "both", "Asif", "Faiza").contains("{"))
        }
    }

    @Test
    fun summaryGroupsCallers() {
        val calls = listOf(
            ModeCall(1, "017", "Rahim", "driving", true, true),
            ModeCall(2, "017", "Rahim", "driving", true, false),
            ModeCall(3, "018", "", "driving", true, true)
        )
        assertEquals("Rahim called 2 times, 018 called once", LifeModes.summary(calls))
        assertEquals("No one called.", LifeModes.summary(emptyList()))
    }

    @Test
    fun myPeopleNicknames() {
        val list = FavoriteContacts.parse(
            """[{"name":"Faiza Rahman","number":"01711111111","nicknames":["বউ","জান",null,""],"whatsapp":"01999999999","vip":true},
               {"name":"Rahim","number":"01822222222"}, {"name":"Bad","number":null}]"""
        )
        assertEquals(2, list.size)
        assertEquals(listOf("বউ", "জান"), list[0].nicknames)
        assertTrue(list[0].vip)
        assertEquals("01999999999", list[0].whatsappNumber)
        assertEquals("01822222222", list[1].whatsappNumber) // same as phone when empty
        assertEquals("", list[1].whatsapp)
        assertEquals("বউ", FavoriteContacts.cleanSpokenName("আমার বউকে"))
        assertEquals("wife", FavoriteContacts.cleanSpokenName("my wife"))
        assertEquals("Rahim", FavoriteContacts.cleanSpokenName("Rahim ko"))
        assertEquals(list[0], FavoriteContacts.bestMatch(list, listOf("বউ")))
        assertEquals(list[1], FavoriteContacts.bestMatch(list, listOf("rahim")))
        assertNull(FavoriteContacts.bestMatch(list, listOf("Karim Uddin")))
        assertEquals(listOf("A", "x"), FavoriteContact(name = "A", number = "1", nicknames = listOf("x")).allNames)
    }

    @Test
    fun myPeopleFoundHowTheOwnerSaysIt() {
        val wife = FavoriteContact(name = "Faiza", number = "01711111111", nicknames = listOf("বউ", "আইরিন"), whatsapp = "01999999999")
        val rahim = FavoriteContact(name = "Rahim", number = "01822222222")
        val people = listOf(wife, rahim)
        // Bangla with endings, translated relation words, other scripts.
        listOf("আমার বউয়ের", "বউয়ের", "বউকে", "my wife", "wife", "Bou", "আইরিনের", "Irin", "Irin's", "ফাইজার",
            "ফাইজা", "Faiza", "faiza whatsapp").forEach { said ->
            assertEquals(said, wife, FavoriteContacts.bestMatch(people, listOf(said)))
        }
        assertEquals(rahim, FavoriteContacts.bestMatch(people, listOf("রহিমকে")))
        assertNull(FavoriteContacts.bestMatch(people, listOf("Karim")))
        assertNull(FavoriteContacts.bestMatch(people, listOf("Rani")))
        assertNull(FavoriteContacts.bestMatch(people, listOf("mom")))
        assertEquals("01999999999", FavoriteContacts.bestMatch(people, listOf("বউ"))!!.whatsappNumber)
    }
}
