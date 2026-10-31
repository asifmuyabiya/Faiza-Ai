package com.faiza.ai.ai.web

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.AddressOptions
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.sos.LocationHelper
import com.faiza.ai.service.FloatingOrbService
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Calendar

/** One hour of forecast. */
data class HourWx(val hour: Int, val tempC: Double, val feelsC: Double, val rainPct: Int, val code: Int)

/** A weather heads-up worth saying. [key] keeps the same alert from being repeated the same day. */
data class WxAlert(val key: String, val text: String)

/**
 * Weather heads-ups (owner: "আজ অনেক গরম… বিকেলে বৃষ্টি আসতে পারে, বাইরে কাজ থাকলে এখনই সেরে আসুন").
 * Pure: hourly forecast in, short spoken lines out. Unit-tested.
 */
object WeatherAdvisor {

    const val HOT_FEELS_C = 36.0
    const val RAIN_PCT = 60
    private val STORM_CODES = setOf(95, 96, 99)

    /** The morning summary for today's hours (from now until 21:00). */
    fun morning(hours: List<HourWx>, nowHour: Int, b: PhraseBase, addr: String): String? {
        val rest = hours.filter { it.hour in nowHour..21 }
        if (rest.isEmpty()) return null
        val hi = rest.maxOf { it.tempC }.toInt()
        val rain = rest.firstOrNull { it.rainPct >= RAIN_PCT }
        val who = addr
        return when (b) {
            PhraseBase.BENGALI -> "$who, আজকের আবহাওয়া: সর্বোচ্চ ${hi}° পর্যন্ত উঠবে" +
                    (rain?.let { "। ${clockBn(it.hour)} নাগাদ বৃষ্টি হতে পারে (${it.rainPct}%), ছাতা নিয়ে বের হবেন" } ?: "। বৃষ্টির সম্ভাবনা কম") + "।"
            PhraseBase.HINDI -> "$who, आज का मौसम: ${hi}° तक जाएगा" +
                    (rain?.let { "। ${it.hour} बजे के आसपास बारिश हो सकती है (${it.rainPct}%), छाता ले जाइए" } ?: "। बारिश के आसार कम हैं") + "।"
            PhraseBase.ENGLISH -> "$who, today's weather: up to ${hi}°" +
                    (rain?.let { ", and rain is likely around ${clockEn(it.hour)} (${it.rainPct}%) — take an umbrella" } ?: ", rain unlikely") + "."
        }
    }

    /** Heads-ups for the next few hours, most useful first. */
    fun alerts(hours: List<HourWx>, nowHour: Int, b: PhraseBase, addr: String, dayKey: String): List<WxAlert> {
        val next = hours.filter { it.hour in (nowHour + 1)..(nowHour + 4) }
        val nowWx = hours.firstOrNull { it.hour == nowHour }
        val out = mutableListOf<WxAlert>()

        // Storm coming.
        next.firstOrNull { it.code in STORM_CODES }?.let { s ->
            out += WxAlert("$dayKey-storm", line(b,
                "$addr, ${clockBn(s.hour)} নাগাদ ঝড়-বজ্রবৃষ্টি হতে পারে। বাইরে থাকলে নিরাপদ জায়গায় থাকবেন, আর বাইরের কাজ থাকলে আগেই সেরে নিন।",
                "$addr, around ${s.hour} बजे आंधी-तूफ़ान आ सकता है। बाहर हों तो सुरक्षित जगह रहिए।",
                "$addr, a thunderstorm is likely around ${clockEn(s.hour)}. If you're out, stay somewhere safe — and finish outdoor jobs before then."))
        }
        // Rain soon while it's dry now → "go now".
        val dryNow = nowWx == null || nowWx.rainPct < 40
        next.firstOrNull { it.rainPct >= RAIN_PCT }?.let { r ->
            if (out.none { it.key.endsWith("storm") }) {
                out += WxAlert("$dayKey-rain-${r.hour}", if (dryNow) line(b,
                    "$addr, এখন তো শুকনো আছে, কিন্তু ${clockBn(r.hour)} নাগাদ বৃষ্টি আসতে পারে (${r.rainPct}%)। বাইরে কোনো কাজ থাকলে এখনই সেরে আসুন, আর ছাতা নিতে ভুলবেন না।",
                    "$addr, अभी सूखा है, लेकिन ${r.hour} बजे के आसपास बारिश हो सकती है (${r.rainPct}%)। बाहर का काम हो तो अभी निपटा लीजिए।",
                    "$addr, it's dry now but rain is likely around ${clockEn(r.hour)} (${r.rainPct}%). If you have anything to do outside, go now — and take an umbrella.")
                else line(b,
                    "$addr, বৃষ্টি চলবে মনে হচ্ছে (${r.rainPct}%)। বের হলে ছাতা নিয়ে যাবেন।",
                    "$addr, बारिश जारी रह सकती है (${r.rainPct}%)। बाहर जाएं तो छाता ले जाइए।",
                    "$addr, the rain looks set to continue (${r.rainPct}%). Take an umbrella if you go out."))
            }
        }
        // Very hot.
        (listOfNotNull(nowWx) + next).maxByOrNull { it.feelsC }?.takeIf { it.feelsC >= HOT_FEELS_C }?.let { h ->
            out += WxAlert("$dayKey-heat", line(b,
                "$addr, আজ অনেক গরম পড়েছে — অনুভূত তাপমাত্রা প্রায় ${h.feelsC.toInt()}°। বাইরে গেলে পানি সাথে রাখবেন, আর দুপুরের কড়া রোদটা এড়িয়ে চলবেন।",
                "$addr, आज बहुत गर्मी है — महसूस होने वाला तापमान करीब ${h.feelsC.toInt()}°। बाहर जाएं तो पानी साथ रखिए।",
                "$addr, it's very hot today — it feels like about ${h.feelsC.toInt()}°. Carry water if you go out and avoid the midday sun."))
        }
        return out
    }

