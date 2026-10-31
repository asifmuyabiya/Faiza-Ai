package com.faiza.ai.ai.web

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.SkillException
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.ai.sos.LocationHelper
import com.faiza.ai.ui.ImageViewerActivity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Internet skills (video 29:43): web search, nearby places, routes, weather, image generation. */
object WebSkills {

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val APP_UA = "FaizaAI/3.0 (Android personal assistant)"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val imageHttp: OkHttpClient by lazy {
        AIProviders.http.newBuilder().readTimeout(110, TimeUnit.SECONDS).build()
    }

    private val prefs get() = FaizaApp.instance.prefs

    fun skills(): List<Skill> = listOf(
        Skill(
            "search_web", "🌐", "Search the internet", "\"Who won the match yesterday?\"",
            "Search the internet and get the top results with short summaries, to answer questions about news, " +
                    "prices, scores, people, facts you're unsure of or anything recent. Answer the user from the results.",
            listOf(ToolParam("query", ParamType.STRING, "Search words, in the best language for the topic"))
        ) { _, args -> searchWeb(args.require("query")) },

        Skill(
            "deep_research", "🔬", "Deep research", "\"Research the best budget phones\"",
            "The research agent: researches a question in depth in the BACKGROUND (several rounds of searching and " +
                    "reading many pages, with its own model) and writes a full report with sources. Use it for anything " +
                    "that needs real digging, comparisons, or a full summary (e.g. 'a full summary of Bangladesh news " +
                    "today'). It takes a few minutes: tell the user you've started and that you'll tell them when it's done " +
                    "— you will get the result as a message. For a quick fact or a headline use search_web / latest_news.",
            listOf(
                ToolParam("topic", ParamType.STRING, "The full question, with every detail the user gave"),
                ToolParam("depth", ParamType.STRING, "quick, deep (default) or max", required = false)
            )
        ) { ctx, args ->
            val depth = ResearchAgent.Depth.from(args.str("depth") ?: prefs.researchDepth)
            if (ResearchAgent.start(ctx, args.require("topic"), depth, tellWhenDone = true))
                "OK: research started (${depth.label}). Tell the user in one sentence that you're researching and will tell them when it's ready."
            else "BUSY: another research is still running — tell the user to wait for it or cancel it on the Research screen."
        },

        Skill(
            "latest_news", "📰", "Latest news", "\"সৌদি আরবে এখন কী হচ্ছে?\"",
            "The newest headlines about a place, person or topic from Google News, newest first, with how long ago each " +
                    "was published. Use for 'latest news / what's happening in X / খবর'. For a place abroad, also call " +
                    "world_time so you can say what time it is there. Summarise the headlines in your own words.",
            listOf(
                ToolParam("topic", ParamType.STRING, "Place, person or topic, e.g. \"Saudi Arabia\", \"Bangladesh\""),
                ToolParam("bangla", ParamType.BOOLEAN, "true for Bangla news sources", required = false)
            )
        ) { _, args -> latestNews(args.require("topic"), args.bool("bangla")) },

        Skill(
            "world_time", "🕐", "Time anywhere", "\"সৌদিতে এখন কয়টা বাজে?\"",
            "The current local date and time in any city or country, and the difference from the user's own time.",
            listOf(ToolParam("place", ParamType.STRING, "City or country"))
        ) { _, args -> worldTime(args.require("place")) },

        Skill(
            "read_webpage", "📄", "Read a web page", "Reads a result for details",
            "Read the text of a web page (e.g. a search result) when the summary isn't enough.",
            listOf(ToolParam("url", ParamType.STRING, "Full https:// address"))
        ) { _, args -> readWebpage(args.require("url")) },

        Skill(
            "get_weather", "🌤️", "Weather", "\"Will it rain tomorrow?\"",
            "Current weather and the next 3 days. Leave place empty for where the user is now.",
            listOf(ToolParam("place", ParamType.STRING, "City or area (optional)", required = false))
        ) { ctx, args -> weather(ctx, args.str("place")) },

        Skill(
            "find_nearby", "📍", "Places nearby", "\"Find a pharmacy near me\"",
            "Find places near the user (restaurants, pharmacies, ATMs, hospitals, mosques, shops…), nearest first " +
                    "with distance (and ratings/opening hours when available). To go to one, use navigate_to.",
            listOf(
                ToolParam("what", ParamType.STRING, "What to look for, in English, e.g. \"pharmacy\", \"biryani restaurant\""),
                ToolParam("radius_km", ParamType.INTEGER, "How far to look, 1–30 (default 3)", required = false)
            )
        ) { ctx, args -> findNearby(ctx, args.require("what"), (args.int("radius_km") ?: 3).coerceIn(1, 30)) },

        Skill(
            "travel_time", "🚗", "Travel time", "\"How long to the airport?\"",
            "How long it takes to get from where the user is to a place, and how far it is" +
                    " (with live traffic when a Google Maps key is set).",
            listOf(
                ToolParam("destination", ParamType.STRING, "Place or address"),
                ToolParam("mode", ParamType.STRING, "How to travel", required = false,
                    enum = listOf("driving", "walking", "cycling", "transit"))
            )
        ) { ctx, args -> travelTime(ctx, args.require("destination"), args.str("mode") ?: "driving") },

        Skill(
            "generate_image", "🎨", "Create images", "\"Draw a cat astronaut\"",
            "Create a picture from a description, show it on the screen and save it to the Gallery. Write the prompt " +
                    "in English, detailed (subject, style, colors, lighting), even if the user spoke another language.",
            listOf(
                ToolParam("prompt", ParamType.STRING, "Detailed English description of the picture"),
                ToolParam("shape", ParamType.STRING, "Picture shape (default square)", required = false,
                    enum = listOf("square", "portrait", "landscape"))
            )
        ) { ctx, args -> generateImage(ctx, args.require("prompt"), args.str("shape")) }
    )

