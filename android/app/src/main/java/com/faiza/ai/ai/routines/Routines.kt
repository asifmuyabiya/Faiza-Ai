package com.faiza.ai.ai.routines

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** How often a scheduled task runs. */
enum class Repeat { ONCE, DAILY, WEEKDAYS, WEEKENDS, WEEKLY }

/**
 * Something Faiza does at a set time (video 40:31): a reminder ("Remind the user to take medicine")
 * or any job she can do ("Run the Good morning macro", "Tell the user today's weather").
 */
data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    /** What Faiza should do then, in her own words. */
    val instruction: String,
    val hour: Int = 0,
    val minute: Int = 0,
    val repeat: Repeat = Repeat.ONCE,
    /** WEEKLY: ISO days 1 = Monday … 7 = Sunday. */
    val days: List<Int> = emptyList(),
    /** ONCE on a given day (epoch day), else the next time [hour]:[minute] comes round. */
    val dateEpochDay: Long? = null,
    /** ONCE "in N minutes": the exact moment. */
    val atMillis: Long? = null,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long = 0L
)

enum class WatchKind { MESSAGE, SCREEN }
enum class WatchAction { TELL, REPLY, TASK }

/** "Tell me when…" (video 40:31): a new notification or something on screen triggers Faiza. */
data class Watch(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val kind: WatchKind = WatchKind.MESSAGE,
    /** App name or part of it ("WhatsApp"); empty = any messaging app. */
    val app: String = "",
    /** Sender names, comma-separated spellings ("Rahim, রহিম"); empty = anyone. */
    val sender: String = "",
    /** Words to look for, comma-separated; empty = any message. SCREEN: the text to wait for. */
    val keywords: String = "",
    val action: WatchAction = WatchAction.TELL,
    val replyText: String = "",
    val instruction: String = "",
    val once: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Stop watching after this moment (0 = never). */
    val expiresAt: Long = 0L,
    val triggerCount: Int = 0,
    val lastTriggeredAt: Long = 0L
)

object Routines {

    private val gson = Gson()
    const val MAX_ITEMS = 60

    // ===== Time =====

