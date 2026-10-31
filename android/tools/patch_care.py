base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'
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
rep('''    /** The user's own rules for her, one per line ("Faiza Rules"). */''', '''    /** Weather heads-ups: morning summary, rain coming, very hot. */
    var isWeatherAlerts: Boolean
        get() = prefs.getBoolean("weather_alerts", true)
        set(value) = prefs.edit().putBoolean("weather_alerts", value).apply()
    /** Weather alerts already said (dayKey-type), so each is said once. */
    var weatherSaidKeys: String
        get() = prefs.getString("weather_said", "") ?: ""
        set(value) = prefs.edit().putString("weather_said", value).apply()
    /** Meal-time check-ins (four daily routines). */
    var isMealReminders: Boolean
        get() = prefs.getBoolean("meal_reminders", false)
        set(value) = prefs.edit().putBoolean("meal_reminders", value).apply()

    /** The user's own rules for her, one per line ("Faiza Rules"). */''')
save(p)

# ---- Service: weather check in the existing minute loop ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''                runCatching {
                    com.faiza.ai.ai.modes.ModeCenter.refreshPrayerTimes(this@FloatingOrbService)
                    com.faiza.ai.ai.modes.ModeCenter.checkTransition(this@FloatingOrbService)
                }''', '''                runCatching {
                    com.faiza.ai.ai.modes.ModeCenter.refreshPrayerTimes(this@FloatingOrbService)
                    com.faiza.ai.ai.modes.ModeCenter.checkTransition(this@FloatingOrbService)
                }
                runCatching { com.faiza.ai.ai.web.WeatherAlerts.tick(this@FloatingOrbService) }''')
save(p)

# ---- Delete guard: important documents get their own warning ----
p = base + r'\ai\guardian\DeleteGuard.kt'
load(p)
rep('''    enum class Level { NONE, NORMAL, IMPORTANT, DUPLICATE }''',
    '''    enum class Level { NONE, NORMAL, IMPORTANT, IMPORTANT_FILE, DUPLICATE }''')
rep('''    /** Pure: is this a delete confirmation, and how serious? [buttons] = the short clickable texts. */''', '''    /** Names that usually mean an important document (ID, certificates, money, work). */
    private val IMPORTANT_FILE_WORDS = listOf(
        "passport", "nid", "national id", "birth certificate", "certificate", "marksheet", "transcript", "cv", "resume",
        "contract", "agreement", "deed", "invoice", "receipt", "bank", "statement", "salary", "tax", "license", "licence",
        "visa", "policy", "insurance", "prescription", "medical report", "backup", "wallet", "seed",
        ".pdf", ".docx", ".doc", ".xlsx", ".xls", ".pptx", ".apk", ".key", ".db",
        "পাসপোর্ট", "আইডি", "জাতীয় পরিচয়", "সার্টিফিকেট", "সনদ", "দলিল", "চুক্তি", "রসিদ", "ব্যাংক", "প্রেসক্রিপশন",
        "पासपोर्ट", "प्रमाणपत्र", "आधार", "बैंक"
    )

    /** Pure: is this a delete confirmation, and how serious? [buttons] = the short clickable texts. */''')
rep('''        if (DUPLICATE_WORDS.any { all.contains(it) }) return Level.DUPLICATE''',
    '''        if (DUPLICATE_WORDS.any { all.contains(it) }) return Level.DUPLICATE
        if (IMPORTANT_FILE_WORDS.any { w -> Regex("(^|[^\\\\p{L}])" + Regex.escape(w) + "([^\\\\p{L}]|$)").containsMatchIn(all) })
            return Level.IMPORTANT_FILE''')
rep('''    fun line(level: Level, b: PhraseBase): String = when (level) {''', '''    fun line(level: Level, b: PhraseBase): String = when (level) {
        Level.IMPORTANT_FILE -> when (b) {
            PhraseBase.BENGALI -> "বস, এই ফাইলটা তো অনেক গুরুত্বপূর্ণ মনে হচ্ছে! ডিলিট করার আগে আরেকবার ভালো করে দেখে নিন — দরকার হলে আগে একটা কপি রেখে দিন।"
            PhraseBase.HINDI -> "बॉस, ये फ़ाइल बहुत ज़रूरी लग रही है! डिलीट करने से पहले एक बार अच्छे से देख लीजिए।"
            PhraseBase.ENGLISH -> "Boss, this file looks important! Take one more careful look before deleting — keep a copy if you need it."
        }''')
save(p)
print("ok")
