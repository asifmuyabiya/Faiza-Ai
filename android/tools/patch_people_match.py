base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'

# ---- OwnerLock: shared "sounds" spelling (vowels kept) for cross-script name matching ----
p = base + r'\ai\guardian\OwnerLock.kt'
s = open(p, encoding='utf-8').read()
old = s[s.index("    fun skeleton(text: String): String {"):s.index("    fun levenshtein(")]
new = '''    fun skeleton(text: String): String = sounds(text, keepVowels = false)

    /**
     * Any script → simple Latin letters with similar sounds merged ("আইরিন" → "airin", "Faiza" → "faija").
     * [keepVowels] = false gives the consonant skeleton.
     */
    fun sounds(text: String, keepVowels: Boolean = true): String {
        val latin = buildString {
            for (ch in text.lowercase()) append(TRANSLIT[ch] ?: ch.toString())
        }
        var t = latin
        listOf("sh" to "s", "ch" to "c", "kh" to "k", "gh" to "g", "jh" to "j", "th" to "t", "dh" to "d",
            "ph" to "f", "bh" to "b", "ng" to "n", "ck" to "k").forEach { (a, b) -> t = t.replace(a, b) }
        val merged = buildString {
            for (c in t) {
                val m = when (c) {
                    'v', 'w' -> 'b'
                    'z' -> 'j'
                    'q' -> 'k'
                    'x' -> 'k'
                    else -> c
                }
                val vowel = m in "aeiouyh"
                if (m in 'a'..'z' && (keepVowels || !vowel)) append(m) else if (m.isDigit()) append(m)
            }
        }
        return buildString { merged.forEach { if (isEmpty() || last() != it) append(it) } }
    }

'''
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)

# ---- FavoriteContacts: better spoken-name cleaning, relation words, cross-script sounds ----
p = base + r'\ai\contacts\FavoriteContacts.kt'
s = open(p, encoding='utf-8').read()
old = s[s.index("    /** Best person for any of [queries]"):s.rindex("}")]
new = '''    /** Best person for any of [queries] by name or nickname (score ≥ 70), or null. */
    fun bestMatch(list: List<FavoriteContact>, queries: List<String>): FavoriteContact? {
        val all = queries.flatMap { listOf(it, cleanSpokenName(it)) }.filter { it.isNotBlank() }.distinct()
        if (all.isEmpty()) return null
        return list.map { f -> f to all.maxOf { q -> f.allNames.maxOf { n -> score(n, q) } } }
            .filter { it.second >= 70 }
            .maxByOrNull { it.second }
            ?.first
    }

    /** 0–100: how well a saved name / nickname matches what was said, across Bangla / Hindi / English. */
    fun score(name: String, query: String): Int {
        val direct = ContactFinder.scoreName(name, query)
        if (direct >= 90) return direct
        // "wife" ↔ "বউ", "mom" ↔ "আম্মু": the AI often translates relation words.
        val rel = if (relationOf(name) != null && relationOf(name) == relationOf(query)) 95 else 0
        // Same sounds in another script: "Irin" ↔ "আইরিন", "Faiza" ↔ "ফাইজা", "Bou" ↔ "বউ".
        val sound = (listOf(name) + name.split(' ')).filter { it.isNotBlank() }.maxOf { soundScore(it, query) }
        return maxOf(direct, rel, sound)
    }

    private fun soundScore(name: String, query: String): Int {
        val ks = com.faiza.ai.ai.guardian.OwnerLock.skeleton(name)
        val qs = com.faiza.ai.ai.guardian.OwnerLock.skeleton(query)
        if (ks.isEmpty() || ks != qs) return 0
        val a = com.faiza.ai.ai.guardian.OwnerLock.sounds(name)
        val b = com.faiza.ai.ai.guardian.OwnerLock.sounds(query)
        val d = com.faiza.ai.ai.guardian.OwnerLock.levenshtein(a, b)
        val sim = 1.0 - d.toDouble() / maxOf(a.length, b.length, 1)
        return when {
            sim >= 0.6 && ks.length >= 2 -> 86
            sim >= 0.6 -> 76 // one-consonant names ("বউ" / "Bou"): only when the vowels agree too
            else -> 0
        }
    }

    /** Relation words in Bangla, Hindi, Banglish and English. */
    private val RELATIONS: Map<String, List<String>> = mapOf(
        "wife" to listOf("wife", "wifey", "বউ", "বৌ", "বউটা", "স্ত্রী", "বিবি", "bou", "bow", "bibi", "biwi", "bibi",
            "बीवी", "पत्नी", "wife ji"),
        "husband" to listOf("husband", "hubby", "স্বামী", "জামাই", "jamai", "swami", "पति", "shami"),
        "mother" to listOf("mom", "mother", "mum", "mummy", "mama", "maa", "ma", "মা", "আম্মু", "আম্মা", "ammu", "amma",
            "আম্মি", "ammi", "माँ", "मां", "अम्मी", "मम्मी"),
        "father" to listOf("dad", "father", "daddy", "papa", "abbu", "abba", "baba", "বাবা", "আব্বু", "আব্বা", "পাপা",
            "पापा", "अब्बू", "पिताजी"),
        "brother" to listOf("brother", "bro", "bhai", "bhaiya", "ভাই", "ভাইয়া", "ভাইয়া", "दादा", "भाई", "भैया"),
        "sister" to listOf("sister", "sis", "apu", "apa", "আপু", "আপা", "বোন", "bon", "didi", "দিদি", "दीदी", "बहन"),
        "son" to listOf("son", "chele", "ছেলে", "बेटा"),
        "daughter" to listOf("daughter", "meye", "মেয়ে", "মেয়ে", "बेटी")
    )

    fun relationOf(word: String): String? {
        val w = FaizaAccessibilityService.normalize(cleanSpokenName(word))
        if (w.isEmpty()) return null
        return RELATIONS.entries.firstOrNull { (_, words) -> words.any { FaizaAccessibilityService.normalize(it) == w } }?.key
    }

    /**
     * "আমার বউয়ের" / "to my wife" / "Irin's WhatsApp" → "বউ" / "wife" / "Irin": removes possessives, case endings and
     * app words so names and nicknames match.
     */
    fun cleanSpokenName(s: String): String {
        var t = s.trim().trimEnd('.', '!', '?', ',')
        // App words the AI sometimes leaves in the name.
        listOf("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "whatsapp", "wa", "chat", "চ্যাট").forEach { w ->
            t = t.replace(Regex("(?i)(^|\\\\s)" + Regex.escape(w) + "(\\\\s|$)"), " ").trim()
        }
        listOf("আমার ", "আমাদের ", "my ", "mera ", "meri ", "मेरी ", "मेरा ", "to ", "the ").forEach {
            if (t.startsWith(it, ignoreCase = true)) t = t.substring(it.length).trim()
        }
        if (t.endsWith("'s") || t.endsWith("’s")) return t.dropLast(2)
        // Longest endings first: "বউয়ের" → "বউ", "আইরিনের" → "আইরিন", "ফাইজার" → "ফাইজা", "বউকে" → "বউ".
        val endings = listOf(
            "য়েরকে", "য়েরকে", "য়ের", "য়ের", "য়েরে", "য়ে", "য়ে", "েরকে", "দেরকে", "দের", "টাকে", "টার", "টা",
            "কেও", "কে", "এর", "ের", "র", "ko", "को", "ka", "ki", "का", "की", "के"
        )
        for (suf in endings) {
            if (t.length > suf.length + 1 && t.endsWith(suf)) return t.removeSuffix(suf).trim()
        }
        return t
    }
'''
s = s.replace(old, new)
if "import com.faiza.ai.service.FaizaAccessibilityService" not in s:
    s = s.replace("import com.faiza.ai.ai.skills.ContactFinder\n",
                  "import com.faiza.ai.ai.skills.ContactFinder\nimport com.faiza.ai.service.FaizaAccessibilityService\n")