    private fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)), zone)

    private fun runsOn(t: ScheduledTask, day: DayOfWeek): Boolean = when (t.repeat) {
        Repeat.ONCE, Repeat.DAILY -> true
        Repeat.WEEKDAYS -> day.value in 1..5
        Repeat.WEEKENDS -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        Repeat.WEEKLY -> day.value in t.days
    }

    /** Next moment (epoch ms) the task should run after [now], or null if it never will again. */
    fun nextRun(t: ScheduledTask, now: ZonedDateTime): Long? {
        if (!t.enabled) return null
        val nowMs = now.toInstant().toEpochMilli()
        if (t.repeat == Repeat.ONCE) {
            t.atMillis?.let { return if (it > nowMs) it else null }
            t.dateEpochDay?.let { d ->
                val ms = at(LocalDate.ofEpochDay(d), t.hour, t.minute, now.zone).toInstant().toEpochMilli()
                return if (ms > nowMs) ms else null
            }
            val today = at(now.toLocalDate(), t.hour, t.minute, now.zone)
            val next = if (today.isAfter(now)) today else today.plusDays(1)
            return next.toInstant().toEpochMilli()
        }
        if (t.repeat == Repeat.WEEKLY && t.days.none { it in 1..7 }) return null
        for (d in 0..7L) {
            val date = now.toLocalDate().plusDays(d)
            if (!runsOn(t, date.dayOfWeek)) continue
            val c = at(date, t.hour, t.minute, now.zone)
            if (c.isAfter(now)) return c.toInstant().toEpochMilli()
        }
        return null
    }

    /** After it ran: one-time tasks switch off; repeating ones stay on. */
    fun afterRun(t: ScheduledTask, ranAt: Long): ScheduledTask =
        t.copy(lastRunAt = ranAt, enabled = t.repeat != Repeat.ONCE && t.enabled)

    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "Every day at 8:00 AM", "Mon, Wed at 6:00 PM", "Once · Tue 6 Oct at 8:00 PM". */
    fun describe(t: ScheduledTask, zone: ZoneId): String {
        val time = LocalTime.of(t.hour.coerceIn(0, 23), t.minute.coerceIn(0, 59)).format(TIME)
        return when (t.repeat) {
            Repeat.DAILY -> "Every day at $time"
            Repeat.WEEKDAYS -> "Weekdays at $time"
            Repeat.WEEKENDS -> "Weekends at $time"
            Repeat.WEEKLY -> t.days.filter { it in 1..7 }.sorted().joinToString(", ") { DAY_NAMES[it - 1] } + " at $time"
            Repeat.ONCE -> {
                val ms = t.atMillis
                    ?: t.dateEpochDay?.let { at(LocalDate.ofEpochDay(it), t.hour, t.minute, zone).toInstant().toEpochMilli() }
                if (ms != null) {
                    val z = Instant.ofEpochMilli(ms).atZone(zone)
                    "Once · ${z.format(DAY)} at ${z.format(TIME)}"
                } else "Once at $time"
            }
        }
    }

    /** "today 8:00 PM", "tomorrow 7:30 AM", "Mon 12 Oct, 9:00 AM". */
    fun whenText(ms: Long, now: ZonedDateTime): String {
        val z = Instant.ofEpochMilli(ms).atZone(now.zone)
        val days = z.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> "today ${z.format(TIME)}"
            1L -> "tomorrow ${z.format(TIME)}"
            else -> "${z.format(DAY)}, ${z.format(TIME)}"
        }
    }

    /** "mon,wed" / "Monday and Friday" / "সোম" → ISO day numbers. */
    fun parseDays(text: String?): List<Int> {
        if (text.isNullOrBlank()) return emptyList()
        val names = mapOf(
            "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7,
            "সোম" to 1, "মঙ্গল" to 2, "বুধ" to 3, "বৃহস্পতি" to 4, "শুক্র" to 5, "শনি" to 6, "রবি" to 7
        )
        return text.lowercase(Locale.ROOT).split(Regex("[,;/ ]+|\\band\\b")).mapNotNull { w ->
            val word = w.trim()
            if (word.isEmpty()) null else names.entries.firstOrNull { word.startsWith(it.key) }?.value
        }.distinct().sorted()
    }

    /** "08:30", "8:30 pm", "20.30" → (hour, minute). */
    fun parseTime(text: String?): Pair<Int, Int>? {
        val m = Regex("^\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*([ap]\\.?m\\.?)?\\s*$", RegexOption.IGNORE_CASE)
            .find(text ?: return null) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ampm = m.groupValues[3].lowercase(Locale.ROOT).replace(".", "")
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    // ===== Matching =====

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        return buildString {
            for (ch in n) {
                val t = Character.getType(ch)
                if (ch.isLetterOrDigit() || t == Character.NON_SPACING_MARK.toInt() ||
                    t == Character.COMBINING_SPACING_MARK.toInt()
                ) append(ch) else append(' ')
            }
        }.replace(Regex(" +"), " ").trim()
    }

    private fun parts(s: String) = s.split(',').map { normalize(it) }.filter { it.isNotEmpty() }

    private val APP_ALIASES = mapOf(
        "sms" to listOf("messaging", "mms", "sms", "messages"),
        "message" to listOf("messaging", "mms", "sms", "messages"),
        "messenger" to listOf("orca", "messenger"),
        "whatsapp" to listOf("whatsapp"),
        "telegram" to listOf("telegram"),
        "imo" to listOf("imo")
    )

    fun appMatches(want: String, pkg: String, appLabel: String): Boolean {
        val w = normalize(want)
        if (w.isEmpty()) return true
        val hay = normalize("$pkg $appLabel").replace(" ", "")
        if (hay.contains(w.replace(" ", ""))) return true
        return APP_ALIASES.entries.any { (k, v) -> w.contains(k) && v.any { hay.contains(it) } }
    }

    fun senderMatches(want: String, sender: String): Boolean {
        val wanted = parts(want)
        if (wanted.isEmpty()) return true
        val s = normalize(sender)
        return wanted.any { w -> s.contains(w) || (s.isNotEmpty() && w.contains(s) && s.length >= 3) }
    }

    fun textMatches(keywords: String, text: String): Boolean {
        val wanted = parts(keywords)
        if (wanted.isEmpty()) return true
        val t = normalize(text)
        return wanted.any { t.contains(it) }
    }

    fun matchesMessage(w: Watch, pkg: String, appLabel: String, sender: String, text: String, now: Long): Boolean =
        w.enabled && w.kind == WatchKind.MESSAGE && (w.expiresAt == 0L || now < w.expiresAt) &&
                appMatches(w.app, pkg, appLabel) && senderMatches(w.sender, sender) && textMatches(w.keywords, "$sender $text")

    /** Best item whose label (or text) matches what the user said, e.g. "cancel the medicine reminder". */
    fun <T> find(items: List<T>, spoken: String, text: (T) -> String): T? {
        val filler = setOf("the", "my", "a", "reminder", "task", "routine", "watch", "alarm")
        val q = normalize(spoken).split(' ').filter { it.isNotEmpty() && it !in filler }
        if (q.isEmpty()) return null
        return items.map { item ->
            val words = normalize(text(item)).split(' ').toSet()
            item to q.count { w -> words.any { it.startsWith(w) || w.startsWith(it) && it.length >= 3 } }
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    // ===== Storage =====

    fun loadTasks(get: () -> String?): List<ScheduledTask> = runCatching {
        gson.fromJson<List<ScheduledTask>>(get() ?: return emptyList(), object : TypeToken<List<ScheduledTask>>() {}.type)
    }.getOrNull().orEmpty().let(::sanitizeTasks)

    fun loadWatches(get: () -> String?): List<Watch> = runCatching {
        gson.fromJson<List<Watch>>(get() ?: return emptyList(), object : TypeToken<List<Watch>>() {}.type)
    }.getOrNull().orEmpty().let(::sanitizeWatches)

    fun saveTasks(put: (String) -> Unit, list: List<ScheduledTask>) = put(gson.toJson(list.take(MAX_ITEMS)))
    fun saveWatches(put: (String) -> Unit, list: List<Watch>) = put(gson.toJson(list.take(MAX_ITEMS)))

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitizeTasks(list: List<ScheduledTask>): List<ScheduledTask> = list
        .filter { it != null && it.label != null && it.instruction != null }
        .map { it.copy(id = it.id ?: UUID.randomUUID().toString(), repeat = it.repeat ?: Repeat.ONCE, days = it.days ?: emptyList()) }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitizeWatches(list: List<Watch>): List<Watch> = list
        .filter { it != null && it.label != null }
        .map {
            it.copy(
                id = it.id ?: UUID.randomUUID().toString(),
                kind = it.kind ?: WatchKind.MESSAGE, action = it.action ?: WatchAction.TELL,
                app = it.app ?: "", sender = it.sender ?: "", keywords = it.keywords ?: "",
                replyText = it.replyText ?: "", instruction = it.instruction ?: ""
            )
        }
}
