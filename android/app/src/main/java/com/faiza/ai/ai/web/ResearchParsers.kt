package com.faiza.ai.ai.web

/** A news headline from Google News RSS. */
data class NewsItem(val title: String, val source: String, val link: String, val pubDate: String)

/** Pure helpers for the research agent (unit-tested). */
object ResearchParsers {

    private val ITEM = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

    private fun tag(block: String, name: String): String =
        Regex("<$name[^>]*>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)
            ?.replace("<![CDATA[", "")?.replace("]]>", "")?.let { WebParsers.decodeEntities(it).trim() }.orEmpty()

    /** Google News RSS → headlines (newest first, as the feed gives them). */
    fun parseNewsRss(xml: String, max: Int = 12): List<NewsItem> =
        ITEM.findAll(xml).mapNotNull { m ->
            val b = m.groupValues[1]
            val source = tag(b, "source")
            var title = tag(b, "title")
            // Google appends " - Source" to titles.
            if (source.isNotEmpty() && title.endsWith(" - $source")) title = title.removeSuffix(" - $source")
            val link = tag(b, "link")
            if (title.isBlank() || link.isBlank()) null else NewsItem(title, source, link, tag(b, "pubDate"))
        }.take(max).toList()

    /** Google News RSS search URL; Bangla feed for Bangla queries / Bangladesh. */
    fun newsUrl(query: String, bangla: Boolean): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return if (bangla) "https://news.google.com/rss/search?q=$q&hl=bn&gl=BD&ceid=BD:bn"
        else "https://news.google.com/rss/search?q=$q&hl=en-US&gl=US&ceid=US:en"
    }

    /** Does the question want fresh news (so the agent also reads news feeds)? */
    fun looksLikeNews(q: String): Boolean {
        val t = q.lowercase()
        return listOf(
            "news", "latest", "today", "update", "breaking", "current", "now", "this week", "election", "war", "price",
            "খবর", "সংবাদ", "আজ", "এখন", "সর্বশেষ", "আপডেট", "বর্তমান", "समाचार", "खबर", "आज", "ताज़ा"
        ).any { t.contains(it) }
    }

    /**
     * The newest, strongest Gemini model on this key: highest version, Pro over Flash over Lite; skips TTS/image/
     * live/embedding models. e.g. ["gemini-2.5-flash","gemini-3.1-pro-preview","gemini-3.8-flash"] → "gemini-3.8-flash"
     * only if no 3.8 pro exists; a 3.8 pro would win.
     */
    fun bestGeminiModel(models: List<String>): String? {
        val skip = listOf("tts", "image", "live", "embed", "audio", "robotics", "aqa", "native", "computer", "learnlm", "gemma")
        val vRe = Regex("gemini-(\\d+(?:\\.\\d+)?)")
        return models.map { it.removePrefix("models/") }
            .filter { m -> m.startsWith("gemini") && skip.none { m.contains(it) } }
            .mapNotNull { m -> vRe.find(m)?.groupValues?.get(1)?.toDoubleOrNull()?.let { m to it } }
            .maxWithOrNull(
                compareBy<Pair<String, Double>> { it.second }
                    .thenBy { (m, _) -> when { m.contains("pro") -> 3; m.contains("lite") -> 1; m.contains("flash") -> 2; else -> 0 } }
                    .thenBy { (m, _) -> if (m.contains("preview") || m.contains("exp")) 0 else 1 }
            )?.first
    }

    /** Lines of a planner reply → clean search queries. */
    fun queries(text: String, max: Int): List<String> =
        text.lines().map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim('"', ' ') }
            .filter { it.length > 2 && !it.startsWith("DONE", ignoreCase = true) && !it.endsWith(":") }
            .distinct().take(max)
}
