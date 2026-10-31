import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- prefs ----------
edit('util/PrefsManager.kt', '''    var codeGeminiModel: String''', '''    /** Code Studio's own Gemini key (empty = Faiza's main key). */
    var codeGeminiKey: String
        get() = prefs.getString("code_gemini_key", "") ?: ""
        set(value) = prefs.edit().putString("code_gemini_key", value.trim()).apply()
    /** "day|model": the newest Gemini model for Code Studio, checked once a day. */
    var codeGeminiModelAuto: String
        get() = prefs.getString("code_gemini_model_auto", "") ?: ""
        set(value) = prefs.edit().putString("code_gemini_model_auto", value).apply()
    var codeGeminiModel: String''')

# ---------- CodeAgent: own key + newest model ----------
p = 'ai/code/CodeAgent.kt'
edit(p, '''    private fun geminiKey(): String = prefs.researchApiKey.trim().ifBlank { prefs.apiKeyFor(ProviderType.GEMINI).orEmpty().trim() }''',
     '''    /** Code Studio's own key if set, else Faiza's main Gemini key. */
    fun geminiKey(): String = prefs.codeGeminiKey.trim().ifBlank { prefs.apiKeyFor(ProviderType.GEMINI).orEmpty().trim() }
    fun hasOwnGeminiKey(): Boolean = prefs.codeGeminiKey.isNotBlank()

    /** The chosen model, or the newest Gemini model this key can use (checked once a day). */
    suspend fun geminiModel(): String {
        prefs.codeGeminiModel.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val today = (System.currentTimeMillis() / 86_400_000L).toString()
        val cached = prefs.codeGeminiModelAuto
        if (cached.startsWith("$today|")) return cached.substringAfter('|')
        val best = runCatching { ResearchParsers.bestGeminiModel(GeminiProvider(geminiKey(), "").listModels()) }.getOrNull()
            ?: ProviderType.GEMINI.defaultModel
        prefs.codeGeminiModelAuto = "$today|$best"
        return best
    }''')
edit(p, '''        val m = prefs.codeGeminiModel.trim().ifEmpty {
            runCatching { ResearchParsers.bestGeminiModel(GeminiProvider(geminiKey(), "").listModels()) }.getOrNull()
                ?: ProviderType.GEMINI.defaultModel
        }
        return GeminiProvider(geminiKey(), m, longClient, deepThinking = true) to m''', '''        val m = geminiModel()
        return GeminiProvider(geminiKey(), m, longClient, deepThinking = true) to m''')
edit(p, '''        geminiKey().isNotEmpty() -> "Gemini · " + prefs.codeGeminiModel.trim().ifEmpty { "best available" }''',
     '''        geminiKey().isNotEmpty() -> "Gemini · " + prefs.codeGeminiModel.trim().ifEmpty {
            prefs.codeGeminiModelAuto.substringAfter('|', "").ifBlank { "newest" } + " (auto)"
        }''')

# ---------- create_file: real code file types ----------
p = 'ai/files/FileSkills.kt'
edit(p, '''        val ext = if (format in setOf("txt", "csv", "md")) format else "txt"''',
     '''        val ext = if (format in setOf("txt", "csv", "md", "html", "css", "js", "py", "json", "xml", "kt", "java")) format else "txt"''')
edit(p, '''                ToolParam("format", ParamType.STRING, "File type", required = false, enum = listOf("txt", "csv", "md"))''',
     '''                ToolParam("format", ParamType.STRING, "File type (html for web pages, py for Python…)", required = false,
                    enum = listOf("txt", "csv", "md", "html", "css", "js", "py", "json", "xml", "kt", "java"))''')
print('ok')
