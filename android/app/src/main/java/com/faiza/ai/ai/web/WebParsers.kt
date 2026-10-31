package com.faiza.ai.ai.web

import com.google.gson.JsonObject
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class SearchResult(val title: String, val url: String, val snippet: String)

data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val openNow: Boolean? = null,
    val kind: String? = null
)

/** Pure parsing/formatting for web search, places, routes, weather and images (unit-tested). */
object WebParsers {

    // ===== Text =====

    private val ENTITIES = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&#x27;" to "'",
        "&apos;" to "'", "&nbsp;" to " ", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…", "&#x2F;" to "/"
    )
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    fun decodeEntities(s: String): String {
        var out = s
        ENTITIES.forEach { (k, v) -> out = out.replace(k, v) }
        return NUMERIC_ENTITY.replace(out) { m ->
            val code = runCatching { m.groupValues[2].toInt(if (m.groupValues[1].isNotEmpty()) 16 else 10) }.getOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
    }

    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("[ \\t\\x0B\\f\\r]+")
    private val BLANK_LINES = Regex("\\n\\s*\\n+")

    fun stripTags(html: String): String = decodeEntities(TAG.replace(html, " ")).replace(SPACES, " ").trim()

    /** Readable text of a web page: no scripts, styles, menus' markup; paragraphs kept. */
    fun htmlToText(html: String): String {
        var s = html
        s = Regex("(?is)<(script|style|noscript|svg|head|nav|footer|form)[^>]*>.*?</\\1>").replace(s, " ")
        s = Regex("(?is)<!--.*?-->").replace(s, " ")
        s = Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr|/section|/article)[^>]*>").replace(s, "\n")
        s = decodeEntities(TAG.replace(s, " "))
        s = s.lines().joinToString("\n") { it.replace(SPACES, " ").trim() }
        return s.replace(BLANK_LINES, "\n\n").trim()
    }

    fun pageTitle(html: String): String? =
        Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let { stripTags(it) }?.ifBlank { null }

    // ===== DuckDuckGo =====

    /** DuckDuckGo links go through a redirect; the real address is in "uddg". */
    fun unwrapDuckLink(href: String): String {
        val h = decodeEntities(href)
        val uddg = Regex("[?&]uddg=([^&]+)").find(h)?.groupValues?.get(1)
        val url = if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else h
        return if (url.startsWith("//")) "https:$url" else url
    }

    /** html.duckduckgo.com results. */
    fun parseDuckHtml(html: String, max: Int = 6): List<SearchResult> {
        val titles = Regex("(?is)<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").findAll(html).toList()
        val snippets = Regex("(?is)<(?:a|div)[^>]+class=\"result__snippet\"[^>]*>(.*?)</(?:a|div)>").findAll(html).toList()
        return titles.mapIndexedNotNull { i, m ->
            val url = unwrapDuckLink(m.groupValues[1])
            if (url.contains("duckduckgo.com/y.js")) return@mapIndexedNotNull null // ads
            SearchResult(stripTags(m.groupValues[2]), url, snippets.getOrNull(i)?.let { stripTags(it.groupValues[1]) }.orEmpty())
        }.filter { it.title.isNotBlank() }.take(max)
    }

    /** lite.duckduckgo.com results (a plain table). */
    fun parseDuckLite(html: String, max: Int = 6): List<SearchResult> {
        val links = Regex("(?is)<a[^>]+href=\"([^\"]+)\"[^>]*class=['\"]result-link['\"][^>]*>(.*?)</a>").findAll(html).toList()
            .ifEmpty { Regex("(?is)<a[^>]+class=['\"]result-link['\"][^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>").findAll(html).toList() }
        val snippets = Regex("(?is)<td[^>]+class=['\"]result-snippet['\"][^>]*>(.*?)</td>").findAll(html).toList()
        return links.mapIndexed { i, m ->
            SearchResult(stripTags(m.groupValues[2]), unwrapDuckLink(m.groupValues[1]),
                snippets.getOrNull(i)?.let { stripTags(it.groupValues[1]) }.orEmpty())
        }.filter { it.title.isNotBlank() && !it.url.contains("duckduckgo.com/y.js") }.take(max)
    }

    fun siteName(url: String): String =
        Regex("^https?://(?:www\\.)?([^/]+)").find(url)?.groupValues?.get(1) ?: url

    fun formatResults(results: List<SearchResult>): String =
        results.mapIndexed { i, r -> "${i + 1}. ${r.title} (${siteName(r.url)}) — ${r.snippet.take(300)}\n   ${r.url}" }
            .joinToString("\n")

    // ===== Places & distance =====

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun formatDistance(m: Double): String = when {
        m < 1_000 -> "${(m / 10).roundToInt() * 10} m"
        m < 10_000 -> "%.1f km".format(java.util.Locale.ENGLISH, m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }

    fun formatDuration(seconds: Long): String {
        val min = ((seconds + 30) / 60).toInt()
        return when {
            min < 1 -> "under a minute"
            min < 60 -> "$min min"
            else -> "${min / 60} h" + if (min % 60 > 0) " ${min % 60} min" else ""
        }
    }

    private fun JsonObject.str(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.dbl(k: String) = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }

    /** OpenStreetMap Nominatim (jsonv2) search results. */
    fun parseNominatim(items: com.google.gson.JsonArray): List<Place> = items.mapNotNull { el ->
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val lat = o.dbl("lat") ?: return@mapNotNull null
        val lon = o.dbl("lon") ?: return@mapNotNull null
        val display = o.str("display_name").orEmpty()
        val name = o.str("name")?.ifBlank { null } ?: display.substringBefore(",").ifBlank { return@mapNotNull null }
        val address = display.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != name }.take(3).joinToString(", ")
        Place(name, address, lat, lon, kind = o.str("type")?.replace('_', ' '))
    }

    /** Google Places API (New) searchText results. */
    fun parseGooglePlaces(root: JsonObject): List<Place> =
        root.getAsJsonArray("places")?.mapNotNull { el ->
            val o = el.asJsonObject
            val loc = o.getAsJsonObject("location") ?: return@mapNotNull null
            Place(
                name = o.getAsJsonObject("displayName")?.str("text") ?: return@mapNotNull null,
                address = o.str("formattedAddress").orEmpty(),
                lat = loc.dbl("latitude") ?: return@mapNotNull null,
                lon = loc.dbl("longitude") ?: return@mapNotNull null,
                rating = o.dbl("rating"),
                ratingCount = o.get("userRatingCount")?.takeIf { it.isJsonPrimitive }?.asInt,
                openNow = o.getAsJsonObject("currentOpeningHours")?.get("openNow")?.takeIf { it.isJsonPrimitive }?.asBoolean
            )
        }.orEmpty()

    /** Nearest first, one line each, for Faiza to read. */
    fun formatPlaces(places: List<Place>, lat: Double, lon: Double, max: Int = 5): String =
        places.map { it to distanceMeters(lat, lon, it.lat, it.lon) }
            .sortedBy { it.second }
            .take(max)
            .mapIndexed { i, (p, d) ->
                buildString {
                    append("${i + 1}. ${p.name} — ${formatDistance(d)} away")
                    p.rating?.let { r ->
                        append(" · rating %.1f".format(java.util.Locale.ENGLISH, r))
                        p.ratingCount?.let { append(" ($it reviews)") }
                    }
                    when (p.openNow) {
                        true -> append(" · open now")
                        false -> append(" · closed now")
                        null -> Unit
                    }
                    if (p.address.isNotBlank()) append(" · ${p.address}")
                }
            }.joinToString("\n")

    // ===== Weather (Open-Meteo, WMO codes) =====

    fun weatherText(code: Int): String = when (code) {
        0 -> "clear sky"
        1 -> "mostly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75, 77 -> "snow"
        80 -> "light showers"
        81 -> "showers"
        82 -> "heavy showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown weather"
    }

    fun formatWeather(place: String, root: JsonObject): String {
        val cur = root.getAsJsonObject("current") ?: return "ERROR: no weather data"
        val sb = StringBuilder()
        val t = cur.dbl("temperature_2m")
        val feels = cur.dbl("apparent_temperature")
        val hum = cur.dbl("relative_humidity_2m")
        val wind = cur.dbl("wind_speed_10m")
        val code = cur.dbl("weather_code")?.toInt()
        sb.append("Weather in $place now: ")
        code?.let { sb.append(weatherText(it)).append(", ") }
        t?.let { sb.append("${it.roundToInt()}°C") }
        feels?.let { sb.append(" (feels like ${it.roundToInt()}°C)") }
        hum?.let { sb.append(", humidity ${it.roundToInt()}%") }
        wind?.let { sb.append(", wind ${it.roundToInt()} km/h") }
        sb.append('.')
        val daily = root.getAsJsonObject("daily")
        val days = daily?.getAsJsonArray("time")
        if (days != null) {
            val max = daily.getAsJsonArray("temperature_2m_max")
            val min = daily.getAsJsonArray("temperature_2m_min")
            val rain = daily.getAsJsonArray("precipitation_probability_max")
            val codes = daily.getAsJsonArray("weather_code")
            val labels = listOf("Today", "Tomorrow", "Day after")
            for (i in 0 until minOf(days.size(), 3)) {
                sb.append("\n${labels[i]} (${days[i].asString}): ")
                codes?.getOrNull(i)?.let { sb.append(weatherText(it.asDouble.toInt())).append(", ") }
                val lo = min?.getOrNull(i)?.asDouble?.roundToInt()
                val hi = max?.getOrNull(i)?.asDouble?.roundToInt()
                if (lo != null && hi != null) sb.append("$lo–$hi°C")
                rain?.getOrNull(i)?.takeIf { !it.isJsonNull }?.let { sb.append(", ${it.asInt}% chance of rain") }
            }
        }
        return sb.toString()
    }

    private fun com.google.gson.JsonArray.getOrNull(i: Int) = if (i < size()) get(i)?.takeIf { !it.isJsonNull } else null

    // ===== Images (Pollinations) =====

    enum class Shape(val width: Int, val height: Int) { SQUARE(1024, 1024), PORTRAIT(768, 1344), LANDSCAPE(1344, 768) }

    fun pollinationsUrl(prompt: String, shape: Shape, seed: Int): String {
        val encoded = URLEncoder.encode(prompt.trim().take(900), "UTF-8").replace("+", "%20")
        return "https://image.pollinations.ai/prompt/$encoded?width=${shape.width}&height=${shape.height}" +
                "&seed=$seed&nologo=true&model=flux&referrer=faiza-ai"
    }
}
