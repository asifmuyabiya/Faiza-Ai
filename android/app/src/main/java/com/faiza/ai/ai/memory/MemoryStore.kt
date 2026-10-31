package com.faiza.ai.ai.memory

import android.content.Context
import com.faiza.ai.FaizaApp
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class MemoryFact(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ConversationLine(
    val at: Long,
    /** "user" or "assistant" */
    val who: String,
    val text: String
)

data class MemoryData(
    val facts: List<MemoryFact> = emptyList(),
    val log: List<ConversationLine> = emptyList()
)

/**
 * Faiza's long-term memory (video 14:13): facts she learned about the user plus a rolling
 * conversation history. Stored privately in the app; incognito strips secrets before saving.
 */
object MemoryStore {

    enum class SaveResult { SAVED, DUPLICATE, BLOCKED_SENSITIVE, DISABLED }

    private const val MAX_FACTS = 500
    private const val MAX_LOG = 800
    private const val MAX_FACT_CHARS = 300
    private const val MAX_LINE_CHARS = 600

    private val gson = Gson()
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor()

    private var file: File? = null
    private var facts = mutableListOf<MemoryFact>()
    private var log = mutableListOf<ConversationLine>()

    private val prefs get() = FaizaApp.instance.prefs

    fun init(ctx: Context) {
        synchronized(lock) {
            if (file != null) return
            val f = File(ctx.filesDir, "memory/memory.json")
            file = f
            if (f.exists()) {
                runCatching { gson.fromJson(f.readText(), MemoryData::class.java) }
                    .onSuccess { data -> loadSafely(data) }
                    .onFailure {
                        // Keep the unreadable file for safety instead of silently losing memories.
                        f.renameTo(File(f.parentFile, "memory-unreadable-${System.currentTimeMillis()}.json"))
                    }
            }
        }
    }

    /** Gson can leave Kotlin "non-null" fields null in a damaged file; skip such entries. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun loadSafely(data: MemoryData?) {
        facts = (data?.facts ?: emptyList())
            .filter { it != null && it.text != null && it.text.isNotBlank() }
            .map { if (it.id == null) it.copy(id = UUID.randomUUID().toString()) else it }
            .toMutableList()
        log = (data?.log ?: emptyList())
            .filter { it != null && it.text != null && it.who != null && it.text.isNotBlank() }
            .toMutableList()
    }

    // ===== Facts =====

    fun remember(text: String): SaveResult {
        if (!prefs.isMemoryEnabled) return SaveResult.DISABLED
        val clean = text.trim().replace(Regex("\\s+"), " ").take(MAX_FACT_CHARS)
        if (clean.isEmpty()) return SaveResult.DUPLICATE
        // If the filter itself ever fails, refuse to store rather than risk saving a secret.
        val secret = runCatching { SensitiveFilter.isSensitive(clean) }.getOrDefault(true)
        if (prefs.isIncognito && secret) return SaveResult.BLOCKED_SENSITIVE
        synchronized(lock) {
            val key = key(clean)
            if (facts.any { key(it.text) == key }) return SaveResult.DUPLICATE
            facts.add(MemoryFact(text = clean))
            while (facts.size > MAX_FACTS) facts.removeAt(0)
        }
        save()
        return SaveResult.SAVED
    }

    fun facts(): List<MemoryFact> = synchronized(lock) { facts.toList() }

    fun recentFacts(count: Int): List<MemoryFact> = synchronized(lock) { facts.takeLast(count) }

    fun delete(id: String) {
        synchronized(lock) { facts.removeAll { it.id == id } }
        save()
    }

    /** Removes facts matching [query]; returns what was removed. */
    fun forget(query: String): List<MemoryFact> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val removed = synchronized(lock) {
            val hits = facts.filter { f ->
                val fw = words(f.text)
                words.all { w -> fw.any { it.startsWith(w) } }
            }
            facts.removeAll(hits.toSet())
            hits
        }
        if (removed.isNotEmpty()) save()
        return removed
    }

    fun clearFacts() {
        synchronized(lock) { facts.clear() }
        save()
    }

    // ===== Conversation history =====

    /** Never throws: a memory problem must not interrupt the conversation. */
    fun logLine(who: String, text: String) = runCatching { logLineUnsafe(who, text) }.let { }

    private fun logLineUnsafe(who: String, text: String) {
        if (!prefs.isMemoryEnabled) return
        var clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        if (prefs.isIncognito) {
            // If the filter fails, drop the line rather than risk storing a secret.
            val sensitive = runCatching { SensitiveFilter.isSensitive(clean) }.getOrNull() ?: return
            if (sensitive) clean = runCatching { SensitiveFilter.redact(clean) }.getOrNull() ?: return
        }
        synchronized(lock) {
            log.add(ConversationLine(System.currentTimeMillis(), who, clean.take(MAX_LINE_CHARS)))
            while (log.size > MAX_LOG) log.removeAt(0)
        }
        save()
    }

    fun logSize(): Int = synchronized(lock) { log.size }

    /** The last lines of talk from the past [withinMs] (so a new voice chat continues where the last one stopped). */
    fun recentConversation(maxLines: Int = 16, withinMs: Long = 3 * 3600_000L, now: Long = System.currentTimeMillis()): List<ConversationLine> =
        synchronized(lock) { log.filter { now - it.at <= withinMs }.takeLast(maxLines) }

    fun clearLog() {
        synchronized(lock) { log.clear() }
        save()
    }

    // ===== Search =====

    /** Best-matching facts and past conversation lines for [query], newest first among equals. */
    fun search(query: String, limit: Int = 12): List<String> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val day = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
        val dayTime = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
        val (f, l) = synchronized(lock) { facts.toList() to log.toList() }

        fun score(text: String): Int {
            val tw = words(text)
            return words.count { w -> tw.any { it.startsWith(w) || w.startsWith(it) && it.length >= 3 } }
        }

        val scored = f.map { Triple(score(it.text), it.createdAt, "[fact, ${day.format(Date(it.createdAt))}] ${it.text}") } +
                l.map {
                    val who = if (it.who == "user") "User" else "You"
                    Triple(score(it.text), it.at, "[${dayTime.format(Date(it.at))}] $who: ${it.text}")
                }
        return scored.filter { it.first > 0 }
            .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })
            .take(limit)
            .map { it.third }
    }

    // ===== Backup =====

    fun export(): MemoryData = synchronized(lock) { MemoryData(facts.toList(), log.toList()) }

    fun replaceAll(data: MemoryData) {
        synchronized(lock) {
            facts = data.facts.filter { it.text.isNotBlank() }.takeLast(MAX_FACTS).toMutableList()
            log = data.log.filter { it.text.isNotBlank() }.takeLast(MAX_LOG).toMutableList()
        }
        save()
    }

    // ===== Internals =====

    private fun key(text: String) = words(text).joinToString(" ")

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.length >= 2 }

    /** Atomic write on a background thread: temp file, then rename. */
    private fun save() {
        val target = synchronized(lock) { file } ?: return
        val json = synchronized(lock) { gson.toJson(MemoryData(facts.toList(), log.toList())) }
        writer.execute {
            runCatching {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
            }
        }
    }
}
