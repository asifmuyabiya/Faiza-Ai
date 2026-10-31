package com.faiza.ai

import com.faiza.ai.ai.routines.Repeat
import com.faiza.ai.ai.routines.Routines
import com.faiza.ai.ai.routines.ScheduledTask
import com.faiza.ai.ai.routines.Watch
import com.faiza.ai.ai.routines.WatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Scheduled tasks & watches (video 40:31). */
class RoutinesTest {

    private val dhaka = ZoneId.of("Asia/Dhaka")
    // Monday 5 Oct 2026, 09:00 in Dhaka
    private val now = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, dhaka)

    private fun at(ms: Long?) = Instant.ofEpochMilli(ms!!).atZone(dhaka)

    @Test fun dailyRunsTodayIfStillAheadElseTomorrow() {
        val later = ScheduledTask(label = "x", instruction = "x", hour = 20, minute = 0, repeat = Repeat.DAILY)
        assertEquals(ZonedDateTime.of(2026, 10, 5, 20, 0, 0, 0, dhaka), at(Routines.nextRun(later, now)))
        val earlier = later.copy(hour = 7)
        assertEquals(ZonedDateTime.of(2026, 10, 6, 7, 0, 0, 0, dhaka), at(Routines.nextRun(earlier, now)))
    }

    @Test fun weekdaysWeekendsAndChosenDays() {
        val friNight = now.withDayOfMonth(9).withHour(23) // Friday 23:00
        val weekdays = ScheduledTask(label = "x", instruction = "x", hour = 7, repeat = Repeat.WEEKDAYS)
        assertEquals(12, at(Routines.nextRun(weekdays, friNight)).dayOfMonth) // Monday
        val weekends = ScheduledTask(label = "x", instruction = "x", hour = 10, repeat = Repeat.WEEKENDS)
        assertEquals(10, at(Routines.nextRun(weekends, now)).dayOfMonth) // Saturday
        val weekly = ScheduledTask(label = "x", instruction = "x", hour = 18, repeat = Repeat.WEEKLY, days = listOf(3, 5))
        assertEquals(7, at(Routines.nextRun(weekly, now)).dayOfMonth) // Wednesday
        assertNull(Routines.nextRun(weekly.copy(days = emptyList()), now))
    }

    @Test fun oneTimeTasks() {
        val inHalfHour = now.plusMinutes(30).toInstant().toEpochMilli()
        val soon = ScheduledTask(label = "x", instruction = "x", atMillis = inHalfHour)
        assertEquals(inHalfHour, Routines.nextRun(soon, now))
        assertNull("already passed", Routines.nextRun(soon, now.plusHours(1)))
        val onDate = ScheduledTask(label = "x", instruction = "x", hour = 10, minute = 30,
            dateEpochDay = LocalDate.of(2026, 10, 20).toEpochDay())
        assertEquals(ZonedDateTime.of(2026, 10, 20, 10, 30, 0, 0, dhaka), at(Routines.nextRun(onDate, now)))
        assertNull(Routines.nextRun(onDate.copy(dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay()), now))
        // After it runs it switches off; repeating ones stay on.
        assertFalse(Routines.afterRun(soon, 1L).enabled)
        assertTrue(Routines.afterRun(soon.copy(repeat = Repeat.DAILY), 1L).enabled)
        assertNull(Routines.nextRun(soon.copy(enabled = false), now))
    }

    @Test fun readableDescriptions() {
        val t = ScheduledTask(label = "x", instruction = "x", hour = 20, minute = 5, repeat = Repeat.DAILY)
        assertEquals("Every day at 8:05 PM", Routines.describe(t, dhaka))
        assertEquals("Mon, Fri at 8:05 PM", Routines.describe(t.copy(repeat = Repeat.WEEKLY, days = listOf(5, 1)), dhaka))
        assertEquals("today 8:05 PM", Routines.whenText(Routines.nextRun(t, now)!!, now))
        assertEquals("tomorrow 7:00 AM", Routines.whenText(Routines.nextRun(t.copy(hour = 7, minute = 0), now)!!, now))
    }

    @Test fun parsesTimesAndDays() {
        assertEquals(20 to 30, Routines.parseTime("20:30"))
        assertEquals(20 to 30, Routines.parseTime("8:30 pm"))
        assertEquals(0 to 15, Routines.parseTime("12.15 a.m."))
        assertEquals(7 to 0, Routines.parseTime("7"))
        assertNull(Routines.parseTime("25:00"))
        assertNull(Routines.parseTime("tomorrow"))
        assertEquals(listOf(1, 3, 5), Routines.parseDays("mon, wed and Friday"))
        assertEquals(listOf(5, 6), Routines.parseDays("শুক্র,শনি"))
    }

    @Test fun messageWatchMatching() {
        val w = Watch(label = "Rahim", app = "WhatsApp", sender = "Rahim, রহিম")
        assertTrue(Routines.matchesMessage(w, "com.whatsapp", "WhatsApp", "রহিম ভাই", "কোথায়?", 0))
        assertTrue(Routines.matchesMessage(w, "com.whatsapp", "WhatsApp", "Rahim Uddin", "hi", 0))
        assertFalse(Routines.matchesMessage(w, "com.whatsapp", "WhatsApp", "Karim", "hi", 0))
        assertFalse(Routines.matchesMessage(w, "org.telegram.messenger", "Telegram", "Rahim", "hi", 0))
        val sms = Watch(label = "OTP", app = "SMS", keywords = "otp, code")
        assertTrue(Routines.matchesMessage(sms, "com.google.android.apps.messaging", "Messages", "Bank", "Your OTP is 1234", 0))
        assertFalse(Routines.matchesMessage(sms, "com.google.android.apps.messaging", "Messages", "Bank", "Balance low", 0))
        assertFalse("expired", Routines.matchesMessage(w.copy(expiresAt = 10), "com.whatsapp", "WhatsApp", "Rahim", "hi", 20))
        assertFalse("screen watches never match messages",
            Routines.matchesMessage(w.copy(kind = WatchKind.SCREEN), "com.whatsapp", "WhatsApp", "Rahim", "hi", 0))
    }

    @Test fun findsByWhatTheUserSays() {
        val tasks = listOf(
            ScheduledTask(label = "Medicine", instruction = "Remind the user to take medicine", hour = 21),
            ScheduledTask(label = "Weather", instruction = "Tell the user the weather", hour = 7)
        )
        assertEquals("Medicine", Routines.find(tasks, "cancel the medicine reminder") { "${it.label} ${it.instruction}" }?.label)
        assertEquals("Weather", Routines.find(tasks, "weather") { it.label }?.label)
        assertNull(Routines.find(tasks, "the reminder") { it.label })
    }

    @Test fun storageSurvivesDamage() {
        var raw: String? = null
        Routines.saveTasks({ raw = it }, listOf(ScheduledTask(label = "A", instruction = "B", hour = 1)))
        assertEquals("A", Routines.loadTasks { raw }.single().label)
        assertTrue(Routines.loadTasks { "garbage" }.isEmpty())
        val w = Routines.loadWatches { """[{"label":"x"},{"id":"1"}]""" }
        assertEquals(1, w.size)
        assertEquals("", w[0].sender)
    }
}
