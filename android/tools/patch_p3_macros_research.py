import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


# ---- Built-in "trained tasks" (MAYA Memories tab) ----
patch(J + "ai/macros/Macros.kt", [
 ("""    val runCount: Int = 0,
    val lastRunAt: Long = 0L
) {""", """    val runCount: Int = 0,
    val lastRunAt: Long = 0L,
    /** Comes with the app (MAYA's built-in trained tasks). */
    val builtIn: Boolean = false
) {"""),
])

patch(J + "ai/macros/MacroRunner.kt", [
 ("""    fun noteTap(e: FaizaAccessibilityService.ScreenElement, ctx: Context) {""",
  """    /** MAYA ships trained tasks like "chatgpt se poochho" / "gemini image": add ours once. */
    fun seedBuiltIns() {
        val prefs = FaizaApp.instance.prefs
        if (prefs.isBuiltInMacrosSeeded) return
        fun ask(app: String, name: String, prompt: String, template: String?) = Macro(
            name = name, builtIn = true,
            steps = listOf(
                MacroStep("open_app", com.google.gson.JsonObject().apply { addProperty("app_name", app) }.toString(), "Open $app"),
                MacroStep("wait", "{\\"seconds\\":2}", "Wait 2 s"),
                MacroStep(
                    "type_text",
                    com.google.gson.JsonObject().apply {
                        addProperty("text", ""); addProperty("submit", false)
                        template?.let { addProperty("template", it) }
                    }.toString(),
                    "Type your request in $app", askEachTime = true, prompt = prompt
                ),
                MacroStep("tap", description = "Tap “Send”", target = "Send")
            )
        )
        val builtIns = listOf(
            ask("ChatGPT", "Ask ChatGPT", "What should I ask ChatGPT?", null),
            ask("Gemini", "Ask Gemini", "What should I ask Gemini?", null),
            ask("ChatGPT", "ChatGPT image", "What picture should ChatGPT make?", "Create an image: {input}"),
            ask("Gemini", "Gemini image", "What picture should Gemini make?", "Generate an image: {input}")
        )
        val existing = all()
        saveAll(existing + builtIns.filter { b -> existing.none { MacroStore.normalize(it.name) == MacroStore.normalize(b.name) } })
        prefs.isBuiltInMacrosSeeded = true
    }

    fun noteTap(e: FaizaAccessibilityService.ScreenElement, ctx: Context) {"""),
 ("""                    "type_text" -> {
                        val text = if (step.askEachTime) inputs.getOrNull(inputIndex++) else null
                        type(step, text ?: step.args().str("text").orEmpty())
                    }""", """                    "type_text" -> {
                        val raw = (if (step.askEachTime) inputs.getOrNull(inputIndex++) else null) ?: step.args().str("text").orEmpty()
                        // Built-in tasks wrap the user's words, e.g. "Create an image: {input}".
                        val template = step.args().str("template")
                        type(step, template?.replace("{input}", raw) ?: raw)
                    }"""),
])

patch(J + "util/PrefsManager.kt", [
 ("""    /** Recorded macros (video 33:47), JSON list. */""", """    var isBuiltInMacrosSeeded: Boolean
        get() = prefs.getBoolean("builtin_macros_seeded", false)
        set(value) = prefs.edit().putBoolean("builtin_macros_seeded", value).apply()

    /** Recorded macros (video 33:47), JSON list. */"""),
])

patch(J + "FaizaApp.kt", [
 ("""        runCatching { com.faiza.ai.ai.routines.RoutineCenter.rescheduleAll(this) }""",
  """        runCatching { com.faiza.ai.ai.routines.RoutineCenter.rescheduleAll(this) }
        runCatching { com.faiza.ai.ai.macros.MacroCenter.seedBuiltIns() }"""),
])

patch(J + "ui/MacrosActivity.kt", [
 ("""        return "$steps · $runs\"""", """        return "$steps · $runs" + if (m.builtIn) " · built-in" else \"\""""),
])

# ---- Deep research (MAYA Optional: "Deep Research runs a dozen searches per report") ----
patch(J + "ai/files/FileSkills.kt", [
 ("""    private suspend fun createFile(ctx: Context, name: String, content: String, format: String): String""",
  """    internal suspend fun createFile(ctx: Context, name: String, content: String, format: String): String"""),
])

patch(J + "ai/web/WebSkills.kt", [
 ("""        Skill(
            "read_webpage",""", """        Skill(
            "deep_research", "🔬", "Deep research", "\\"Research the best budget phones\\"",
            "Research a topic properly: many web searches, reads the best pages, and writes a report with sources " +
                    "(saved as a file). Takes about a minute — tell the user first. Then summarise it in a few sentences.",
            listOf(ToolParam("topic", ParamType.STRING, "What to research, in detail"))
        ) { ctx, args -> deepResearch(ctx, args.require("topic")) },

        Skill(
            "read_webpage","""),
 ("""    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {""",
  """    /** Results from every free/keyed source we have, best first, without duplicates. */
    private suspend fun collectResults(query: String): List<SearchResult> {
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
                if (code in 200..299) "SOURCE: ${r.title} (${r.url})\\n" + WebParsers.htmlToText(html).take(3_500) else null
            }.getOrNull()
        }
        val snippets = results.take(20).joinToString("\\n") { "- ${it.title} (${it.url}): ${it.snippet.take(200)}" }
        // 4. Write the report.
        val report = provider.chat(
            "You write clear research reports for a phone user. Use only the material given; say when something is " +
                    "uncertain. Structure: short summary, key findings (bullets), details, and a Sources list with links. " +
                    "Write in the same language as the topic. Markdown is fine.",
            listOf(com.faiza.ai.ai.provider.ChatTurn.User(
                "TOPIC: $topic\\n\\nSEARCH RESULTS:\\n$snippets\\n\\nPAGES:\\n${pages.joinToString("\\n\\n")}".take(60_000)
            )),
            emptyList()
        ).text?.trim().orEmpty()
        if (report.isEmpty()) return "ERROR: couldn't write the report."
        val saved = runCatching {
            com.faiza.ai.ai.files.FileSkills.createFile(ctx, "Research - ${topic.take(40)}", report, "md")
        }.getOrElse { "(couldn't save the file)" }
        return "REPORT (${queries.size} searches, ${pages.size} pages read):\\n${report.take(2_500)}\\n\\n$saved " +
                "Tell the user the main findings in a few sentences."
    }

    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {"""),
])

patch(J + "ai/FaizaAgent.kt", [
 ("""                "generate_image" -> IMAGE_TIMEOUT_MS""", """                "generate_image" -> IMAGE_TIMEOUT_MS
                "deep_research" -> MACRO_TIMEOUT_MS"""),
])
print("built-in macros + deep research done")
