package com.faiza.ai

import com.faiza.ai.ai.prayer.PrayerCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCalcTest {

    private fun near(expected: Int, actual: Int, tol: Int, what: String) =
        assertTrue("$what: expected ~${PrayerCalc.clock(expected)}, got ${PrayerCalc.clock(actual)}", kotlin.math.abs(expected - actual) <= tol)

    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test fun dhakaWinter() {
        // Dhaka, 1 Jan 2025 (Islamic Foundation timetable ≈ Fajr 5:20, Sunrise 6:41, Dhuhr 12:03, Asr 3:47, Maghrib 5:25, Isha 6:44)
        val t = PrayerCalc.times(2025, 1, 1, 23.8103, 90.4125, 6.0)
        near(hm(5, 20), t.fajr, 6, "Fajr")
        near(hm(6, 41), t.sunrise, 5, "Sunrise")
        near(hm(12, 3), t.dhuhr, 4, "Dhuhr")
        near(hm(15, 47), t.asr, 8, "Asr")
        near(hm(17, 25), t.maghrib, 6, "Maghrib")
        near(hm(18, 44), t.isha, 8, "Isha")
    }

    @Test fun dhakaSummerOrderIsSane() {
        val t = PrayerCalc.times(2025, 6, 21, 23.8103, 90.4125, 6.0)
        val all = t.list().map { it.second }
        assertEquals(all, all.sorted())
        near(hm(11, 59), t.dhuhr, 6, "Dhuhr")
        near(hm(18, 50), t.maghrib, 10, "Maghrib")
    }

    @Test fun meccaUmmAlQura() {
        val t = PrayerCalc.times(2025, 6, 21, 21.4225, 39.8262, 3.0, PrayerCalc.Method.UMM_AL_QURA, asrFactor = 1)
        near(hm(12, 22), t.dhuhr, 5, "Dhuhr")
        assertEquals(90, t.isha - (t.maghrib - 1))
    }

    @Test fun hijriDates() {
        val r = PrayerCalc.hijri(2025, 3, 15)
        assertEquals(1446, r.year); assertEquals(9, r.month)
        assertTrue(r.isRamadan)
        val m = PrayerCalc.hijri(2024, 7, 17) // ~10 Muharram 1446
        assertEquals(1446, m.year); assertEquals(1, m.month)
        assertTrue(m.day in 9..12)
        assertEquals(PrayerCalc.hijri(2025, 3, 16).day, PrayerCalc.hijri(2025, 3, 15, adjustDays = 1).day)
    }

    @Test fun qiblaDirections() {
        val dhaka = PrayerCalc.qibla(23.8103, 90.4125)
        assertTrue("Dhaka qibla $dhaka", dhaka in 276.0..280.0)
        val london = PrayerCalc.qibla(51.5074, -0.1278)
        assertTrue("London qibla $london", london in 117.0..121.0)
    }

    @Test fun clockText() {
        assertEquals("5:07 AM", PrayerCalc.clock(5 * 60 + 7))
        assertEquals("12:03 PM", PrayerCalc.clock(12 * 60 + 3))
        assertEquals("12:00 AM", PrayerCalc.clock(0))
    }

    @Test fun reminderLines() {
        val pc = com.faiza.ai.ai.prayer.PrayerCenter
        assertEquals("আসর-এর ওয়াক্ত শুরু হয়েছে। নামাজের সময় হয়েছে।", pc.line("azan", "Asr", true, 0))
        assertEquals("ফজর নামাজের আর ১০ মিনিট বাকি।".replace("১০", "10"), pc.line("azan", "Fajr", true, 10))
        assertTrue(pc.line("sehri", "Fajr", true, 0).contains("সেহরি"))
        assertTrue(pc.line("iftar", "Maghrib", false, 0).contains("Iftar"))
    }
}
