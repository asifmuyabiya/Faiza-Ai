package com.faiza.ai

import com.faiza.ai.ai.khata.Khata
import com.faiza.ai.ai.khata.Khata.Entry
import com.faiza.ai.ai.khata.Khata.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KhataTest {

    @Test fun balancesPerPerson() {
        val e = listOf(
            Entry(type = Type.GAVE, amount = 500.0, person = "Karim"),
            Entry(type = Type.GOT_BACK, amount = 200.0, person = "karim"),
            Entry(type = Type.TOOK, amount = 1000.0, person = "Rahim"),
            Entry(type = Type.PAID_BACK, amount = 400.0, person = "Rahim"),
            Entry(type = Type.GAVE, amount = 100.0, person = "Salam"),
            Entry(type = Type.GOT_BACK, amount = 100.0, person = "Salam"),
            Entry(type = Type.EXPENSE, amount = 850.0, note = "বাজার")
        )
        val b = Khata.balances(e)
        assertEquals(2, b.size) // Salam is settled
        val rahim = b.first { it.person == "Rahim" }
        assertEquals(-600.0, rahim.net, 0.001)
        val karim = b.first { it.person.equals("karim", true) }
        assertEquals(300.0, karim.net, 0.001)
    }

    @Test fun summaryAndCategories() {
        val now = System.currentTimeMillis()
        val e = listOf(
            Entry(at = now, type = Type.EXPENSE, amount = 850.0, note = "বাজারে মাছ"),
            Entry(at = now, type = Type.EXPENSE, amount = 120.0, note = "রিকশা ভাড়া"),
            Entry(at = now, type = Type.EXPENSE, amount = 50.0, note = "সবজি"),
            Entry(at = now, type = Type.INCOME, amount = 20000.0, note = "বেতন"),
            Entry(at = now - 60L * 24 * 3600_000, type = Type.EXPENSE, amount = 999.0, note = "old")
        )
        val s = Khata.summary(e, Khata.period("month", now))
        assertEquals(1020.0, s.spent, 0.001)
        assertEquals(20000.0, s.earned, 0.001)
        assertEquals("বাজার" to 900.0, s.byNote.first())
        assertTrue(s.byNote.any { it.first == "যাতায়াত" })
        val all = Khata.summary(e, Khata.period("all", now))
        assertEquals(2019.0, all.spent, 0.001)
    }

    @Test fun money() {
        assertEquals("৳1,500", Khata.taka(1500.0))
        assertEquals("৳12.50", Khata.taka(12.5))
        assertEquals(Type.GAVE, Type.from("baki"))
        assertEquals(Type.EXPENSE, Type.from("expense"))
        assertEquals(null, Type.from("xyz"))
    }
}