    private fun line(b: PhraseBase, bn: String, hi: String, en: String) = when (b) {
        PhraseBase.BENGALI -> bn
        PhraseBase.HINDI -> hi
        PhraseBase.ENGLISH -> en
    }

    fun clockEn(h: Int) = when {
        h == 0 -> "12 am"; h < 12 -> "$h am"; h == 12 -> "12 pm"; else -> "${h - 12} pm"
    }

    fun clockBn(h: Int): String {
        val part = when (h) { in 4..11 -> "সকাল"; in 12..14 -> "দুপুর"; in 15..17 -> "বিকেল"; in 18..19 -> "সন্ধ্যা"; else -> "রাত" }
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$part ${h12}টা"
    }
}

/** Phone side: fetches the hourly forecast now and then, and says each heads-up once. */
object WeatherAlerts {

    private val prefs get() = FaizaApp.instance.prefs
    @Volatile private var lastFetchAt = 0L
    @Volatile private var busy = false

    /** Called from the service's minute loop; does nothing most of the time. */
    suspend fun tick(ctx: Context) {
        if (!prefs.isWeatherAlerts || busy) return
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour !in 7..21) return
        val now = System.currentTimeMillis()
        if (now - lastFetchAt < 90 * 60_000L) return
        val loc = LocationHelper.lastKnown(ctx) ?: return
        busy = true
        try {
            lastFetchAt = now
            val hours = fetch(loc.latitude, loc.longitude) ?: return
            val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
            val base = AppLanguage.from(prefs.language).base
            val addr = AddressOptions.localized(prefs.userAddress.ifBlank { "Boss" }, base)
            val said = prefs.weatherSaidKeys.split(',').filter { it.startsWith(dayKey) }.toMutableSet()

            val lines = mutableListOf<WxAlert>()
            if (hour in 7..10 && "$dayKey-morning" !in said) {
                WeatherAdvisor.morning(hours, hour, base, addr)?.let { lines += WxAlert("$dayKey-morning", it) }
            }
            lines += WeatherAdvisor.alerts(hours, hour, base, addr, dayKey).filter { it.key !in said }
            val pick = lines.firstOrNull() ?: return // one at a time; the next check can say the next one
            said += pick.key
            prefs.weatherSaidKeys = said.joinToString(",")
            say(ctx, pick.text)
        } finally {
            busy = false
        }
    }

    private fun say(ctx: Context, text: String) {
        val n = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_sun)
            .setContentTitle("Weather")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7901, n) }
        FloatingOrbService.instance?.announceEvent(text)
    }

    private suspend fun fetch(lat: Double, lon: Double): List<HourWx>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=1" +
                    "&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code"
            AIProviders.http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                val h = JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject.getAsJsonObject("hourly")
                val times = h.getAsJsonArray("time")
                val t = h.getAsJsonArray("temperature_2m")
                val f = h.getAsJsonArray("apparent_temperature")
                val p = h.getAsJsonArray("precipitation_probability")
                val c = h.getAsJsonArray("weather_code")
                (0 until times.size()).map { i ->
                    HourWx(
                        hour = times[i].asString.substringAfter('T').take(2).toInt(),
                        tempC = t[i].asDouble,
                        feelsC = f[i].asDouble,
                        rainPct = runCatching { p[i].asInt }.getOrDefault(0),
                        code = runCatching { c[i].asInt }.getOrDefault(0)
                    )
                }
            }
        }.getOrNull()
    }
}
