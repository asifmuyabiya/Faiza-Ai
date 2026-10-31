package com.faiza.ai.ai.modes

import java.util.UUID

/**
 * Life modes (the owner's idea): Driving, Sleeping, Prayer, Meeting… While one is on, calls are declined
 * politely with a text, VIPs and repeat callers still get through, and Faiza stays quiet.
 * Pure logic, so it is unit-tested.
 */
data class LifeMode(
    val id: String,
    val emoji: String,
    val name: String,
    val subtitle: String,
    val messageBn: String,
    val messageEn: String,
    val builtIn: Boolean = true
)

/** A caller declined (or let through) while a mode was on. */
data class ModeCall(val at: Long, val number: String, val name: String, val modeId: String, val declined: Boolean, val texted: Boolean)

object LifeModes {

    const val REPEAT_CALL_MS = 3 * 60_000L
    private const val SIG_BN = " — {ai}, {name}-এর AI সহকারী"
    private const val SIG_EN = " — {ai}, {name}'s AI assistant"

    val BUILT_IN = listOf(
        LifeMode(
            "driving", "🚗", "Driving", "Calls get a text · safe on the road",
            "আসসালামু আলাইকুম। {name} এখন গাড়ি চালাচ্ছেন, তাই কল ধরতে পারছেন না। নিরাপদে পৌঁছে আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Hello! {name} is driving right now and can't take your call. {name} will call you back after arriving safely.$SIG_EN"
        ),
        LifeMode(
            "sleeping", "😴", "Sleeping", "Quiet night · a summary in the morning",
            "আসসালামু আলাইকুম। {name} এখন বিশ্রাম নিচ্ছেন। ঘুম থেকে উঠলেই আমি জানাব যে আপনি কল করেছিলেন। খুব জরুরি হলে আরেকবার কল করুন।$SIG_BN",
            "Hello! {name} is resting right now. I'll let {name} know you called. If it's urgent, please call again.$SIG_EN"
        ),
        LifeMode(
            "prayer", "🕌", "Prayer", "During namaz · automatic at prayer times",
            "আসসালামু আলাইকুম। {name} এখন নামাজে আছেন। নামাজ শেষে আমি ওনাকে জানাব, উনি আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Assalamu alaikum! {name} is at prayer right now. I'll let {name} know you called, and {name} will call you back after prayer.$SIG_EN"
        ),
        LifeMode(
            "meeting", "💼", "Meeting", "Busy at work · calls back after",
            "আসসালামু আলাইকুম। {name} এখন একটা মিটিংয়ে আছেন। মিটিং শেষে আপনাকে কল ব্যাক করবেন। জরুরি হলে আরেকবার কল করুন।$SIG_BN",
            "Hello! {name} is in a meeting right now and will call you back after it. If it's urgent, please call again.$SIG_EN"
        ),
        LifeMode(
            "focus", "📚", "Focus", "Study or deep work",
            "আসসালামু আলাইকুম। {name} এখন জরুরি একটা কাজে মনোযোগ দিচ্ছেন। একটু পরে আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Hello! {name} is focusing on something important right now and will call you back a little later.$SIG_EN"
        ),
        LifeMode(
            "gym", "🏋️", "Workout", "At the gym or exercising",
            "আসসালামু আলাইকুম। {name} এখন ব্যায়াম করছেন। শেষ হলেই আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Hello! {name} is working out right now and will call you back soon.$SIG_EN"
        ),
        LifeMode(
            "event", "🎬", "Event", "Cinema, wedding, class…",
            "আসসালামু আলাইকুম। {name} এখন একটা অনুষ্ঠানে আছেন। পরে আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Hello! {name} is at an event right now and will call you back later.$SIG_EN"
        ),
        LifeMode(
            "gaming", "🎮", "Gaming", "No interruptions while playing",
            "আসসালামু আলাইকুম। {name} এখন একটু ব্যস্ত আছেন। পরে আপনাকে কল ব্যাক করবেন।$SIG_BN",
            "Hello! {name} is busy right now and will call you back later.$SIG_EN"
        )
    )

    /** A user-made mode: one message, used for both languages. */
    fun custom(name: String, emoji: String, message: String) = LifeMode(
        id = "custom-" + UUID.randomUUID().toString().take(8), emoji = emoji.ifBlank { "⭐" }, name = name,
        subtitle = "Your own mode", messageBn = message, messageEn = message, builtIn = false
    )

    /** Which mode is on right now. A mode switched on by hand wins; then the automatic ones. */
    fun effective(
        manualId: String?, manualUntil: Long, now: Long,
        carConnected: Boolean, autoDriving: Boolean,
        gameInFront: Boolean, autoGaming: Boolean,
        inPrayerTime: Boolean, autoPrayer: Boolean,
        sleepEnabled: Boolean, minuteOfDay: Int, sleepStart: Int, sleepEnd: Int
    ): String? = when {
        manualId != null && (manualUntil == 0L || now < manualUntil) -> manualId
        autoDriving && carConnected -> "driving"
        autoGaming && gameInFront -> "gaming"
        autoPrayer && inPrayerTime -> "prayer"
        sleepEnabled && inWindow(minuteOfDay, sleepStart, sleepEnd) -> "sleeping"
        else -> null
    }

    /** start..end in minutes of the day; may cross midnight (23:00–07:00). */
    fun inWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    /** Prayer mode for [lengthMin] minutes from each prayer time (minutes of day). */
    fun inPrayerWindow(minute: Int, prayerTimes: List<Int>, lengthMin: Int = 20): Boolean =
        prayerTimes.any { t -> inWindow(minute, t, (t + lengthMin) % (24 * 60)) }

    /** "HH:mm" (Aladhan returns e.g. "04:31" or "04:31 (+06)") → minutes of day. */
    fun parseClock(s: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** What to do with a ringing call in a mode. */
    enum class CallAction { LET_RING_VIP, LET_RING_REPEAT, DECLINE }

    fun decideCall(isVip: Boolean, lastDeclinedAt: Long?, now: Long): CallAction = when {
        isVip -> CallAction.LET_RING_VIP
        lastDeclinedAt != null && now - lastDeclinedAt < REPEAT_CALL_MS -> CallAction.LET_RING_REPEAT
        else -> CallAction.DECLINE
    }

    /** The text for the caller. [lang] "bn", "en" or "both". */
    fun message(mode: LifeMode, lang: String, userName: String, assistantName: String, override: String? = null): String {
        val first = userName.trim().substringBefore(' ')
        fun fill(t: String, bn: Boolean): String {
            val name = first.ifEmpty { if (bn) "উনি" else "The person you called" }
            return t.replace("{name}-এর", if (first.isEmpty()) "ওনার" else "$first-এর")
                .replace("{name}'s", if (first.isEmpty()) "their" else "$first's")
                .replace("{name}", name).replace("{ai}", assistantName)
        }
        if (!override.isNullOrBlank()) return fill(override, bn = lang != "en")
        return when (lang) {
            "en" -> fill(mode.messageEn, false)
            "both" -> fill(mode.messageBn, true) + "\n\n" + fill(mode.messageEn, false)
            else -> fill(mode.messageBn, true)
        }
    }

    /** "While you were driving: Rahim called 2 times, 01711… once." */
    fun summary(calls: List<ModeCall>): String {
        if (calls.isEmpty()) return "No one called."
        return calls.groupBy { it.name.ifBlank { it.number } }.entries.joinToString(", ") { (who, list) ->
            "$who " + if (list.size == 1) "called once" else "called ${list.size} times"
        }
    }
}
