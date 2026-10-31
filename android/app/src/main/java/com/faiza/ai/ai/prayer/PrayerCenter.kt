package com.faiza.ai.ai.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.routines.RoutineCenter
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.sos.LocationHelper
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.PrayerActivity
import java.util.Calendar
import java.util.TimeZone

/** Today's times for where the user is, azan / sehri / iftar reminders, and the voice tool. */
object PrayerCenter {

    private val prefs get() = FaizaApp.instance.prefs

    data class Place(val lat: Double, val lng: Double, val label: String)

    /** Last known location (saved for next time), else the last saved one, else Dhaka. */
    fun place(ctx: Context): Place {
        LocationHelper.lastKnown(ctx)?.let { loc ->
            prefs.prayerLat = loc.latitude.toFloat(); prefs.prayerLng = loc.longitude.toFloat()
            return Place(loc.latitude, loc.longitude, "your location")
        }
        if (prefs.prayerLat != 0f || prefs.prayerLng != 0f) return Place(prefs.prayerLat.toDouble(), prefs.prayerLng.toDouble(), "last known location")
        return Place(23.8103, 90.4125, "Dhaka (turn on location for your own area)")
    }

    private fun tzHours(cal: Calendar): Double = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0

    fun timesFor(ctx: Context, cal: Calendar = Calendar.getInstance()): PrayerCalc.Times {
        val p = place(ctx)
        return PrayerCalc.times(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), p.lat, p.lng, tzHours(cal),
            PrayerCalc.Method.from(prefs.prayerMethod), if (prefs.prayerHanafi) 2 else 1
        )
    }

    fun hijri(cal: Calendar = Calendar.getInstance()): PrayerCalc.Hijri =
        PrayerCalc.hijri(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), prefs.hijriAdjust)

    fun isRamadanSoon(cal: Calendar = Calendar.getInstance()): Boolean {
        val h = hijri(cal)
        return h.isRamadan || (h.month == 8 && h.day >= 28)
    }

    /** Minutes of day → the next prayer after now: (name, minuteOfDay, isTomorrow). */
    fun next(ctx: Context): Triple<String, Int, Boolean> {
        val now = Calendar.getInstance()
        val m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val today = timesFor(ctx, now).prayers()
        today.firstOrNull { it.second > m }?.let { return Triple(it.first, it.second, false) }
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val f = timesFor(ctx, tomorrow).fajr
        return Triple("Fajr", f, true)
    }

    // ---------------- Reminders ----------------

    data class Reminder(val at: Long, val kind: String, val prayer: String)

    /** Every reminder in the next ~2 days (azan per prayer, sehri 30 min before Fajr, iftar at Maghrib in Ramadan). */
    fun upcoming(ctx: Context, now: Long = System.currentTimeMillis()): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (dayOffset in 0..1) {
            val cal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, dayOffset) }
            val t = timesFor(ctx, cal)
            fun at(min: Int): Long = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MINUTE, min)
            }.timeInMillis
            if (prefs.azanReminders) t.prayers().forEach { (name, min) ->
                if (min >= 0) out += Reminder(at(min) - prefs.azanBeforeMin * 60_000L, "azan", name)
            }
            if (prefs.ramadanReminders && isRamadanSoon(cal)) {
                if (t.fajr >= 0) out += Reminder(at(t.fajr - 30), "sehri", "Fajr")
                if (t.maghrib >= 0) out += Reminder(at(t.maghrib), "iftar", "Maghrib")
            }
        }
        return out.filter { it.at > now + 5_000 }.sortedBy { it.at }
    }

    private fun pending(ctx: Context, r: Reminder?): PendingIntent = PendingIntent.getBroadcast(
        ctx, 4401,
        Intent(ctx, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            if (r != null) { putExtra("kind", r.kind); putExtra("prayer", r.prayer); putExtra("at", r.at) }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Sets the one alarm for the next reminder (each firing sets the following one). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = upcoming(ctx).firstOrNull()
        if (next == null) { am.cancel(pending(ctx, null)); return }
        val pi = pending(ctx, next)
        runCatching {
            if (RoutineCenter.canUseExactAlarms(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi)
        }
    }

    fun line(kind: String, prayer: String, bangla: Boolean, before: Int): String {
        val bn = PrayerCalc.NAMES_BN[prayer] ?: prayer
        return if (bangla) when (kind) {
            "sehri" -> "সেহরির সময় শেষ হতে আর ৩০ মিনিট বাকি। এখনই সেহরি খেয়ে নিন।"
            "iftar" -> "ইফতারের সময় হয়েছে। আল্লাহুম্মা লাকা সুমতু… ইফতার করে নিন।"
            else -> if (before > 0) "$bn নামাজের আর $before মিনিট বাকি।" else "$bn-এর ওয়াক্ত শুরু হয়েছে। নামাজের সময় হয়েছে।"
        } else when (kind) {
            "sehri" -> "30 minutes left for Sehri. Please finish eating now."
            "iftar" -> "It's time for Iftar."
            else -> if (before > 0) "$prayer prayer in $before minutes." else "It's time for $prayer prayer."
        }
    }

    fun fire(ctx: Context, kind: String, prayer: String) {
        val bangla = com.faiza.ai.ai.AppLanguage.from(prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI
        val text = line(kind, prayer, bangla, if (kind == "azan") prefs.azanBeforeMin else 0)
        val open = PendingIntent.getActivity(
            ctx, 4402, Intent(ctx, PrayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(if (kind == "azan") "🕌 ${PrayerCalc.NAMES_BN[prayer] ?: prayer}" else if (kind == "sehri") "🌙 সেহরি" else "🌅 ইফতার")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4403, n) }
        val sound = prefs.azanSoundUri
        if (kind == "azan" && prefs.azanBeforeMin == 0 && !sound.isNullOrBlank()) playAzan(ctx, Uri.parse(sound))
        else FloatingOrbService.instance?.announceEvent(text)
    }

    private var player: MediaPlayer? = null

    /** The azan the user picked (their own audio file). */
    fun playAzan(ctx: Context, uri: Uri) {
        stopAzan()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(ctx.applicationContext, uri)
                setOnCompletionListener { stopAzan() }
                prepare(); start()
            }
        }.getOrNull()
    }

    fun stopAzan() { player?.runCatching { stop(); release() }; player = null }

    const val ACTION_FIRE = "com.faiza.ai.PRAYER_FIRE"

    // ---------------- Voice tool ----------------

    fun summary(ctx: Context): String {
        val t = timesFor(ctx)
        val h = hijri()
        val p = place(ctx)
        val (nName, nMin, tomorrow) = next(ctx)
        return buildString {
            appendLine("Hijri: ${h.day} ${h.monthName} ${h.year} (${h.day} ${h.monthNameBn}) — may differ by a day from local moon sighting.")
            appendLine("Place: ${p.label}")
            t.list().forEach { (n, m) -> appendLine("${PrayerCalc.NAMES_BN[n]} ($n): ${PrayerCalc.clock(m)}") }
            appendLine("NEXT: $nName at ${PrayerCalc.clock(nMin)}" + if (tomorrow) " (tomorrow)" else "")
            if (isRamadanSoon()) appendLine("RAMADAN: Sehri ends ${PrayerCalc.clock(t.fajr)} (finish a few minutes earlier), Iftar ${PrayerCalc.clock(t.maghrib)}")
        }
    }

    fun skill() = Skill(
        "prayer", "🕌", "Prayer times", "\"আজ নামাজের সময়?\" · \"আসর কখন?\" · \"ইফতার কয়টায়?\" · \"কিবলা কোন দিকে?\"",
        "Prayer times worked out for the user's location (no internet): Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha, the next " +
                "prayer, Hijri date, Ramadan Sehri/Iftar times, and the Qibla direction. action=today | qibla | open " +
                "(the prayer screen with the compass) | reminders_on | reminders_off.",
        listOf(ToolParam("action", ParamType.STRING, "today | qibla | open | reminders_on | reminders_off"))
    ) { ctx, args ->
        when (args.require("action").lowercase()) {
            "qibla" -> {
                val p = place(ctx)
                val q = PrayerCalc.qibla(p.lat, p.lng)
                "QIBLA: ${q.toInt()}° from north (clockwise) at ${p.label}. From Bangladesh that's west, a little to the north. " +
                        "Offer to open the compass (action=open) to point the phone exactly."
            }
            "open" -> {
                ctx.startActivity(Intent(ctx, PrayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "The prayer screen with the Qibla compass is open."
            }
            "reminders_on" -> { prefs.azanReminders = true; schedule(ctx); "Azan reminders are on for all five prayers." }
            "reminders_off" -> { prefs.azanReminders = false; schedule(ctx); "Azan reminders are off." }
            else -> summary(ctx)
        }
    }
}

/** Fires each reminder and sets the next one; also re-sets after a reboot or update. */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == PrayerCenter.ACTION_FIRE) {
            val kind = intent.getStringExtra("kind")
            val prayer = intent.getStringExtra("prayer")
            val at = intent.getLongExtra("at", 0L)
            // Ignore an alarm that arrives very late (phone was off): just set the next one.
            if (kind != null && prayer != null && System.currentTimeMillis() - at < 15 * 60_000L) {
                runCatching { PrayerCenter.fire(ctx, kind, prayer) }
            }
        }
        runCatching { PrayerCenter.schedule(ctx) }
    }
}
