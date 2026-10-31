base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = base + '\\' + p
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)


# --- prefs
edit(r'util\PrefsManager.kt', '''    // Code Studio
''', '''    // Gaming mode
    var gamingEnabled: Boolean
        get() = prefs.getBoolean("gaming_enabled", true)
        set(value) = prefs.edit().putBoolean("gaming_enabled", value).apply()
    /** Ask "গেমিং মোড অন করব?" when a game opens. */
    var gamingAsk: Boolean
        get() = prefs.getBoolean("gaming_ask", true)
        set(value) = prefs.edit().putBoolean("gaming_ask", value).apply()
    /** Start by itself in every game, without asking. */
    var gamingAutoStart: Boolean
        get() = prefs.getBoolean("gaming_auto_start", false)
        set(value) = prefs.edit().putBoolean("gaming_auto_start", value).apply()
    /** Turn off when the user leaves the game. */
    var gamingAutoOff: Boolean
        get() = prefs.getBoolean("gaming_auto_off", true)
        set(value) = prefs.edit().putBoolean("gaming_auto_off", value).apply()
    var gamingTalk: String
        get() = prefs.getString("gaming_talk", "CHATTY") ?: "CHATTY"
        set(value) = prefs.edit().putString("gaming_talk", value).apply()
    var gamingJokes: Boolean
        get() = prefs.getBoolean("gaming_jokes", true)
        set(value) = prefs.edit().putBoolean("gaming_jokes", value).apply()

    // Code Studio
''')

# --- accessibility screenshot size option
p = r'service\FaizaAccessibilityService.kt'
edit(p, '''    suspend fun screenshotJpeg(): String? {''', '''    suspend fun screenshotJpeg(maxSide: Int? = null, quality: Int = 70): String? {''')
edit(p, '''                            val scale = minOf(1f, 768f / bmp.width)''', '''                            val scale = if (maxSide != null) minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                            else minOf(1f, 768f / bmp.width)''')
edit(p, '''                            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)''', '''                            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)''')

# --- service
p = r'service\FloatingOrbService.kt'
edit(p, '''        FaizaAccessibilityService.foregroundListener = { pkg ->
            topPackage = pkg
            updateOrbVisibility()
        }''', '''        FaizaAccessibilityService.foregroundListener = { pkg ->
            topPackage = pkg
            updateOrbVisibility()
            runCatching { onForegroundForGaming(pkg) }
        }''')
edit(p, '''say a short goodbye and call go_to_sleep."
''', '''say a short goodbye and call go_to_sleep." +
                (com.faiza.ai.ai.game.GameMode.promptAddon()?.let { "\\n\\n$it" } ?: "")
''')
edit(p, '''                if (prefs.isProactiveChat && !guardMode && live?.isSpeaking != true''',
     '''                if (prefs.isProactiveChat && !guardMode && !com.faiza.ai.ai.game.GameMode.active.value && live?.isSpeaking != true''')
edit(p, '''        override fun onModelSpeech(text: String) {
            if (guardMode) return''', '''        override fun onModelSpeech(text: String) {
            lastModelSpeechAt = SystemClock.elapsedRealtime()
            if (guardMode) return''')
edit(p, '''            if (userTranscript.isEmpty()) lastUserSpeechStart = SystemClock.elapsedRealtime()''',
     '''            if (userTranscript.isEmpty()) lastUserSpeechStart = SystemClock.elapsedRealtime()
            lastUserSpeechAt = SystemClock.elapsedRealtime()''')
edit(p, '''    override fun onDestroy() {
''', '''    override fun onDestroy() {
        com.faiza.ai.ai.game.GameMode.end()
''')
edit(p, '''    // ---------------- Camera window (floating mini app) ----------------''', open(
    r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools\gaming_service.kt.txt', encoding='utf-8').read()
     + '''
    // ---------------- Camera window (floating mini app) ----------------''')

# --- register skill + prompt
p = r'ai\FaizaAgent.kt'
edit(p, '''        skills += com.faiza.ai.ai.code.CodeSkills.skills().filter { it.id !in disabled }''',
     '''        skills += com.faiza.ai.ai.code.CodeSkills.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.game.GameMode.skills().filter { it.id !in disabled }''')
edit(p, '''            - CODING:''', '''            - GAMES: "গেমিং মোড অন/অফ", "গেম খেলতে গাইড করো" → gaming_mode (on/off; guide = one quick tip).
            - CODING:''')
print("ok")
