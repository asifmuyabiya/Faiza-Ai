package com.faiza.ai

import com.faiza.ai.ai.vision.Medicine
import com.faiza.ai.ai.vision.Medicine.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineTest {

    @Test fun patterns() {
        assertEquals(listOf(Slot.MORNING, Slot.NIGHT), Medicine.slots("1+0+1"))
        assertEquals(listOf(Slot.MORNING, Slot.NOON, Slot.NIGHT), Medicine.slots("1+1+1"))
        assertEquals(listOf(Slot.NIGHT), Medicine.slots("0+0+1"))
        assertEquals(listOf(Slot.MORNING, Slot.NOON, Slot.EVENING, Slot.NIGHT), Medicine.slots("1+1+1+1"))
        assertEquals(listOf(Slot.MORNING, Slot.NIGHT), Medicine.slots("১+০+১"))
        assertEquals(listOf(Slot.MORNING, Slot.NIGHT), Medicine.slots("½+0+1".replace("½", "0.5")))
        assertEquals(listOf(Slot.MORNING), Medicine.slots("??"))
    }

    @Test fun beforeFoodIsEarlier() {
        assertEquals(7 to 30, Medicine.timeFor(Slot.MORNING, "খাওয়ার আগে"))
        assertEquals(8 to 0, Medicine.timeFor(Slot.MORNING, "after food"))
        assertEquals(21 to 0, Medicine.timeFor(Slot.NIGHT, "before meal"))
    }

    @Test fun parsesModelJson() {
        val reply = "```json\n[{\"name\":\"Napa 500mg\",\"dose\":\"1 tablet\",\"pattern\":\"1+1+1\",\"timing\":\"after food\",\"days\":5}," +
                "{\"name\":\"Seclo 20\",\"dose\":\"1 capsule\",\"pattern\":\"1+0+1\",\"timing\":\"খাওয়ার আগে\",\"days\":null}," +
                "{\"name\":\"\",\"pattern\":\"1+0+0\"}]\n```"
        val m = Medicine.parse(reply)
        assertEquals(2, m.size)
        assertEquals("Napa 500mg", m[0].name); assertEquals(5, m[0].days)
        assertNull(m[1].days)
        assertTrue(Medicine.describe(m[1]).contains("সকাল, রাত"))
        assertTrue(Medicine.parse("no list here").isEmpty())
    }

    @Test fun slugs() {
        assertEquals("napa-500mg", Medicine.slug("Napa 500mg"))
    }
}
