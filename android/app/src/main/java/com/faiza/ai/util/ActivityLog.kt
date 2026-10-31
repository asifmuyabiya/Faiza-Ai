package com.faiza.ai.util

import com.faiza.ai.FaizaApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** One thing Faiza did on her own (a reminder ran, a message was answered, a macro finished…). */
data class LogEntry(val at: Long, val kind: String, val title: String, val text: String)

/** The 🔔 inbox on the home screen (MAYA): what happened while you weren't looking. */
object ActivityLog {

    private const val MAX = 100
    private val gson = Gson()
    private val lock = Any()

    private val prefs get() = FaizaApp.instance.prefs

    @Suppress("SENSELESS_COMPARISON")
    fun all(): List<LogEntry> = synchronized(lock) {
        val json = prefs.activityLogJson ?: return emptyList()
        runCatching { gson.fromJson<List<LogEntry>>(json, object : TypeToken<List<LogEntry>>() {}.type) }
            .getOrNull().orEmpty()
            .filter { it != null && it.title != null && it.kind != null }
            .map { if (it.text == null) it.copy(text = "") else it }
    }

    fun add(kind: String, title: String, text: String) = synchronized(lock) {
        val list = listOf(LogEntry(System.currentTimeMillis(), kind, title.take(80), text.take(300))) + all()
        prefs.activityLogJson = gson.toJson(list.take(MAX))
    }

    fun hasUnread(): Boolean = all().any { it.at > prefs.activityLogSeenAt }

    fun markSeen() {
        prefs.activityLogSeenAt = System.currentTimeMillis()
    }

    fun clear() = synchronized(lock) { prefs.activityLogJson = null }
}
