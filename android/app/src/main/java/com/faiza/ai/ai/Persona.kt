package com.faiza.ai.ai

import java.util.Locale

/** The three languages the app's fixed sentences are written in. */
enum class PhraseBase { ENGLISH, BENGALI, HINDI }

/**
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
    val base: PhraseBase
        get() = when (baseCode) {
            "bn" -> PhraseBase.BENGALI
            "hi" -> PhraseBase.HINDI
            else -> PhraseBase.ENGLISH
        }

    companion object {
        fun from(code: String?): AppLanguage = values().firstOrNull { it.code == code } ?: ENGLISH
    }
}

/** What each Gemini voice sounds like. */
val VOICE_TRAITS = mapOf(
    "Zephyr" to "Bright", "Puck" to "Upbeat", "Charon" to "Informative", "Kore" to "Firm", "Fenrir" to "Excitable",
    "Leda" to "Youthful", "Orus" to "Firm", "Aoede" to "Breezy", "Callirrhoe" to "Easy-going", "Autonoe" to "Bright",
    "Enceladus" to "Breathy", "Iapetus" to "Clear", "Umbriel" to "Easy-going", "Algieba" to "Smooth",
    "Despina" to "Smooth", "Erinome" to "Clear", "Algenib" to "Gravelly", "Rasalgethi" to "Informative",
    "Laomedeia" to "Upbeat", "Achernar" to "Soft", "Alnilam" to "Firm", "Schedar" to "Even", "Gacrux" to "Mature",
    "Pulcherrima" to "Forward", "Achird" to "Friendly", "Zubenelgenubi" to "Casual", "Vindemiatrix" to "Gentle",
    "Sadachbia" to "Lively", "Sadaltager" to "Knowledgeable", "Sulafat" to "Warm"
)

/** "Breezy · Aoede" */
fun voiceLabel(voice: String): String = VOICE_TRAITS[voice]?.let { "$it · $voice" } ?: voice

