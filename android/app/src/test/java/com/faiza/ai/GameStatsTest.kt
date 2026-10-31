package com.faiza.ai

import com.faiza.ai.ai.game.GameStats
import com.faiza.ai.ai.game.GameStats.Match
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GameStatsTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private fun at(day: String, h: Int) = LocalDate.parse(day).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dayReport() {
        val m = listOf(
            Match(at("2026-10-18", 10), "Free Fire", 3, 7, false),
            Match(at("2026-10-18", 11), "Free Fire", 5, 1, true),
            Match(at("2026-10-18", 12), "Free Fire", 1, 22, false),
            Match(at("2026-10-17", 23), "Free Fire", 9, 2, false)
        )
        val d = GameStats.day(m, LocalDate.parse("2026-10-18"), zone)
        assertEquals(3, d.matches); assertEquals(9, d.kills); assertEquals(1, d.booyahs)
        assertEquals(1, d.best); assertEquals(5, d.bestKills)
        val t = GameStats.text(d, "Free Fire")
        assertTrue(t.contains("3 ম্যাচ") && t.contains("9 কিল") && t.contains("BOOYAH"))
        assertTrue(GameStats.shareText(d, "Free Fire").contains("#FaizaAI"))
    }

    @Test fun emptyDay() {
        val d = GameStats.day(emptyList(), LocalDate.parse("2026-10-18"), zone)
        assertEquals(0, d.matches); assertEquals(0.0, d.avgKills, 0.0)
    }
}