# drop the old cleanSpokenName (now replaced above) if it survived outside the replaced block
open(p, 'w', encoding='utf-8').write(s)

# ---- BuiltInSkills: My people first (cleaned names), then phone book, then WhatsApp search in both scripts ----
p = base + r'\ai\skills\BuiltInSkills.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep('''            "Open the WhatsApp chat of a person or group by name (works with Bengali names too).",''',
    '''            "Open the WhatsApp chat of a person or group. Checks the user's My people list FIRST (names and nicknames " +
                    "like \\"বউ\\", uses their saved WhatsApp number directly — works even if WhatsApp has them under " +
                    "another name), then the phone book, then searches inside WhatsApp. Pass the name exactly as the user " +
                    "said it (\\"বউ\\", \\"আইরিন\\" — not translated), and the same name in English letters / English " +
                    "in alternate_names (\\"wife, Bou\\", \\"Irin\\").",''')
rep('''        val queries = ContactFinder.queries(contact, alternates)
        val phoneMatch = runCatching { resolveContact(ctx, contact, alternates, forWhatsApp = true) }.getOrNull()
        if (phoneMatch != null) {''', '''        val queries = whatsappSearchQueries(contact, alternates)
        val phoneMatch = runCatching { resolveContact(ctx, contact, alternates, forWhatsApp = true) }.getOrNull()
        if (phoneMatch != null) {''')
rep('''            val opened = searchWhatsAppChat(ctx, ContactFinder.queries(contact, alternates))''',
    '''            val opened = searchWhatsAppChat(ctx, whatsappSearchQueries(contact, alternates))''')
rep('''    private suspend fun searchWhatsAppChat(ctx: Context, queries: List<String>): String {''',
    '''    /**
     * Names to type into WhatsApp's search when the person isn't in My people / contacts: the cleaned name first
     * ("আমার বউয়ের" → "বউ"), then what the user said, the alternates, and an English-letter spelling of a Bangla name.
     */
    internal fun whatsappSearchQueries(contact: String, alternates: String?): List<String> {
        val said = ContactFinder.queries(contact, alternates)
        val cleaned = said.map { FavoriteContacts.cleanSpokenName(it) }
        val latin = cleaned.filter { n -> n.any { it.code in 0x0980..0x09FF || it.code in 0x0900..0x097F } }
            .map { com.faiza.ai.ai.guardian.OwnerLock.sounds(it) }
            .filter { it.length >= 3 }
        return (cleaned + said + latin).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { FaizaAccessibilityService.normalize(it) }.take(5)
    }

    private suspend fun searchWhatsAppChat(ctx: Context, queries: List<String>): String {''')
# whatsapp_message tool: same hint.
rep('''            "Send a WhatsApp message to a person or group. Requires the user's spoken confirmation first.",''',
    '''            "Send a WhatsApp message to a person or group. Requires the user's spoken confirmation first. Finds the " +
                    "person in My people first (nicknames too, their WhatsApp number), like open_whatsapp_chat; pass " +
                    "the name as the user said it plus English-letter spellings in alternate_names.",''')
open(p, 'w', encoding='utf-8').write(s)
print("ok")
