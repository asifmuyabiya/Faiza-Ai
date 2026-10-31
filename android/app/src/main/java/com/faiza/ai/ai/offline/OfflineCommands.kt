package com.faiza.ai.ai.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.faiza.ai.ai.skills.BuiltInSkills
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * No internet (owner: "নেট না থাকলেও জরুরি কাজ"): the AI can't be reached, so simple commands are understood right
 * here and done with Faiza's own tools — time, date, battery, torch, call, alarm, timer, open an app, volume,
 * prayer time.
 */
object OfflineCommands {

    sealed class Cmd {
        object Time : Cmd()
        object Date : Cmd()
        object Battery : Cmd()
        data class Torch(val on: Boolean) : Cmd()
        data class Call(val who: String) : Cmd()
        data class Alarm(val hour: Int, val minute: Int) : Cmd()
        data class Timer(val seconds: Int) : Cmd()
        data class OpenApp(val app: String) : Cmd()
        data class Volume(val percent: Int) : Cmd()
        object Prayer : Cmd()
    }

    fun online(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    private fun digits(s: String) = s.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")

    private val NUM_WORDS = mapOf(
        "এক" to 1, "দুই" to 2, "তিন" to 3, "চার" to 4, "পাঁচ" to 5, "ছয়" to 6, "সাত" to 7, "আট" to 8, "নয়" to 9, "দশ" to 10,
        "এগারো" to 11, "বারো" to 12, "পনেরো" to 15, "বিশ" to 20, "ত্রিশ" to 30, "পঁয়তাল্লিশ" to 45,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30
    )

    private fun firstNumber(t: String): Int? {
        Regex("\\d+").find(t)?.let { return it.value.toIntOrNull() }
        return NUM_WORDS.entries.filter { Regex("(^|\\s)${it.key}").containsMatchIn(t) }.maxByOrNull { it.key.length }?.value
    }

    /** Understands one simple command (Bangla / English / Banglish); null = needs the AI. */
    fun parse(input: String): Cmd? {
        val t = digits(input.lowercase().trim())
        val has = { words: List<String> -> words.any { t.contains(it) } }
        return when {
            has(listOf("টর্চ", "torch", "flash", "ফ্ল্যাশ", "লাইট জ্বাল")) ->
                Cmd.Torch(!has(listOf("বন্ধ", "নিভাও", "off", "অফ")))
            has(listOf("টাইমার", "timer")) -> {
                val n = firstNumber(t) ?: return null
                val sec = when {
                    has(listOf("ঘণ্টা", "ঘন্টা", "hour")) -> n * 3600
                    has(listOf("সেকেন্ড", "second", "sec")) -> n
                    else -> n * 60
                }
                Cmd.Timer(sec)
            }
            has(listOf("অ্যালার্ম", "এলার্ম", "alarm", "জাগিয়ে", "ঘুম থেকে তুল")) -> parseClock(t)?.let { Cmd.Alarm(it.first, it.second) }
            has(listOf("কল দাও", "কল করো", "ফোন দাও", "ফোন করো", "call ")) -> {
                val who = t.replace(Regex("(কে|কেও)?\\s*(একটা\\s*)?(কল|ফোন)\\s*(দাও|করো|দে|লাগাও)|^call\\s+|please"), " ")
                    .replace(Regex("কে$|কে\\s"), " ").trim()
                if (who.isBlank()) null else Cmd.Call(who)
            }
            has(listOf("কয়টা বাজে", "কটা বাজে", "সময় কত", "what time", "time koto", "koyta baje")) -> Cmd.Time
            has(listOf("আজ কত তারিখ", "তারিখ কত", "what date", "what's the date", "আজকে কি বার", "কি বার")) -> Cmd.Date
            has(listOf("চার্জ", "ব্যাটারি", "battery", "charge")) -> Cmd.Battery
            has(listOf("নামাজ", "আজান", "prayer", "namaz", "ইফতার", "সেহরি")) -> Cmd.Prayer
            has(listOf("ভলিউম", "volume", "আওয়াজ", "সাউন্ড")) -> when {
                has(listOf("বাড়া", "বাড়া", "up", "জোরে", "বেশি")) -> Cmd.Volume(80)
                has(listOf("কমা", "down", "আস্তে", "কম")) -> Cmd.Volume(30)
                else -> firstNumber(t)?.let { Cmd.Volume(it.coerceIn(0, 100)) }
            }
            Regex("(খোলো|খুলো|চালু করো|open\\s)").containsMatchIn(t) -> {
                val app = t.replace(Regex("খোলো|খুলো|চালু করো|^open\\s+|অ্যাপ|app"), " ").trim()
                if (app.isBlank()) null else Cmd.OpenApp(app)
            }
            else -> null
        }
    }

    /** "সকাল ৬টায়", "রাত ১০টা ৩০", "6:30 am", "৭টায়" → (hour, minute). */
    fun parseClock(t0: String): Pair<Int, Int>? {
        val t = digits(t0.lowercase())
        val m = Regex("(\\d{1,2})\\s*(?::|\\.|টা|টায়|টায়|বাজে)?\\s*(\\d{1,2})?").find(t) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        var min = m.groupValues[2].toIntOrNull() ?: 0
        if (t.contains("সাড়ে") || t.contains("সাড়ে")) min = 30
        if (h > 23 || min > 59) return null
        val pm = listOf("pm", "বিকাল", "বিকেল", "সন্ধ্যা", "সন্ধ্যা", "রাত").any { t.contains(it) }
        val noon = t.contains("দুপুর")
        val am = listOf("am", "সকাল", "ভোর").any { t.contains(it) }
        h = when {
            h == 12 && am -> 0
            noon && h in 1..5 -> h + 12
            t.contains("রাত") && h in 1..3 -> h          // রাত ২টা = 2 AM
            pm && h in 1..11 -> h + 12
            else -> h
        }
        return h to min
    }

    /** Does it with the built-in tools; returns what to say (Bangla or English). */
    suspend fun run(ctx: Context, cmd: Cmd, bangla: Boolean): String {
        suspend fun tool(id: String, args: JsonObject): String =
            BuiltInSkills.all.firstOrNull { it.id == id }?.run?.invoke(ctx, args) ?: "ERROR"
        fun ok(r: String) = !r.startsWith("ERROR")
        return when (cmd) {
            Cmd.Time -> SimpleDateFormat("h:mm a", Locale.ENGLISH).format(java.util.Date()).let { if (bangla) "এখন $it বাজে।" else "It's $it." }
            Cmd.Date -> SimpleDateFormat("EEEE, d MMMM yyyy", if (bangla) Locale("bn") else Locale.ENGLISH).format(java.util.Date())
                .let { if (bangla) "আজ $it।" else "Today is $it." }
            Cmd.Battery -> tool("battery_status", JsonObject())
            is Cmd.Torch -> tool("flashlight", JsonObject().apply { addProperty("on", cmd.on) })
                .let { if (!ok(it)) it else if (bangla) (if (cmd.on) "টর্চ জ্বালালাম।" else "টর্চ বন্ধ করলাম।") else "Torch ${if (cmd.on) "on" else "off"}." }
            is Cmd.Call -> tool("call_contact", JsonObject().apply { addProperty("contact", cmd.who) })
                .let { if (ok(it) && bangla) "${cmd.who}-কে কল দিচ্ছি।" else it }
            is Cmd.Alarm -> tool("set_alarm", JsonObject().apply { addProperty("hour", cmd.hour); addProperty("minute", cmd.minute) })
                .let { if (ok(it) && bangla) "%d:%02d-এ অ্যালার্ম দিলাম।".format(cmd.hour, cmd.minute) else it }
            is Cmd.Timer -> tool("set_timer", JsonObject().apply { addProperty("seconds", cmd.seconds) })
                .let { if (ok(it) && bangla) "${cmd.seconds / 60} মিনিটের টাইমার চালু।" else it }
            is Cmd.OpenApp -> tool("open_app", JsonObject().apply { addProperty("app_name", cmd.app) })
            is Cmd.Volume -> tool("set_volume", JsonObject().apply { addProperty("percent", cmd.percent) })
            Cmd.Prayer -> runCatching {
                val (n, m, _) = com.faiza.ai.ai.prayer.PrayerCenter.next(ctx)
                val name = if (bangla) com.faiza.ai.ai.prayer.PrayerCalc.NAMES_BN[n] ?: n else n
                if (bangla) "পরের নামাজ $name, ${com.faiza.ai.ai.prayer.PrayerCalc.clock(m)}-এ।" else "Next prayer: $name at ${com.faiza.ai.ai.prayer.PrayerCalc.clock(m)}."
            }.getOrDefault("—")
        }
    }

    fun cantLine(bangla: Boolean) = if (bangla)
        "এখন ইন্টারনেট নেই। নেট ছাড়া আমি সময়, তারিখ, চার্জ, টর্চ, কল, অ্যালার্ম, টাইমার, অ্যাপ খোলা আর নামাজের সময় বলতে পারি।"
    else "No internet right now. Offline I can do: time, date, battery, torch, calls, alarms, timers, open apps and prayer times."

    @Suppress("unused") private val keepCalendar = Calendar.getInstance()
}
