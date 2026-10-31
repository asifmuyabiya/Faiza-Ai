import sys
sys.path.insert(0, r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools')
from settings_rows import row, apply

root = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
base = root + r'\java\com\faiza\ai'
s = ''


def load(p):
    global s
    s = open(p, encoding='utf-8').read()


def save(p):
    open(p, 'w', encoding='utf-8').write(s)


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# ---- Prefs ----
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    /** Weather heads-ups: morning summary, rain coming, very hot. */''', '''    // ====== Research agent ======
    /** Its own Gemini key (empty = use the main Gemini key). */
    var researchApiKey: String
        get() = prefs.getString("research_key", "") ?: ""
        set(value) = prefs.edit().putString("research_key", value.trim()).apply()
    /** Its own model (empty = newest available, picked automatically). */
    var researchModel: String
        get() = prefs.getString("research_model", "") ?: ""
        set(value) = prefs.edit().putString("research_model", value.trim()).apply()
    /** "day|model" — the automatic pick, refreshed daily. */
    var researchModelAuto: String
        get() = prefs.getString("research_model_auto", "") ?: ""
        set(value) = prefs.edit().putString("research_model_auto", value).apply()
    var researchDepth: String
        get() = prefs.getString("research_depth", "DEEP") ?: "DEEP"
        set(value) = prefs.edit().putString("research_depth", value).apply()

    /** Weather heads-ups: morning summary, rain coming, very hot. */''')
save(p)

# ---- Voice tools ----
p = base + r'\ai\web\WebSkills.kt'
load(p)
rep('''            "deep_research", "🔬", "Deep research", "\\"Research the best budget phones\\"",
            "Research a topic properly: many web searches, reads the best pages, and writes a report with sources " +
                    "(saved as a file). Takes about a minute — tell the user first. Then summarise it in a few sentences.",
            listOf(ToolParam("topic", ParamType.STRING, "What to research, in detail"))
        ) { ctx, args -> deepResearch(ctx, args.require("topic")) },''', '''            "deep_research", "🔬", "Deep research", "\\"Research the best budget phones\\"",
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
            "latest_news", "📰", "Latest news", "\\"সৌদি আরবে এখন কী হচ্ছে?\\"",
            "The newest headlines about a place, person or topic from Google News, newest first, with how long ago each " +
                    "was published. Use for 'latest news / what's happening in X / খবর'. For a place abroad, also call " +
                    "world_time so you can say what time it is there. Summarise the headlines in your own words.",
            listOf(
                ToolParam("topic", ParamType.STRING, "Place, person or topic, e.g. \\"Saudi Arabia\\", \\"Bangladesh\\""),
                ToolParam("bangla", ParamType.BOOLEAN, "true for Bangla news sources", required = false)
            )
        ) { _, args -> latestNews(args.require("topic"), args.bool("bangla")) },

        Skill(
            "world_time", "🕐", "Time anywhere", "\\"সৌদিতে এখন কয়টা বাজে?\\"",
            "The current local date and time in any city or country, and the difference from the user's own time.",
            listOf(ToolParam("place", ParamType.STRING, "City or country"))
        ) { _, args -> worldTime(args.require("place")) },''')
rep('''    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {''', '''    private suspend fun latestNews(topic: String, bangla: Boolean): String {
        val (code, xml) = getText(ResearchParsers.newsUrl(topic, bangla || topic.any { it.code in 0x0980..0x09FF }), maxBytes = 800_000)
        if (code !in 200..299) return "ERROR: the news service answered HTTP $code"
        val items = ResearchParsers.parseNewsRss(xml, 10)
        if (items.isEmpty()) return "No recent news found for \\"$topic\\"."
        val now = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.ENGLISH)
        return "Latest news about $topic (newest first):\\n" + items.joinToString("\\n") { n ->
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
            ?: return "ERROR: couldn't find \\"$place\\""
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

    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {''')
save(p)

# ---- Prompt: when to use which ----
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''            - INTERNET: for news, scores, prices, recent events or facts you aren't sure of, call search_web and answer''',
    '''            - NEWS & RESEARCH: "latest news / what's happening in <place>" → latest_news (and world_time for a place abroad,
              so you can say the local time there). Anything that needs real digging, comparisons or a full summary
              (e.g. "Bangladesh-এর আজকের পুরো সারাংশ") → deep_research (runs in the background; you'll get the result).
              Films/series: give information and LEGAL places to watch only (Netflix, Prime, Chorki, Hoichoi, cinemas,
              official YouTube channels) — never piracy or free-download sites like MLWBD; say so kindly if asked.
            - INTERNET: for news, scores, prices, recent events or facts you aren't sure of, call search_web and answer''')
save(p)

# ---- Service: result arrives ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''    /** A "tell me in N seconds" timer finished: say it (the notification is already shown). */''', '''    /** The research agent finished: let the live conversation tell the user (the notification is already shown). */
    fun deliverResearch(instruction: String) {
        live?.sendText("[RESEARCH RESULT] $instruction")
    }

    /** A "tell me in N seconds" timer finished: say it (the notification is already shown). */''')
save(p)

# ---- Side menu + Settings ----
p = base + r'\ui\DashboardActivity.kt'
load(p)
rep('''            Triple(R.drawable.ic_today, "Markets") { openRoom("markets") },''',
    '''            Triple(R.drawable.ic_globe, "Deep research") { startActivity(Intent(this, ResearchActivity::class.java)) },
            Triple(R.drawable.ic_today, "Markets") { openRoom("markets") },''')
save(p)
apply("rowSerpKey", [row("research", "ic_globe", "cyan", "Research agent",
                         "Its own Gemini model & key for deep research", "nav")])
p = base + r'\ui\SettingsActivity.kt'
load(p)
rep('''        findViewById<View>(R.id.rowAvatar).setOnClickListener {''', '''        findViewById<View>(R.id.rowResearch).setOnClickListener {
            startActivity(Intent(this, ResearchActivity::class.java))
        }
        findViewById<View>(R.id.rowAvatar).setOnClickListener {''')
save(p)

p = root + r'\AndroidManifest.xml'
load(p)
rep('''        <activity android:name=".ui.RulesActivity"''', '''        <activity android:name=".ui.ResearchActivity"
            android:launchMode="singleTop"
            android:theme="@style/Theme.FaizaAI"
            android:windowSoftInputMode="adjustResize" />
        <activity android:name=".ui.RulesActivity"''')
save(p)
print("ok")