val FEMALE_VOICES = listOf("Aoede", "Kore", "Leda", "Zephyr", "Callirrhoe", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Gacrux", "Pulcherrima", "Vindemiatrix", "Sulafat")
val MALE_VOICES = listOf("Algenib", "Charon", "Fenrir", "Puck", "Orus", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Rasalgethi", "Alnilam", "Schedar", "Achird", "Zubenelgenubi", "Sadachbia", "Sadaltager", "Achernar")

enum class Persona(
    val id: String,
    val displayName: String,
    val emoji: String,
    val tagline: String,
    val pitch: Float,
    val speechRate: Float,
    val isPro: Boolean,
    val style: String,
    /** Default Gemini Live voice; female voices for Faiza/Friday, male for Venom. */
    val liveVoice: String = "Aoede",
    val liveVoiceChoices: List<String> = FEMALE_VOICES
) {
    FAIZA(
        "faiza", "Faiza", "🤖", "Friendly & helpful",
        pitch = 1.1f, speechRate = 1.0f, isPro = false,
        style = """
            PERSONA: Faiza
            - Friendly, warm and caring female assistant
            - Supportive tone, like a helpful close friend
            - Phrases like "Sure thing!", "Of course!", "Happy to help!"
        """.trimIndent()
    ),
    FRIDAY(
        "friday", "Friday", "🦾", "Smart & efficient",
        pitch = 0.95f, speechRate = 1.1f, isPro = true,
        style = """
            PERSONA: Friday
            - Efficient, smart, slightly formal assistant (like Tony Stark's AI)
            - Precise and to the point, no small talk
            - Phrases like "Right away, boss.", "Processing.", "Task completed."
        """.trimIndent(),
        liveVoice = "Kore"
    ),
    VENOM(
        "venom", "Venom", "👾", "Alien-style deep voice",
        pitch = 0.6f, speechRate = 0.85f, isPro = true,
        style = """
            PERSONA: Venom
            - Deep, dramatic, alien symbiote personality
            - Helpful, but with an edgy, intense twist
            - Refers to itself as "WE"; calls the user "host"
            - Phrases like "WE will do it.", "Consider it done, host."
        """.trimIndent(),
        liveVoice = "Charon",
        liveVoiceChoices = MALE_VOICES
    ),
    JARVIS(
        "jarvis", "Jarvis", "🎩", "Calm British butler AI",
        pitch = 0.9f, speechRate = 1.0f, isPro = true,
        style = """
            PERSONA: Jarvis
            - Calm, polished, witty AI butler (like Tony Stark's J.A.R.V.I.S.)
            - Dry British humour, unflappable, quietly brilliant; calls the user "sir" (or "ma'am")
            - Phrases like "As you wish, sir.", "Right away.", "Might I suggest…", "Done, and rather elegantly."
        """.trimIndent(),
        liveVoice = "Sadachbia",
        liveVoiceChoices = MALE_VOICES
    );

    companion object {
        fun from(id: String?): Persona = values().firstOrNull { it.id == id } ?: FAIZA
    }
}

/** Every sentence Faiza speaks without asking the AI, in all supported languages. */
object Phrases {

    /** Assistant names as speech recognizers tend to write them. */
    private val WAKE_NAMES = setOf(
        "maya", "maaya", "mayaa", "maia", "mya", "myah", "moya", "maiya",
        "faiza", "fayza", "faisa", "fiza", "feza", "phaiza", "faza"
    )
    private val WAKE_GREETINGS = setOf(
        "hey", "hi", "hay", "he", "hai", "hello", "ok", "okay", "ai", "a", "yo", "hii", "hiya",
        // "Wake up Faiza", "Online Faiza", "Suno Faiza", "Oi Faiza"
        "up", "wake", "online", "on", "suno", "shuno", "listen", "oi", "oye"
    )

    /** Words that may follow the name: "Faiza online", "Faiza wake up", "Faiza suno". */
    private val WAKE_AFTER = setOf("online", "on", "wake", "up", "suno", "shuno", "listen", "jago", "uth")

    /** Native-script names: matched anywhere ("হে মায়া", "हाय माया"). */
    private val WAKE_SCRIPT_NAMES = listOf(
        "মায়া", "মাইয়া", "মায়া", "ফাইজা", "ফায়জা", "ফাইযা",
        "माया", "मया", "फ़ाइज़ा", "फाइजा", "फैज़ा", "फैजा"
    )

    /**
     * True for "hey maya", "hi maya", "ai hi maya", "hey faiza", or just the name said on its own —
     * but not for the name in the middle of a longer sentence.
     */
    fun isWakeWord(text: String, extraNames: Collection<String> = emptyList()): Boolean {
        val t = text.lowercase(Locale.ROOT).trim()
        if (t.isEmpty()) return false
        if (WAKE_SCRIPT_NAMES.any { t.contains(it) }) return true
        val custom = extraNames.map { it.lowercase(Locale.ROOT).trim() }.filter { it.length >= 3 }
        val words = t.split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }
        if (custom.any { name -> !name.contains(' ') && name !in WAKE_NAMES && words.contains(name) && wordsNear(words, name) }) {
            return true
        }
        words.forEachIndexed { i, w ->
            if (w in WAKE_NAMES || soundsLikeName(w)) {
                if (i > 0 && words[i - 1] in WAKE_GREETINGS) return true
                if (words.size <= 2) return true
                if (i == 0 && words.size <= 3 && words[1] in WAKE_AFTER) return true
            }
        }
        return false
    }

    /** The offline recogniser often mis-hears the name ("faiza" → "visa", "fiza", "pizza"): accept near-misses. */
    private fun soundsLikeName(w: String): Boolean {
        if (w.length < 4 || w.length > 6) return false
        // Faiza family: f/ph start, a "z"/"s" sound, ends in a vowel — "faiza, fiza, feza, phaiza, faisa".
        if ((w[0] == 'f' || w.startsWith("ph")) && (w.contains('z') || w.contains('s')) && w.endsWith("a")) return true
        // Maya family: starts "ma", has a y/i, ends in a vowel — "maya, maiya, moya, maia" (not "mama", "matter").
        if (w.startsWith("ma") && (w.contains('y') || w.contains('i')) && w.endsWith("a")) return true
        // Within one edit of any known spelling.
        return WAKE_NAMES.any { it.length >= 5 && editDistance(w, it) <= 1 }
    }

    private fun editDistance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    fun greeting(p: Persona, l: AppLanguage, name: String = p.displayName): String = when (l.base) {
        PhraseBase.ENGLISH -> when (p) {
            Persona.FAIZA -> "Hi! $name is ready. Just say Hey $name."
            Persona.FRIDAY -> "$name online. All systems ready."
            Persona.VENOM -> "WE are awake, host."
            Persona.JARVIS -> "Good to see you, sir. $name at your service."
        }
        PhraseBase.BENGALI -> when (p) {
            Persona.FAIZA -> "হাই! $name রেডি। শুধু বলো হে $name।"
            Persona.FRIDAY -> "$name অনলাইন। সব সিস্টেম প্রস্তুত।"
            Persona.VENOM -> "আমরা জেগে উঠেছি।"
            Persona.JARVIS -> "স্বাগতম স্যার। $name আপনার সেবায় হাজির।"
        }
        PhraseBase.HINDI -> when (p) {
            Persona.FAIZA -> "हाय! $name तैयार है। बस बोलिए हे $name।"
            Persona.FRIDAY -> "$name ऑनलाइन। सभी सिस्टम तैयार।"
            Persona.VENOM -> "हम जाग गए हैं।"
            Persona.JARVIS -> "स्वागत है सर। $name आपकी सेवा में।"
        }
    }

    /** Custom name said with a greeting before it, or on its own. */
    private fun wordsNear(words: List<String>, name: String): Boolean {
        words.forEachIndexed { i, w ->
            if (w == name && ((i > 0 && words[i - 1] in WAKE_GREETINGS) || words.size <= 2 ||
                        (i == 0 && words.size <= 3 && words[1] in WAKE_AFTER))
            ) return true
        }
        return false
    }

    fun wakeReply(p: Persona, l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> when (p) {
            Persona.FAIZA -> "Yes? I'm listening."
            Persona.FRIDAY -> "At your service."
            Persona.VENOM -> "WE are listening, host."
            Persona.JARVIS -> "Yes, sir?"
        }
        PhraseBase.BENGALI -> when (p) {
            Persona.FAIZA -> "হ্যাঁ, বলো। আমি শুনছি।"
            Persona.FRIDAY -> "আদেশ করুন।"
            Persona.VENOM -> "আমরা শুনছি।"
            Persona.JARVIS -> "জি স্যার, বলুন।"
        }
        PhraseBase.HINDI -> when (p) {
            Persona.FAIZA -> "हाँ, बोलिए। मैं सुन रही हूँ।"
            Persona.FRIDAY -> "आदेश दीजिए।"
            Persona.VENOM -> "हम सुन रहे हैं।"
            Persona.JARVIS -> "जी सर, बोलिए।"
        }
    }

    fun didntHear(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "I didn't hear anything. Try again."
        PhraseBase.BENGALI -> "কিছু শুনতে পেলাম না, আবার বলো।"
        PhraseBase.HINDI -> "मुझे कुछ सुनाई नहीं दिया, फिर से बोलिए।"
    }

    fun trialEnded(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "Your free trial has ended. Please buy a license to keep using Faiza AI."
        PhraseBase.BENGALI -> "তোমার ফ্রি ট্রায়াল শেষ। ফাইজা এআই চালিয়ে যেতে একটা লাইসেন্স কিনে নাও।"
        PhraseBase.HINDI -> "आपका फ्री ट्रायल खत्म हो गया है। जारी रखने के लिए लाइसेंस खरीदें।"
    }

    fun noApiKey(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "My AI brain isn't set up yet. Please add your Gemini API key in the app."
        PhraseBase.BENGALI -> "আমার এআই এখনো সেটআপ হয়নি। অ্যাপে গিয়ে জেমিনি এপিআই কি যোগ করো।"
        PhraseBase.HINDI -> "मेरा एआई अभी सेट नहीं है। ऐप में जेमिनी एपीआई की जोड़िए।"
    }

    fun genericError(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "Sorry, something went wrong. Please try again."
        PhraseBase.BENGALI -> "দুঃখিত, কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করো।"
        PhraseBase.HINDI -> "माफ़ कीजिए, कुछ गड़बड़ हो गई। फिर से कोशिश कीजिए।"
    }

    fun apiKeyInvalid(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "My AI key isn't working. Please check your API key in Faiza settings."
        PhraseBase.BENGALI -> "আমার এআই কি কাজ করছে না। ফাইজার সেটিংসে গিয়ে এপিআই কি টা চেক করো।"
        PhraseBase.HINDI -> "मेरी एआई की काम नहीं कर रही। फ़ाइज़ा सेटिंग्स में एपीआई की चेक कीजिए।"
    }

    fun rateLimited(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "I've hit the free usage limit for now. Please try again in a minute."
        PhraseBase.BENGALI -> "এই মুহূর্তে ফ্রি লিমিট শেষ। এক মিনিট পর আবার চেষ্টা করো।"
        PhraseBase.HINDI -> "अभी फ्री लिमिट खत्म हो गई है। एक मिनट बाद फिर कोशिश कीजिए।"
    }

    fun networkError(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "I can't reach the internet right now."
        PhraseBase.BENGALI -> "এখন ইন্টারনেটে কানেক্ট করতে পারছি না।"
        PhraseBase.HINDI -> "अभी इंटरनेट से कनेक्ट नहीं हो पा रहा।"
    }

    fun tooManySteps(l: AppLanguage): String = when (l.base) {
        PhraseBase.ENGLISH -> "That task needed too many steps. Let's try it in smaller parts."
        PhraseBase.BENGALI -> "কাজটা অনেক বড়। চলো ছোট ছোট করে করি।"
        PhraseBase.HINDI -> "यह काम बहुत लंबा है। चलिए छोटे हिस्सों में करते हैं।"
    }

    fun incomingCall(l: AppLanguage, caller: String?): String = when (l.base) {
        PhraseBase.ENGLISH -> if (caller != null) "Incoming call from $caller" else "You have an incoming call"
        PhraseBase.BENGALI -> if (caller != null) "$caller ফোন করছে" else "একটা কল আসছে"
        PhraseBase.HINDI -> if (caller != null) "$caller का कॉल आ रहा है" else "एक कॉल आ रहा है"
    }

    fun newMessage(l: AppLanguage, app: String, sender: String): String = when (l.base) {
        PhraseBase.ENGLISH -> "New $app message from $sender"
        PhraseBase.BENGALI -> "$sender থেকে $app-এ নতুন মেসেজ"
        PhraseBase.HINDI -> "$sender का $app पर नया मैसेज"
    }

    fun ttsMissing(l: AppLanguage): String =
        "${l.displayName} voice is not installed. Install it from Settings → Text-to-speech → Google → Install voice data."
}
