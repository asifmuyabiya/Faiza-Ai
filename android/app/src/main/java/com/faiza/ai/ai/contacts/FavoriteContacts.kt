package com.faiza.ai.ai.contacts

import com.faiza.ai.ai.skills.ContactFinder
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.util.PrefsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Someone in "My people" (video 24:26 + the owner's idea): checked before the phone book, found by their name
 * OR a nickname ("বউ", "Ammu"), with an optional separate WhatsApp number. VIPs always get through a Life mode.
 */
data class FavoriteContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val number: String,
    val sos: Boolean = false,
    /** Up to 3 other names she may hear: "বউ", "Faiza", "জান". */
    val nicknames: List<String> = emptyList(),
    /** WhatsApp number if different from [number]; empty = same. */
    val whatsapp: String = "",
    /** Calls always ring, whatever Life mode is on. */
    val vip: Boolean = false
) {
    val whatsappNumber: String get() = whatsapp.ifBlank { number }
    val allNames: List<String> get() = listOf(name) + nicknames
}

object FavoriteContacts {

    const val MAX_SOS = 5
    const val MAX_NICKNAMES = 3
    private val gson = Gson()

    /** Gson ignores Kotlin defaults for missing fields: rebuild every entry safely. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun parse(json: String?): List<FavoriteContact> {
        if (json == null) return emptyList()
        return runCatching {
            gson.fromJson<List<FavoriteContact>>(json, object : TypeToken<List<FavoriteContact>>() {}.type)
                .filter { it != null && it.name != null && it.number != null && it.number.any(Char::isDigit) }
                .map {
                    it.copy(
                        id = it.id ?: UUID.randomUUID().toString(),
                        nicknames = (it.nicknames ?: emptyList()).filter { n -> n != null && n.isNotBlank() }.take(MAX_NICKNAMES),
                        whatsapp = it.whatsapp ?: ""
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun load(prefs: PrefsManager): List<FavoriteContact> = parse(prefs.favoriteContactsJson)

    fun save(prefs: PrefsManager, list: List<FavoriteContact>) {
        prefs.favoriteContactsJson = gson.toJson(list)
    }

    /**
     * Who SOS texts and calls, in order: contacts marked SOS; if none are marked,
     * the first favourites. Never more than [MAX_SOS].
     */
    fun sosContacts(list: List<FavoriteContact>): List<FavoriteContact> {
        val marked = list.filter { it.sos }
        return (marked.ifEmpty { list }).take(MAX_SOS)
    }

    /** Best person for any of [queries] by name or nickname (score ≥ 70), or null. */
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
        // One spelling of "য়" (letter + dot), so the endings below always match.
        var t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).trim().trimEnd('.', '!', '?', ',')
        // App words the AI sometimes leaves in the name.
        listOf("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "whatsapp", "wa", "chat", "চ্যাট").forEach { w ->
            t = t.replace(Regex("(^|\\s)" + Regex.escape(w) + "(\\s|$)", RegexOption.IGNORE_CASE), " ").trim()
        }
        listOf("আমার ", "আমাদের ", "my ", "mera ", "meri ", "मेरी ", "मेरा ", "to ", "the ").forEach {
            if (t.startsWith(it, ignoreCase = true)) t = t.substring(it.length).trim()
        }
        if (t.endsWith("'s") || t.endsWith("’s")) return t.dropLast(2)
        // Longest endings first: "বউয়ের" → "বউ", "আইরিনের" → "আইরিন", "ফাইজার" → "ফাইজা", "বউকে" → "বউ".
        val y = "\u09AF\u09BC" // য়
        val endings = listOf(
            "${y}েরকে", "${y}েরে", "${y}ের", "${y}ে", "েরকে", "দেরকে", "দের", "টাকে", "টার", "টা",
            "কেও", "কে", "এর", "ের", "র", " ko", " ka", " ki", " को", " का", " की", " के"
        )
        for (suf in endings) {
            if (t.length > suf.length + 1 && t.endsWith(suf)) return t.removeSuffix(suf).trim()
        }
        return t
    }
}
