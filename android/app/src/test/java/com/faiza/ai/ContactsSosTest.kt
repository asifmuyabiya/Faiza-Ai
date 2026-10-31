package com.faiza.ai

import com.faiza.ai.ai.contacts.FavoriteContact
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.skills.ContactFinder
import com.faiza.ai.ai.sos.SosMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsSosTest {

    @Test
    fun internationalNumbers() {
        assertEquals("8801712345678", PhoneNumbers.toInternational("01712-345678", "880"))
        assertEquals("8801712345678", PhoneNumbers.toInternational("+880 1712 345678", "91"))
        assertEquals("8801712345678", PhoneNumbers.toInternational("008801712345678", null))
        assertEquals("919876543210", PhoneNumbers.toInternational("9876543210", "91"))
        assertEquals("8801712345678", PhoneNumbers.toInternational("8801712345678", "880"))
        assertEquals("01712345678", PhoneNumbers.toInternational("01712345678", null))
    }

    @Test
    fun sameNumberIgnoresFormatting() {
        assertTrue(PhoneNumbers.sameNumber("+880 1712-345678", "01712345678"))
        assertFalse(PhoneNumbers.sameNumber("01712345678", "01812345678"))
        assertFalse(PhoneNumbers.sameNumber("", ""))
    }

    @Test
    fun countryLookup() {
        assertEquals("880", PhoneNumbers.dialCodeForIso("BD"))
        assertEquals("1", PhoneNumbers.dialCodeForIso("ca"))
        assertNull(PhoneNumbers.dialCodeForIso("zz"))
        assertEquals("Nepal", PhoneNumbers.countryForDial("977")?.name)
    }

    private val ammu = FavoriteContact(name = "Ammu", number = "01711111111", sos = true)
    private val chand = FavoriteContact(name = "চাঁদ", number = "01722222222")
    private val rahim = FavoriteContact(name = "Rahim Bhai", number = "01733333333", sos = true)

    @Test
    fun favoritesMatchAnyScript() {
        val list = listOf(ammu, chand, rahim)
        assertEquals("চাঁদ", FavoriteContacts.bestMatch(list, ContactFinder.queries("Chand", "চাঁদ"))?.name)
        assertEquals("Rahim Bhai", FavoriteContacts.bestMatch(list, listOf("rahim"))?.name)
        assertNull(FavoriteContacts.bestMatch(list, listOf("Karim")))
    }

    @Test
    fun sosContactsAreMarkedOnesInOrderMaxFive() {
        assertEquals(listOf("Ammu", "Rahim Bhai"), FavoriteContacts.sosContacts(listOf(ammu, chand, rahim)).map { it.name })
        // Nobody marked → first favourites, never more than 5.
        val many = (1..8).map { FavoriteContact(name = "P$it", number = "0170000000$it") }
        assertEquals(5, FavoriteContacts.sosContacts(many).size)
        assertTrue(FavoriteContacts.sosContacts(emptyList()).isEmpty())
    }

    @Test
    fun sosMessageWithLocation() {
        val now = 1_700_000_000_000L
        val msg = SosMessage.build("Rahim", "Blood group B+", SosMessage.Place(23.810331, 90.412521, 12.4f, now), now)
        assertTrue(msg.startsWith("SOS! Rahim needs help urgently! Blood group B+"))
        assertTrue(msg.contains("https://maps.google.com/?q=23.810331,90.412521"))
        assertTrue(msg.contains("±12 m"))
        assertTrue(msg.contains("just now"))
    }

    @Test
    fun sosMessageWithoutLocationOrName() {
        val msg = SosMessage.build(null, "  ", null)
        assertTrue(msg.startsWith("SOS! I need help urgently!"))
        assertTrue(msg.contains("Location unavailable"))
        assertFalse(msg.contains("maps.google.com"))
    }
}
