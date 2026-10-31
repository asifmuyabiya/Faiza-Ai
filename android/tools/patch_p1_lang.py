import os, re
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai")

p = "ai/Persona.kt"
s = open(p, encoding="utf-8").read()
old_enum = s[s.index("enum class AppLanguage("):s.index("val FEMALE_VOICES")]
new_enum = '''/**
 * Languages she speaks (MAYA has a long list). Live voice handles them all through [promptName];
 * the few fixed phrases she says without the AI fall back to the closest of English / Bengali / Hindi ([base]).
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val locale: Locale,
    val speechTag: String,
    val promptName: String,
    private val baseCode: String
) {
    ENGLISH("en", "English", Locale.US, "en-US", "English", "en"),
    ENGLISH_IN("en-IN", "English (India)", Locale("en", "IN"), "en-IN", "Indian English", "en"),
    BENGALI("bn", "বাংলা (বাংলাদেশ)", Locale("bn", "BD"), "bn-BD",
        "Bengali as spoken in Bangladesh (written in বাংলা script)", "bn"),
    BENGALI_IN("bn-IN", "বাংলা (ভারত)", Locale("bn", "IN"), "bn-IN",
        "Bengali as spoken in West Bengal (written in বাংলা script)", "bn"),
    BANGLISH("banglish", "Banglish (বাংলা + English)", Locale("bn", "BD"), "bn-BD",
        "Banglish — everyday Bangladeshi Bengali naturally mixed with English words (Bengali words in বাংলা script, " +
                "English words in English)", "bn"),
    HINDI("hi", "हिन्दी (Hindi)", Locale("hi", "IN"), "hi-IN", "Hindi (written in Devanagari script)", "hi"),
    HINGLISH("hinglish", "Hinglish (Hindi + English)", Locale("hi", "IN"), "hi-IN",
        "Hinglish — everyday Hindi naturally mixed with English words", "hi"),
    URDU("ur", "اردو (Urdu)", Locale("ur", "PK"), "ur-PK", "Urdu (written in Urdu script)", "hi"),
    NEPALI("ne", "नेपाली (Nepali)", Locale("ne", "NP"), "ne-NP", "Nepali (Devanagari script)", "hi"),
    ASSAMESE("as", "অসমীয়া (Assamese)", Locale("as", "IN"), "as-IN", "Assamese", "bn"),
    PUNJABI("pa", "ਪੰਜਾਬੀ (Punjabi)", Locale("pa", "IN"), "pa-IN", "Punjabi (Gurmukhi script)", "hi"),
    BHOJPURI("bho", "भोजपुरी (Bhojpuri)", Locale("hi", "IN"), "hi-IN", "Bhojpuri (Devanagari script)", "hi"),
    MARATHI("mr", "मराठी (Marathi)", Locale("mr", "IN"), "mr-IN", "Marathi", "hi"),
    GUJARATI("gu", "ગુજરાતી (Gujarati)", Locale("gu", "IN"), "gu-IN", "Gujarati", "hi"),
    TAMIL("ta", "தமிழ் (Tamil)", Locale("ta", "IN"), "ta-IN", "Tamil", "en"),
    TELUGU("te", "తెలుగు (Telugu)", Locale("te", "IN"), "te-IN", "Telugu", "en"),
    KANNADA("kn", "ಕನ್ನಡ (Kannada)", Locale("kn", "IN"), "kn-IN", "Kannada", "en"),
    MALAYALAM("ml", "മലയാളം (Malayalam)", Locale("ml", "IN"), "ml-IN", "Malayalam", "en"),
    ODIA("or", "ଓଡ଼ିଆ (Odia)", Locale("or", "IN"), "or-IN", "Odia", "en"),
    ARABIC("ar", "العربية (Arabic)", Locale("ar"), "ar", "Arabic", "en"),
    INDONESIAN("id", "Bahasa Indonesia", Locale("in", "ID"), "id-ID", "Indonesian", "en");

    /** English, Bengali or Hindi — for the app's own fixed sentences. */
    val base: AppLanguage
        get() = when (baseCode) {
            "bn" -> BENGALI
            "hi" -> HINDI
            else -> ENGLISH
        }

    companion object {
        fun from(code: String?): AppLanguage = values().firstOrNull { it.code == code } ?: ENGLISH
    }
}

'''
s = s.replace(old_enum, new_enum)
n = s.count("when (l) {")
s = s.replace("when (l) {", "when (l.base) {")
open(p, "w", encoding="utf-8").write(s)
print("Phrases switched:", n)

p = "ai/SpeakingStyle.kt"
s = open(p, encoding="utf-8").read()
a = "fun previewLine(style: SpeakingStyle, lang: AppLanguage, name: String): String = when (lang) {"
assert s.count(a) == 1
s = s.replace(a, "fun previewLine(style: SpeakingStyle, lang: AppLanguage, name: String): String = when (lang.base) {")
s = s.replace("            else -> ", "            else -> ") if False else s
open(p, "w", encoding="utf-8").write(s)

p = "ui/SettingsActivity.kt"
s = open(p, encoding="utf-8").read()
a = '''        val languages = arrayOf("English", "বাংলা (Bengali)", "हिन्दी (Hindi)")
        val values = arrayOf("en", "bn", "hi")'''
b = '''        val all = AppLanguage.values()
        val languages = all.map { it.displayName }.toTypedArray()
        val values = all.map { it.code }.toTypedArray()'''
assert s.count(a) == 1
s = s.replace(a, b)
open(p, "w", encoding="utf-8").write(s)
print("languages done")
