package com.faiza.ai

import com.faiza.ai.ai.web.ResearchAgent
import com.faiza.ai.ai.web.ResearchParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchParsersTest {

    // Shape taken from a real Google News RSS reply (2026-10-05).
    private val rss = """<rss><channel><title>"Saudi Arabia" - Google News</title>
        <item><title>Saudi-backed forces recapture key areas around strait from Houthis in Yemen - The Guardian</title>
        <link>https://news.google.com/rss/articles/CBMi123?oc=5</link><guid isPermaLink="false">CBMi123</guid>
        <pubDate>Mon, 05 Oct 2026 19:44:00 GMT</pubDate><description>&lt;a href="x"&gt;x&lt;/a&gt;</description>
        <source url="https://www.theguardian.com">The Guardian</source></item>
        <item><title>Riyadh Season opens &amp; draws crowds - Arab News</title><link>https://news.google.com/rss/articles/CBMi456</link>
        <pubDate>Mon, 05 Oct 2026 12:00:00 GMT</pubDate><source url="https://www.arabnews.com">Arab News</source></item>
        </channel></rss>"""

    @Test
    fun parsesGoogleNews() {
        val items = ResearchParsers.parseNewsRss(rss)
        assertEquals(2, items.size)
        assertEquals("Saudi-backed forces recapture key areas around strait from Houthis in Yemen", items[0].title)
        assertEquals("The Guardian", items[0].source)
        assertEquals("Mon, 05 Oct 2026 19:44:00 GMT", items[0].pubDate)
        assertEquals("Riyadh Season opens & draws crowds", items[1].title)
        assertTrue(ResearchParsers.newsUrl("বাংলাদেশ", bangla = true).contains("hl=bn&gl=BD"))
    }

    @Test
    fun picksTheNewestStrongestGemini() {
        val models = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-3.1-pro-preview", "gemini-3.8-flash",
            "gemini-3.8-flash-lite", "gemini-3.8-pro", "gemini-3.8-flash-live", "gemini-3.9-flash-image", "gemini-3.8-pro-tts")
        assertEquals("gemini-3.8-pro", ResearchParsers.bestGeminiModel(models))
        assertEquals("gemini-3.8-flash", ResearchParsers.bestGeminiModel(models - "gemini-3.8-pro"))
        assertEquals(null, ResearchParsers.bestGeminiModel(listOf("text-embedding-004")))
    }

    @Test
    fun queriesAndNewsDetection() {
        assertEquals(listOf("bangladesh news today", "dhaka traffic update"),
            ResearchParsers.queries("1. bangladesh news today\n- \"dhaka traffic update\"\nDONE\n", 5))
        assertTrue(ResearchParsers.looksLikeNews("সৌদি আরবের সর্বশেষ খবর"))
        assertTrue(ResearchParsers.looksLikeNews("latest Bangladesh update"))
        assertFalse(ResearchParsers.looksLikeNews("how does photosynthesis work"))
    }

    @Test
    fun piracySitesAreNeverUsed() {
        assertTrue(ResearchAgent.blocked("https://mlwbd.example/movie"))
        assertTrue(ResearchAgent.blocked("https://www.1337x.to/x"))
        assertFalse(ResearchAgent.blocked("https://www.chorki.com/watch"))
        assertFalse(ResearchAgent.blocked("https://www.netflix.com/title/1"))
    }
}
