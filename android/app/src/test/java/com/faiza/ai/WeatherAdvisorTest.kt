package com.faiza.ai

import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.guardian.DeleteGuard
import com.faiza.ai.ai.web.HourWx
import com.faiza.ai.ai.web.WeatherAdvisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAdvisorTest {

    private fun day(rainFrom: Int = 99, feels: Double = 31.0, storm: Int = -1) = (0..23).map { h ->
        HourWx(h, 30.0, feels, if (h >= rainFrom) 75 else 10, if (h == storm) 95 else 1)
    }

    @Test
    fun rainLaterMeansGoNow() {
        val a = WeatherAdvisor.alerts(day(rainFrom = 14), nowHour = 12, b = PhraseBase.BENGALI, addr = "বস", dayKey = "d")
        assertEquals(1, a.size)
        assertTrue(a[0].text.contains("এখনই সেরে আসুন"))
        assertTrue(a[0].text.contains("দুপুর ২টা") || a[0].text.contains("দুপুর 2টা"))
    }

    @Test
    fun heatStormAndQuietDays() {
        val hot = WeatherAdvisor.alerts(day(feels = 39.0), 12, PhraseBase.ENGLISH, "Boss", "d")
        assertTrue(hot.any { it.key == "d-heat" && it.text.contains("39") })
        val storm = WeatherAdvisor.alerts(day(rainFrom = 15, storm = 15), 13, PhraseBase.ENGLISH, "Boss", "d")
        assertTrue(storm.first().key == "d-storm")
        assertTrue(storm.none { it.key.startsWith("d-rain") }) // storm already covers it
        assertTrue(WeatherAdvisor.alerts(day(), 12, PhraseBase.ENGLISH, "Boss", "d").isEmpty())
    }

    @Test
    fun morningSummary() {
        val m = WeatherAdvisor.morning(day(rainFrom = 16), 8, PhraseBase.ENGLISH, "Jaan")!!
        assertTrue(m.startsWith("Jaan, today's weather: up to 30°"))
        assertTrue(m.contains("4 pm"))
        assertEquals("7 am", WeatherAdvisor.clockEn(7))
        assertEquals("12 pm", WeatherAdvisor.clockEn(12))
    }

    @Test
    fun importantFilesGetTheirOwnWarning() {
        assertEquals(DeleteGuard.Level.IMPORTANT_FILE, DeleteGuard.analyze(listOf("Delete passport_scan.jpg?"), listOf("Cancel", "Delete")))
        assertEquals(DeleteGuard.Level.IMPORTANT_FILE, DeleteGuard.analyze(listOf("Delete \"report.pdf\"?"), listOf("Cancel", "Delete")))
        assertEquals(DeleteGuard.Level.IMPORTANT_FILE, DeleteGuard.analyze(listOf("এই সার্টিফিকেট মুছবেন?"), listOf("বাতিল", "মুছুন")))
        assertEquals(DeleteGuard.Level.NORMAL, DeleteGuard.analyze(listOf("Delete this photo?"), listOf("Cancel", "Delete")))
        assertTrue(DeleteGuard.line(DeleteGuard.Level.IMPORTANT_FILE, PhraseBase.BENGALI).contains("আরেকবার ভালো করে দেখে নিন"))
    }
}