    // ===== HTTP =====

    internal suspend fun getText(url: String, ua: String = BROWSER_UA, maxBytes: Long = 2_000_000): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", ua)
                .header("Accept-Language", "en-US,en;q=0.8,bn;q=0.6").get().build()
            AIProviders.http.newCall(req).execute().use { r ->
                val body = r.body ?: return@use r.code to ""
                val src = body.source()
                src.request(maxBytes)
                val bytes = src.buffer.let { buf -> buf.readByteArray(minOf(buf.size, maxBytes)) }
                r.code to String(bytes, Charsets.UTF_8)
            }
        }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("User-Agent", APP_UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        AIProviders.http.newCall(b.get().build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw SkillException("the service answered HTTP ${r.code}")
            runCatching { JsonParser.parseString(text) }.getOrNull()?.let {
                if (it.isJsonObject) it.asJsonObject else JsonObject().apply { add("items", it) }
            } ?: throw SkillException("unreadable reply")
        }
    }

    private suspend fun postJson(url: String, body: JsonObject, headers: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
        headers.forEach { (k, v) -> b.header(k, v) }
        AIProviders.http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            if (!r.isSuccessful) {
                val msg = json?.getAsJsonObject("error")?.get("message")?.asString
                    ?: json?.get("detail")?.toString() ?: "HTTP ${r.code}"
                throw SkillException(msg)
            }
            json ?: throw SkillException("unreadable reply")
        }
    }

    private fun enc(s: String) = Uri.encode(s)

    // ===== Search =====

    private suspend fun searchWeb(query: String): String {
        val tavilyKey = prefs.tavilyApiKey.trim()
        if (tavilyKey.isNotEmpty()) {
            runCatching {
                val root = postJson(
                    "https://api.tavily.com/search",
                    JsonObject().apply {
                        addProperty("api_key", tavilyKey)
                        addProperty("query", query)
                        addProperty("max_results", 5)
                        addProperty("include_answer", true)
                    },
                    mapOf("Authorization" to "Bearer $tavilyKey")
                )
                val answer = root.get("answer")?.takeIf { it.isJsonPrimitive }?.asString
                val results = root.getAsJsonArray("results")?.map {
                    val o = it.asJsonObject
                    SearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(),
                        o.get("content")?.asString.orEmpty())
                }.orEmpty()
                if (answer != null || results.isNotEmpty()) {
                    return (answer?.let { "Quick answer: $it\n" } ?: "") + WebParsers.formatResults(results) +
                            "\nAnswer the user briefly from these results and mention the source site."
                }
            }
        }
        // Optional keyed searches (MAYA Optional): SerpAPI (Google), then Brave.
        serpApi(query)?.let { return it }
        brave(query)?.let { return it }
        val results = runCatching {
            val (code, html) = getText("https://html.duckduckgo.com/html/?q=${enc(query)}&kl=wt-wt")
            if (code == 200) WebParsers.parseDuckHtml(html) else emptyList()
        }.getOrDefault(emptyList()).ifEmpty {
            runCatching {
                val (code, html) = getText("https://lite.duckduckgo.com/lite/?q=${enc(query)}")
                if (code == 200) WebParsers.parseDuckLite(html) else emptyList()
            }.getOrDefault(emptyList())
        }.ifEmpty { wikipedia(query) }
        if (results.isEmpty()) {
            return "ERROR: the search found nothing (or the internet is off). Try other words, or offer to open Google with web_search."
        }
        return WebParsers.formatResults(results) +
                "\nAnswer the user briefly from these results and mention the source site. " +
                "If a result needs more detail, call read_webpage with its address."
    }

    private val ANSWER_NOTE = "\nAnswer the user briefly from these results and mention the source site."

    private suspend fun serpApi(query: String): String? {
        val key = prefs.serpApiKey.trim().ifEmpty { return null }
        return runCatching {
            val root = getJson("https://serpapi.com/search.json?engine=google&num=6&q=${enc(query)}&api_key=${enc(key)}")
            val box = root.getAsJsonObject("answer_box")
            val quick = box?.let { (it.get("answer") ?: it.get("snippet") ?: it.get("result"))?.takeIf { e -> e.isJsonPrimitive }?.asString }
            val results = root.getAsJsonArray("organic_results")?.take(6)?.map {
                val o = it.asJsonObject
                SearchResult(o.get("title")?.asString.orEmpty(), o.get("link")?.asString.orEmpty(), o.get("snippet")?.asString.orEmpty())
            }.orEmpty()
            if (quick == null && results.isEmpty()) null
            else (quick?.let { "Quick answer: $it\n" } ?: "") + WebParsers.formatResults(results) + ANSWER_NOTE
        }.getOrNull()
    }

    private suspend fun brave(query: String): String? {
        val key = prefs.braveApiKey.trim().ifEmpty { return null }
        return runCatching {
            val root = getJson(
                "https://api.search.brave.com/res/v1/web/search?count=6&q=${enc(query)}",
                mapOf("X-Subscription-Token" to key, "Accept" to "application/json")
            )
            val results = root.getAsJsonObject("web")?.getAsJsonArray("results")?.map {
                val o = it.asJsonObject
                SearchResult(
                    o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(),
                    WebParsers.stripTags(o.get("description")?.asString.orEmpty())
                )
            }.orEmpty()
            if (results.isEmpty()) null else WebParsers.formatResults(results) + ANSWER_NOTE
        }.getOrNull()
    }

    /** Results from every free/keyed source we have, best first, without duplicates. */
    internal suspend fun collectResults(query: String): List<SearchResult> {
        val all = mutableListOf<SearchResult>()
        prefs.serpApiKey.trim().takeIf { it.isNotEmpty() }?.let { key ->
            runCatching {
                getJson("https://serpapi.com/search.json?engine=google&num=8&q=${enc(query)}&api_key=${enc(key)}")
                    .getAsJsonArray("organic_results")?.forEach {
                        val o = it.asJsonObject
                        all += SearchResult(o.get("title")?.asString.orEmpty(), o.get("link")?.asString.orEmpty(), o.get("snippet")?.asString.orEmpty())
                    }
            }
        }
        prefs.braveApiKey.trim().takeIf { it.isNotEmpty() }?.let { key ->
            runCatching {
                getJson("https://api.search.brave.com/res/v1/web/search?count=8&q=${enc(query)}",
                    mapOf("X-Subscription-Token" to key, "Accept" to "application/json"))
                    .getAsJsonObject("web")?.getAsJsonArray("results")?.forEach {
                        val o = it.asJsonObject
                        all += SearchResult(o.get("title")?.asString.orEmpty(), o.get("url")?.asString.orEmpty(),
                            WebParsers.stripTags(o.get("description")?.asString.orEmpty()))
                    }
            }
        }
        if (all.size < 4) {
            all += runCatching {
                val (code, html) = getText("https://html.duckduckgo.com/html/?q=${enc(query)}&kl=wt-wt")
                if (code == 200) WebParsers.parseDuckHtml(html, 8) else emptyList()
            }.getOrDefault(emptyList())
        }
        if (all.isEmpty()) all += wikipedia(query)
        return all.filter { it.url.startsWith("http") }.distinctBy { WebParsers.siteName(it.url) + it.title.take(30) }
    }

    private suspend fun deepResearch(ctx: Context, topic: String): String {
        val provider = AIProviders.fromPrefs(prefs) ?: throw SkillException("the AI isn't set up")
        // 1. Plan the searches.
        val plan = provider.chat(
            "You plan web research. Reply with 5 short, varied search queries for the topic, one per line, no numbering.",
            listOf(com.faiza.ai.ai.provider.ChatTurn.User(topic)), emptyList()
        ).text.orEmpty().lines().map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.length > 2 }.take(6)
        val queries = (listOf(topic) + plan).distinct()
        // 2. Search.
        val results = queries.flatMap { collectResults(it).take(5) }.distinctBy { it.url }
        if (results.isEmpty()) return "ERROR: the searches found nothing (is the internet on?)."
        // 3. Read the best pages.
        val pages = results.take(4).mapNotNull { r ->
            runCatching {
                val (code, html) = getText(r.url, maxBytes = 1_500_000)
                if (code in 200..299) "SOURCE: ${r.title} (${r.url})\n" + WebParsers.htmlToText(html).take(3_500) else null
            }.getOrNull()
        }
        val snippets = results.take(20).joinToString("\n") { "- ${it.title} (${it.url}): ${it.snippet.take(200)}" }
        // 4. Write the report.
        val report = provider.chat(
            "You write clear research reports for a phone user. Use only the material given; say when something is " +
                    "uncertain. Structure: short summary, key findings (bullets), details, and a Sources list with links. " +
                    "Write in the same language as the topic. Markdown is fine.",
            listOf(com.faiza.ai.ai.provider.ChatTurn.User(
                "TOPIC: $topic\n\nSEARCH RESULTS:\n$snippets\n\nPAGES:\n${pages.joinToString("\n\n")}".take(60_000)
            )),
            emptyList()
        ).text?.trim().orEmpty()
        if (report.isEmpty()) return "ERROR: couldn't write the report."
        val saved = runCatching {
            com.faiza.ai.ai.files.FileSkills.createFile(ctx, "Research - ${topic.take(40)}", report, "md")
        }.getOrElse { "(couldn't save the file)" }
        return "REPORT (${queries.size} searches, ${pages.size} pages read):\n${report.take(2_500)}\n\n$saved " +
                "Tell the user the main findings in a few sentences."
    }

    private suspend fun latestNews(topic: String, bangla: Boolean): String {
        val (code, xml) = getText(ResearchParsers.newsUrl(topic, bangla || topic.any { it.code in 0x0980..0x09FF }), maxBytes = 800_000)
        if (code !in 200..299) return "ERROR: the news service answered HTTP $code"
        val items = ResearchParsers.parseNewsRss(xml, 10)
        if (items.isEmpty()) return "No recent news found for \"$topic\"."
        val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.ENGLISH)
        return "Latest news about $topic (newest first):\n" + items.joinToString("\n") { n ->
            val age = runCatching { fmt.parse(n.pubDate)!!.time }.getOrNull()?.let { t ->
                val m = (now - t) / 60_000
                when { m < 60 -> "${m} min ago"; m < 1_440 -> "${m / 60} h ago"; else -> "${m / 1_440} days ago" }
            } ?: n.pubDate
            "- ${n.title} (${n.source}, $age)"
        }
    }

    private suspend fun worldTime(place: String): String {
        val g = getJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=${enc(place)}")
            .getAsJsonArray("results")?.firstOrNull()?.asJsonObject
            ?: return "ERROR: couldn't find \"$place\""
        val zone = java.time.ZoneId.of(g.get("timezone")?.asString ?: return "ERROR: no time zone for $place")
        val there = java.time.ZonedDateTime.now(zone)
        val here = java.time.ZonedDateTime.now()
        val diffMin = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
        val diff = when {
            diffMin == 0 -> "the same time as you"
            diffMin > 0 -> "${diffMin / 60}h${if (diffMin % 60 != 0) " ${diffMin % 60}m" else ""} ahead of you"
            else -> "${-diffMin / 60}h${if (-diffMin % 60 != 0) " ${-diffMin % 60}m" else ""} behind you"
        }
        val name = listOfNotNull(g.get("name")?.asString, g.get("country")?.asString).distinct().joinToString(", ")
        return "$name: ${there.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a", java.util.Locale.ENGLISH))} " +
                "(${zone.id}) — $diff. Your time: ${here.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))}."
    }

    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {
        val root = getJson("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=4&srsearch=${enc(query)}")
        root.getAsJsonObject("query")?.getAsJsonArray("search")?.map {
            val o = it.asJsonObject
            val title = o.get("title").asString
            SearchResult(title, "https://en.wikipedia.org/wiki/${enc(title.replace(' ', '_'))}",
                WebParsers.stripTags(o.get("snippet")?.asString.orEmpty()))
        }.orEmpty()
    }.getOrDefault(emptyList())

    private suspend fun readWebpage(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "ERROR: give a full https:// address"
        val (code, html) = runCatching { getText(url) }.getOrElse { return "ERROR: couldn't open that page (${it.message})" }
        if (code !in 200..299) return "ERROR: the page answered HTTP $code"
        val text = WebParsers.htmlToText(html)
        if (text.length < 40) return "ERROR: the page has no readable text (it may need JavaScript)."
        val title = WebParsers.pageTitle(html)?.let { "Title: $it\n" }.orEmpty()
        return title + text.take(8_000) + if (text.length > 8_000) "\n[…cut]" else ""
    }

    // ===== Location helpers =====

    private suspend fun here(ctx: Context): Location {
        if (!LocationHelper.hasPermission(ctx)) {
            throw SkillException("Location permission is off. Ask the user to allow Location in Faiza AI → Settings → Phone access.")
        }
        return LocationHelper.best(ctx, 8_000)
            ?: throw SkillException("couldn't get the phone's location. Ask the user to turn on Location (GPS).")
    }

    /** First match for a place name, preferring results near [near]. */
    private suspend fun geocode(name: String, near: Location?): Pair<String, Pair<Double, Double>>? {
        val view = near?.let {
            val d = 0.5
            "&viewbox=${it.longitude - d},${it.latitude + d},${it.longitude + d},${it.latitude - d}"
        }.orEmpty()
        val root = getJson("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=${enc(name)}$view")
        val place = WebParsers.parseNominatim(root.getAsJsonArray("items") ?: JsonArray()).firstOrNull() ?: return null
        return place.name to (place.lat to place.lon)
    }

    // ===== Weather =====

    private suspend fun weather(ctx: Context, place: String?): String {
        val (label, lat, lon) = if (place.isNullOrBlank()) {
            val loc = here(ctx)
            Triple("your area", loc.latitude, loc.longitude)
        } else {
            val g = getJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name=${enc(place)}")
                .getAsJsonArray("results")?.firstOrNull()?.asJsonObject
                ?: return "ERROR: couldn't find a place called \"$place\""
            Triple(
                listOfNotNull(g.get("name")?.asString, g.get("country")?.asString).joinToString(", "),
                g.get("latitude").asDouble, g.get("longitude").asDouble
            )
        }
        val root = getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=3" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
        )
        return WebParsers.formatWeather(label, root)
    }

    /** "29°" and "Partly cloudy" for the home card, from the last known location (cached 30 min). */
    @Volatile private var homeWeather: Triple<Long, String, String>? = null

    suspend fun homeWeatherSummary(ctx: Context): Pair<String, String>? {
        homeWeather?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.let { return it.second to it.third }
        val loc = LocationHelper.lastKnown(ctx) ?: return null
        return runCatching {
            val root = getJson(
                "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
                        "&current=temperature_2m,weather_code&timezone=auto"
            )
            val cur = root.getAsJsonObject("current")
            val temp = "${Math.round(cur.get("temperature_2m").asDouble)}°"
            val sky = WebParsers.weatherText(cur.get("weather_code").asDouble.toInt()).replaceFirstChar { it.uppercase() }
            homeWeather = Triple(System.currentTimeMillis(), temp, sky)
            temp to sky
        }.getOrNull()
    }

    // ===== Nearby places =====

    private suspend fun findNearby(ctx: Context, what: String, radiusKm: Int): String {
        val loc = here(ctx)
        val mapsKey = prefs.mapsApiKey.trim()
        var note = ""
        if (mapsKey.isNotEmpty()) {
            try {
                val root = postJson(
                    "https://places.googleapis.com/v1/places:searchText",
                    JsonObject().apply {
                        addProperty("textQuery", what)
                        addProperty("maxResultCount", 8)
                        add("locationBias", JsonObject().apply {
                            add("circle", JsonObject().apply {
                                add("center", JsonObject().apply {
                                    addProperty("latitude", loc.latitude)
                                    addProperty("longitude", loc.longitude)
                                })
                                addProperty("radius", (radiusKm * 1000).coerceAtMost(50_000).toDouble())
                            })
                        })
                    },
                    mapOf(
                        "X-Goog-Api-Key" to mapsKey,
                        "X-Goog-FieldMask" to "places.displayName,places.formattedAddress,places.rating," +
                                "places.userRatingCount,places.location,places.currentOpeningHours.openNow"
                    )
                )
                val places = WebParsers.parseGooglePlaces(root)
                if (places.isNotEmpty()) {
                    return WebParsers.formatPlaces(places, loc.latitude, loc.longitude) +
                            "\nTell the user the best 2–3 briefly. To go to one, call navigate_to with its name and address."
                }
            } catch (e: SkillException) {
                note = "\n(Google Maps didn't answer: ${e.message}. Used OpenStreetMap instead.)"
            }
        }
        for (km in listOf(radiusKm, maxOf(radiusKm * 3, 10)).distinct()) {
            val d = km / 111.0
            val root = getJson(
                "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=15&bounded=1&q=${enc(what)}" +
                        "&viewbox=${loc.longitude - d},${loc.latitude + d},${loc.longitude + d},${loc.latitude - d}"
            )
            val places = WebParsers.parseNominatim(root.getAsJsonArray("items") ?: JsonArray())
            if (places.isNotEmpty()) {
                return WebParsers.formatPlaces(places, loc.latitude, loc.longitude) + note +
                        "\nTell the user the nearest 2–3 briefly. To go to one, call navigate_to with its name and address."
            }
        }
        return "Nothing called \"$what\" was found within ${maxOf(radiusKm * 3, 10)} km on the map.$note " +
                "Try a simpler word (e.g. \"restaurant\", \"pharmacy\")."
    }

    // ===== Travel time =====

    private suspend fun travelTime(ctx: Context, destination: String, mode: String): String {
        val loc = here(ctx)
        val mapsKey = prefs.mapsApiKey.trim()
        if (mapsKey.isNotEmpty()) {
            try {
                val travelMode = when (mode) { "walking" -> "WALK"; "cycling" -> "BICYCLE"; "transit" -> "TRANSIT"; else -> "DRIVE" }
                val root = postJson(
                    "https://routes.googleapis.com/directions/v2:computeRoutes",
                    JsonObject().apply {
                        add("origin", JsonObject().apply {
                            add("location", JsonObject().apply {
                                add("latLng", JsonObject().apply {
                                    addProperty("latitude", loc.latitude)
                                    addProperty("longitude", loc.longitude)
                                })
                            })
                        })
                        add("destination", JsonObject().apply { addProperty("address", destination) })
                        addProperty("travelMode", travelMode)
                        if (travelMode == "DRIVE") addProperty("routingPreference", "TRAFFIC_AWARE")
                    },
                    mapOf(
                        "X-Goog-Api-Key" to mapsKey,
                        "X-Goog-FieldMask" to "routes.duration,routes.staticDuration,routes.distanceMeters"
                    )
                )
                val route = root.getAsJsonArray("routes")?.firstOrNull()?.asJsonObject
                if (route != null) {
                    val secs = route.get("duration")?.asString?.removeSuffix("s")?.toDoubleOrNull()?.toLong() ?: 0
                    val usual = route.get("staticDuration")?.asString?.removeSuffix("s")?.toDoubleOrNull()?.toLong()
                    val meters = route.get("distanceMeters")?.asDouble ?: 0.0
                    val traffic = if (travelMode == "DRIVE" && usual != null && secs - usual > 300)
                        " Traffic adds about ${WebParsers.formatDuration(secs - usual)}." else ""
                    return "To $destination by $mode: about ${WebParsers.formatDuration(secs)} " +
                            "(${WebParsers.formatDistance(meters)}) right now.$traffic"
                }
            } catch (_: SkillException) {
                // Fall back to the free route below.
            }
        }
        val (name, target) = geocode(destination, loc) ?: return "ERROR: couldn't find \"$destination\" on the map."
        val straight = WebParsers.distanceMeters(loc.latitude, loc.longitude, target.first, target.second)
        if (mode == "walking" || mode == "cycling") {
            val speed = if (mode == "walking") 4.8 else 15.0 // km/h
            val secs = (straight * 1.3 / 1000 / speed * 3600).toLong()
            return "To $name on foot/bike: roughly ${WebParsers.formatDuration(secs)} " +
                    "(about ${WebParsers.formatDistance(straight * 1.3)}). This is an estimate."
        }
        val osrm = runCatching {
            getJson("https://router.project-osrm.org/route/v1/driving/" +
                    "${loc.longitude},${loc.latitude};${target.second},${target.first}?overview=false")
        }.getOrNull()
        val r = osrm?.getAsJsonArray("routes")?.firstOrNull()?.asJsonObject
        return if (r != null) {
            "To $name by car: about ${WebParsers.formatDuration(r.get("duration").asDouble.toLong())} " +
                    "(${WebParsers.formatDistance(r.get("distance").asDouble)}) without traffic. " +
                    "For live traffic the user can add a Google Maps key in Settings, or say \"navigate\"."
        } else {
            "$name is about ${WebParsers.formatDistance(straight)} away in a straight line."
        }
    }

    // ===== Image generation =====

    private suspend fun generateImage(ctx: Context, prompt: String, shapeArg: String?): String {
        val shape = when (shapeArg) {
            "portrait" -> WebParsers.Shape.PORTRAIT
            "landscape" -> WebParsers.Shape.LANDSCAPE
            else -> WebParsers.Shape.SQUARE
        }
        val url = WebParsers.pollinationsUrl(prompt, shape, Random.nextInt(1, 1_000_000))
        val request = Request.Builder().url(url).header("User-Agent", APP_UA).apply {
            prefs.pollinationsToken.trim().takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") }
        }.get().build()
        val (code, bytes) = withContext(Dispatchers.IO) {
            runCatching {
                imageHttp.newCall(request).execute().use { r -> r.code to (if (r.isSuccessful) r.body?.bytes() else null) }
            }.getOrDefault(0 to null)
        }
        if (bytes == null) {
            return if (code == 0) "ERROR: the image service didn't answer in time. Try again in a minute."
            else "ERROR: the image service answered HTTP $code. Try again or with a simpler description."
        }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return "ERROR: the image service sent something that isn't a picture. Try again."
        val ext = if (opts.outMimeType == "image/png") "png" else "jpg"
        val mime = if (ext == "png") "image/png" else "image/jpeg"

        val uri = withContext(Dispatchers.IO) { save(ctx, bytes, ext, mime) }
            ?: return "ERROR: made the picture but couldn't save it."
        ctx.startActivity(
            Intent(ctx, ImageViewerActivity::class.java)
                .setData(uri)
                .putExtra(ImageViewerActivity.EXTRA_PROMPT, prompt)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        val where = if (Build.VERSION.SDK_INT >= 29) "the Gallery (Pictures/Faiza AI)" else "Faiza's pictures folder"
        return "OK: the picture is on the screen now and saved to $where."
    }

    private fun save(ctx: Context, bytes: ByteArray, ext: String, mime: String): Uri? = runCatching {
        val name = "faiza_${System.currentTimeMillis()}.$ext"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Faiza AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Faiza AI").apply { mkdirs() }
            val file = File(dir, name).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        }
    }.getOrNull()
}
