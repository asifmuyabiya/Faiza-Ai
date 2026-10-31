import os
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


patch(J + "util/PrefsManager.kt", [
 ("""    var pollinationsToken: String""", """    var braveApiKey: String
        get() = prefs.getString("brave_api_key", "") ?: ""
        set(value) = prefs.edit().putString("brave_api_key", value.trim()).apply()

    var serpApiKey: String
        get() = prefs.getString("serp_api_key", "") ?: ""
        set(value) = prefs.edit().putString("serp_api_key", value.trim()).apply()

    /** When a Gemini model hits its free limit, quietly try another one (MAYA sub-agent brain). */
    var isAutoSwitchModels: Boolean
        get() = prefs.getBoolean("auto_switch_models", true)
        set(value) = prefs.edit().putBoolean("auto_switch_models", value).apply()

    var pollinationsToken: String"""),
])

patch(J + "ui/ProfileSections.kt", [
 ("""        nav(R.id.rowImageToken, R.id.subImageToken,""", """        nav(R.id.rowBraveKey, R.id.subBraveKey, { keyStatus(prefs.braveApiKey, "") }, "Optional · backup search") {
            editText(
                "Brave Search key", prefs.braveApiKey, "BSA…", secret = true,
                help = "Optional. Used when the other searches don't answer. Free plan at brave.com/search/api.",
                helpUrl = "https://brave.com/search/api/"
            ) { prefs.braveApiKey = it }
        }
        nav(R.id.rowSerpKey, R.id.subSerpKey, { keyStatus(prefs.serpApiKey, "") }, "Optional · Google results") {
            editText(
                "SerpAPI key", prefs.serpApiKey, "key", secret = true,
                help = "Optional. Real Google results (with quick answers). Free plan at serpapi.com.",
                helpUrl = "https://serpapi.com/manage-api-key"
            ) { prefs.serpApiKey = it }
        }
        nav(R.id.rowImageToken, R.id.subImageToken,"""),
])

patch(J + "ui/SettingsActivity.kt", [
 ("""        findViewById<View>(R.id.rowLiveModel).setOnClickListener { showLiveModelDialog() }""",
  """        findViewById<View>(R.id.rowLiveModel).setOnClickListener { showLiveModelDialog() }
        val swAuto = findViewById<SwitchMaterial>(R.id.swAutoSwitch)
        swAuto.isChecked = prefs.isAutoSwitchModels
        swAuto.setOnCheckedChangeListener { _, on -> prefs.isAutoSwitchModels = on }
        findViewById<View>(R.id.rowAutoSwitch).setOnClickListener { swAuto.toggle() }
        findViewById<View>(R.id.rowShareBackup).setOnClickListener { shareBackup() }"""),
 ("""    private fun writeBackup(uri: android.net.Uri) {""", """    /** MAYA "Share backup": send the file straight to WhatsApp, Drive, email… */
    private fun shareBackup() {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
                    val file = java.io.File(cacheDir, "faiza-backup-$date.json")
                    file.writeText(BackupManager.export(prefs))
                    androidx.core.content.FileProvider.getUriForFile(this@SettingsActivity, "$packageName.files", file)
                }.getOrNull()
            }
            if (uri == null) {
                Toast.makeText(this@SettingsActivity, "Couldn't prepare the backup", Toast.LENGTH_LONG).show()
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Share backup"))
        }
    }

    private fun refreshBackupCounts() {
        val c = BackupManager.currentCounts(prefs)
        findViewById<TextView>(R.id.subSaveBackup).text =
            "${c.facts} memories · ${c.lines} chats · ${c.contacts} contacts"
    }

    private fun writeBackup(uri: android.net.Uri) {"""),
 ("""        refreshDefaultAssistant()
    }""", """        refreshDefaultAssistant()
        refreshBackupCounts()
    }"""),
])

# ---- Web search: Brave and SerpAPI as extra sources ----
patch(J + "ai/web/WebSkills.kt", [
 ("""        val results = runCatching {
            val (code, html) = getText("https://html.duckduckgo.com/html/?q=${enc(query)}&kl=wt-wt")""",
  """        // Optional keyed searches (MAYA Optional): SerpAPI (Google), then Brave.
        serpApi(query)?.let { return it }
        brave(query)?.let { return it }
        val results = runCatching {
            val (code, html) = getText("https://html.duckduckgo.com/html/?q=${enc(query)}&kl=wt-wt")"""),
 ("""    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {""",
  """    private val ANSWER_NOTE = "\\nAnswer the user briefly from these results and mention the source site."

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
            else (quick?.let { "Quick answer: $it\\n" } ?: "") + WebParsers.formatResults(results) + ANSWER_NOTE
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

    private suspend fun wikipedia(query: String): List<SearchResult> = runCatching {"""),
])

# ---- Gemini: switch model when the free limit is hit ----
patch(J + "ai/provider/GeminiProvider.kt", [
 ("""    override suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply =
        withContext(Dispatchers.IO) {""",
  """    /**
     * MAYA's "sub-agent brain": when this model is out of free quota (429) or overloaded (5xx), quietly try
     * other Gemini Flash models with the same key, newest first.
     */
    override suspend fun chat(system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply {
        try {
            return chatWith(model, system, history, tools)
        } catch (e: AIProviderException) {
            if (!switchable(e) || !com.faiza.ai.FaizaApp.instance.prefs.isAutoSwitchModels) throw e
            for (other in alternatives()) {
                try {
                    return chatWith(other, system, history, tools)
                } catch (e2: AIProviderException) {
                    if (!switchable(e2)) break
                }
            }
            throw e
        }
    }

    private fun switchable(e: AIProviderException) = e.httpCode == 429 || (e.httpCode ?: 0) >= 500

    @Volatile private var fallbackModels: List<String>? = null

    private suspend fun alternatives(): List<String> {
        fallbackModels?.let { return it }
        val list = runCatching { listModels() }.getOrDefault(emptyList())
            .filter { it != model && it.contains("flash") && !it.contains("exp") }
            .sortedDescending()
            .take(4)
        return list.also { fallbackModels = it }
    }

    private suspend fun chatWith(model: String, system: String, history: List<ChatTurn>, tools: List<ToolSpec>): AIReply =
        withContext(Dispatchers.IO) {"""),
])
print("keys + backup share + model switch done")
