package com.faiza.ai.ai.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.str
import com.faiza.ai.service.FloatingOrbService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** A "tell me in N seconds/minutes" timer that Faiza herself announces. */
data class FaizaTimer(val id: String, val endsAt: Long, val message: String, val label: String, val seconds: Int)

/**
 * Faiza's own timers (owner: "১৫ সেকেন্ড পর জানিও" — she said nothing, then claimed only 15 s had passed). She
 * speaks when it's done, even after the conversation ended, and always knows the real time and what's left.
 */
object FaizaTimers {

    private val gson = Gson()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val prefs get() = FaizaApp.instance.prefs

    fun active(): List<FaizaTimer> = runCatching {
        gson.fromJson<List<FaizaTimer>>(prefs.faizaTimersJson ?: return emptyList(), object : TypeToken<List<FaizaTimer>>() {}.type)
    }.getOrNull().orEmpty().filter { it.endsAt > System.currentTimeMillis() - 60_000 }

    private fun save(list: List<FaizaTimer>) {
        prefs.faizaTimersJson = gson.toJson(list)
    }

    /** "15 seconds" / "2 minutes 30 seconds" / "1 hour 5 minutes". */
    fun durationText(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return listOfNotNull(
            if (h > 0) "$h hour" + (if (h > 1) "s" else "") else null,
            if (m > 0) "$m minute" + (if (m > 1) "s" else "") else null,
            if (s > 0) "$s second" + (if (s > 1) "s" else "") else null
        ).joinToString(" ").ifEmpty { "0 seconds" }
    }

    fun schedule(ctx: Context, seconds: Int, message: String?, label: String?): FaizaTimer {
        val t = FaizaTimer(
            id = UUID.randomUUID().toString().take(8),
            endsAt = System.currentTimeMillis() + seconds * 1000L,
            message = message?.takeIf { it.isNotBlank() }
                ?: "${prefs.userAddress.ifBlank { "Boss" }}, your ${durationText(seconds)} are up!",
            label = label?.takeIf { it.isNotBlank() } ?: durationText(seconds),
            seconds = seconds
        )
        save(active() + t)
        // In-app timer for exact short timers; the alarm also wakes the phone if Faiza was closed.
        main.postDelayed({ fire(ctx.applicationContext, t.id) }, seconds * 1000L)
        runCatching {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = pending(ctx, t.id)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endsAt, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endsAt, pi)
        }
        return t
    }

    private fun pending(ctx: Context, id: String) = PendingIntent.getBroadcast(
        ctx, id.hashCode(), Intent(ctx, FaizaTimerReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Time's up (from the in-app timer or the alarm, whichever comes first — only once). */
    @Synchronized
    fun fire(ctx: Context, id: String) {
        val list = active()
        val t = list.firstOrNull { it.id == id } ?: return
        save(list.filter { it.id != id })
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id)) }
        val n = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("⏱️ ${t.label} — time's up")
            .setContentText(t.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id.hashCode(), n) }
        FloatingOrbService.instance?.deliverTimer(t.message)
    }

    fun cancel(ctx: Context, id: String?): Int {
        val list = active()
        val gone = if (id == null) list else list.filter { it.id == id || it.label.equals(id, ignoreCase = true) }
        gone.forEach { t -> runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, t.id)) } }
        save(list - gone.toSet())
        return gone.size
    }

    fun skills(): List<Skill> = listOf(
        Skill(
            "remind_in", "⏱️", "Tell me in…", "\"১৫ সেকেন্ড পর জানিও\"",
            "When the user says \"tell me / remind me / জানিও / মনে করিয়ে দিও in N seconds/minutes/hours\": YOU will say it " +
                    "out loud at exactly that time (even if the conversation has ended). Give the total in seconds " +
                    "(15 s → 15, 10 min → 600). Write message as the exact words to say then, in the user's language " +
                    "(e.g. \"বস, ১৫ সেকেন্ড হয়ে গেছে!\" or \"বস, এখন ওষুধ খাওয়ার সময়!\"). Use set_timer only for a ringing Clock-app timer.",
            listOf(
                ToolParam("seconds", ParamType.INTEGER, "How long from now, in seconds (1 to 86400)"),
                ToolParam("message", ParamType.STRING, "What to say when the time is up, in the user's language"),
                ToolParam("label", ParamType.STRING, "Short name, e.g. \"tea\"", required = false)
            )
        ) { ctx, args ->
            val sec = args.int("seconds") ?: return@Skill "ERROR: missing seconds"
            if (sec !in 1..86_400) return@Skill "ERROR: between 1 second and 24 hours"
            val t = schedule(ctx, sec, args.str("message"), args.str("label"))
            "OK: in ${durationText(sec)} (at ${clock(t.endsAt)}) you will say: \"${t.message}\". Confirm in one short sentence."
        },
        Skill(
            "time_now", "🕐", "Time & timers", "\"কয়টা বাজে?\", \"কতক্ষণ বাকি?\"",
            "The real current time and how long is left on each running timer / how long ago it started. Call this " +
                    "whenever the user asks the time, how long it's been, or how long is left — never guess.",
            emptyList()
        ) { _, _ ->
            val now = System.currentTimeMillis()
            val timers = active().filter { it.endsAt > now }
            "Now: " + SimpleDateFormat("EEEE d MMMM yyyy, h:mm:ss a", Locale.ENGLISH).format(Date(now)) +
                    if (timers.isEmpty()) ". No timers running." else ". Timers: " + timers.joinToString("; ") {
                        val left = ((it.endsAt - now) / 1000).toInt()
                        "${it.label}: ${durationText(left)} left (started ${durationText(it.seconds - left)} ago)"
                    }
        },
        Skill(
            "cancel_reminder", "🚫", "Cancel a timer", "\"টাইমার বাদ দাও\"",
            "Cancel a timer made with remind_in (by its label), or all of them.",
            listOf(ToolParam("label", ParamType.STRING, "Which one; empty = all", required = false))
        ) { ctx, args ->
            val n = cancel(ctx, args.str("label"))
            if (n == 0) "OK: no matching timers" else "OK: cancelled $n timer(s)"
        }
    )

    private fun clock(ms: Long) = SimpleDateFormat("h:mm:ss a", Locale.ENGLISH).format(Date(ms))
}

/** The alarm for a Faiza timer (works even when Faiza was closed). */
class FaizaTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("id")?.let { runCatching { FaizaTimers.fire(context, it) } }
    }
}
