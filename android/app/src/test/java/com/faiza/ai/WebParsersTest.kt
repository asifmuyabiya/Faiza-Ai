package com.faiza.ai

import com.faiza.ai.ai.web.WebParsers
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web search, places, routes, weather and image links (video 29:43). */
class WebParsersTest {

    @Test fun parsesDuckDuckGoHtmlResults() {
        val html = """
            <div class="result results_links"><h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.espncricinfo.com%2Fseries%2Fx&amp;rut=abc">Bangladesh <b>beat</b> India &amp; win</a>
            </h2><a class="result__snippet" href="#">Bangladesh won by <b>5 wickets</b> in Dhaka&#39;s stadium.</a></div>
            <div class="result"><a class="result__a" href="https://duckduckgo.com/y.js?ad=1">Ad</a><a class="result__snippet">ad</a></div>
            <div class="result"><a class="result__a" href="https://example.org/page">Second</a><a class="result__snippet">Two</a></div>
        """.trimIndent()
        val r = WebParsers.parseDuckHtml(html)
        assertEquals(2, r.size)
        assertEquals("Bangladesh beat India & win", r[0].title)
        assertEquals("https://www.espncricinfo.com/series/x", r[0].url)
        assertEquals("Bangladesh won by 5 wickets in Dhaka's stadium.", r[0].snippet)
        assertEquals("espncricinfo.com", WebParsers.siteName(r[0].url))
        assertEquals("https://example.org/page", r[1].url)
    }

    @Test fun parsesDuckDuckGoLiteResults() {
        val html = """
            <tr><td><a rel="nofollow" href="https://bn.wikipedia.org/wiki/ঢাকা" class='result-link'>ঢাকা - উইকিপিডিয়া</a></td></tr>
            <tr><td class='result-snippet'>ঢাকা বাংলাদেশের রাজধানী।</td></tr>
        """.trimIndent()
        val r = WebParsers.parseDuckLite(html)
        assertEquals(1, r.size)
        assertEquals("ঢাকা - উইকিপিডিয়া", r[0].title)
        assertEquals("ঢাকা বাংলাদেশের রাজধানী।", r[0].snippet)
    }

    @Test fun pageTextDropsScriptsAndKeepsParagraphs() {
        val html = "<html><head><title>News &amp; more</title><style>p{}</style></head><body><nav>Menu</nav>" +
                "<script>var x=1;</script><h1>Headline</h1><p>First&nbsp;para.</p><p>Second para.</p></body></html>"
        val text = WebParsers.htmlToText(html)
        assertFalse(text.contains("var x"))
        assertFalse(text.contains("Menu"))
        assertTrue(text.contains("Headline"))
        assertTrue(text.contains("First para."))
        assertEquals("News & more", WebParsers.pageTitle(html))
    }

    @Test fun distancesAndDurationsReadNaturally() {
        // Dhaka Shahbag → Gulshan 1 ≈ 6.5 km
        val d = WebParsers.distanceMeters(23.7383, 90.3958, 23.7806, 90.4166)
        assertTrue("got $d", d in 5_000.0..6_000.0)
        assertEquals("450 m", WebParsers.formatDistance(447.0))
        assertEquals("2.5 km", WebParsers.formatDistance(2_480.0))
        assertEquals("25 km", WebParsers.formatDistance(24_700.0))
        assertEquals("25 min", WebParsers.formatDuration(1_500))
        assertEquals("1 h 5 min", WebParsers.formatDuration(3_900))
        assertEquals("under a minute", WebParsers.formatDuration(20))
    }

    @Test fun nearbyPlacesAreSortedNearestFirst() {
        val osm = JsonParser.parseString(
            """[{"lat":"23.75","lon":"90.39","name":"Far Pharmacy","display_name":"Far Pharmacy, Road 2, Dhanmondi, Dhaka","type":"pharmacy"},
                {"lat":"23.7385","lon":"90.3960","name":"","display_name":"Lazz Pharma, Shahbag, Dhaka","type":"pharmacy"},
                {"lat":"bad"}]"""
        ).asJsonArray
        val places = WebParsers.parseNominatim(osm)
        assertEquals(2, places.size)
        val text = WebParsers.formatPlaces(places, 23.7383, 90.3958)
        assertTrue(text, text.startsWith("1. Lazz Pharma — 30 m away"))
        assertTrue(text.contains("2. Far Pharmacy"))
    }

    @Test fun googlePlacesShowRatingsAndOpenNow() {
        val root = JsonParser.parseString(
            """{"places":[{"displayName":{"text":"Star Kabab"},"formattedAddress":"Dhanmondi 2, Dhaka",
               "rating":4.3,"userRatingCount":5120,"location":{"latitude":23.739,"longitude":90.383},
               "currentOpeningHours":{"openNow":true}}]}"""
        ).asJsonObject
        val text = WebParsers.formatPlaces(WebParsers.parseGooglePlaces(root), 23.7383, 90.3958)
        assertTrue(text, text.contains("Star Kabab"))
        assertTrue(text, text.contains("rating 4.3 (5120 reviews) · open now"))
    }

    @Test fun weatherIsReadable() {
        val root = JsonParser.parseString(
            """{"current":{"temperature_2m":31.4,"apparent_temperature":37.2,"relative_humidity_2m":74,"weather_code":2,"wind_speed_10m":9.6},
               "daily":{"time":["2026-10-05","2026-10-06","2026-10-07"],"weather_code":[95,61,0],
               "temperature_2m_max":[33.1,31.0,32.4],"temperature_2m_min":[26.0,25.4,25.9],"precipitation_probability_max":[80,60,null]}}"""
        ).asJsonObject
        val w = WebParsers.formatWeather("Dhaka, Bangladesh", root)
        assertTrue(w, w.startsWith("Weather in Dhaka, Bangladesh now: partly cloudy, 31°C (feels like 37°C), humidity 74%, wind 10 km/h."))
        assertTrue(w, w.contains("Today (2026-10-05): thunderstorm, 26–33°C, 80% chance of rain"))
        assertTrue(w, w.contains("Day after (2026-10-07): clear sky, 26–32°C"))
    }

    @Test fun imageLinkIsSafelyEncoded() {
        val url = WebParsers.pollinationsUrl("A cat & dog, 50% cute?", WebParsers.Shape.PORTRAIT, 42)
        assertTrue(url, url.startsWith("https://image.pollinations.ai/prompt/A%20cat%20%26%20dog%2C%2050%25%20cute%3F?"))
        assertTrue(url.contains("width=768&height=1344"))
        assertTrue(url.contains("seed=42"))
    }
}
