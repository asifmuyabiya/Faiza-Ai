package com.faiza.ai.ai.skills

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.faiza.ai.service.FaizaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Finds phone contacts by name in any script, tolerating spelling differences ("চাঁদ" / "Chand"). */
object ContactFinder {

    data class Match(val name: String, val number: String, val score: Int)

    /** Primary name plus comma-separated alternate spellings, cleaned and de-duplicated. */
    fun queries(primary: String, alternates: String?): List<String> =
        (listOf(primary) + alternates.orEmpty().split(',', '/', '|'))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { FaizaAccessibilityService.normalize(it) }

    fun looksLikeNumber(s: String): Boolean {
        val digits = s.count { it.isDigit() }
        return digits >= 5 && s.all { it.isDigit() || it in "+-() " }
    }

    /** Ranked matches across all [queries]; throws [SkillException] without the Contacts permission. */
    suspend fun search(ctx: Context, queries: List<String>, limit: Int = 5): List<Match> = withContext(Dispatchers.IO) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw SkillException("Contacts permission not granted. Ask the user to allow it in Faiza AI settings.")
        }
        val normalizedQueries = queries.map { FaizaAccessibilityService.normalize(it) }.filter { it.isNotEmpty() }
        if (normalizedQueries.isEmpty()) return@withContext emptyList()

        val best = HashMap<String, Match>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val n = FaizaAccessibilityService.normalize(name)
                if (n.isEmpty()) continue
                val score = normalizedQueries.maxOf { score(n, name, it) }
                if (score <= 0) continue
                val key = n
                if ((best[key]?.score ?: -1) < score) best[key] = Match(name, number, score)
            }
        }
        best.values.sortedByDescending { it.score }.take(limit)
    }

    /** 0–100: how well a saved [name] matches what the user said ([query]), any script. */
    fun scoreName(name: String, query: String): Int {
        val n = FaizaAccessibilityService.normalize(name)
        val q = FaizaAccessibilityService.normalize(query)
        if (n.isEmpty() || q.isEmpty()) return 0
        return score(n, name, q)
    }

    private fun score(n: String, original: String, q: String): Int {
        if (n == q) return 100
        if (n.startsWith(q)) return 88
        val words = original.lowercase().split(' ', '.', '-', '_').map { FaizaAccessibilityService.normalize(it) }
        if (words.any { it == q }) return 92
        if (words.any { it.startsWith(q) && q.length >= 2 }) return 82
        if (q.length >= 3 && n.contains(q)) return 72
        // Spelling differences: "chand" vs "chaand", "faiza" vs "fayza".
        val ratio = similarity(n, q)
        val wordRatio = words.maxOfOrNull { similarity(it, q) } ?: 0.0
        val r = maxOf(ratio, wordRatio)
        return if (r >= 0.72) (r * 80).toInt() else 0
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = levenshtein(a, b)
        return 1.0 - d.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
