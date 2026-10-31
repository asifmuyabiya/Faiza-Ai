package com.faiza.ai.ai.driving

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.modes.ModeCenter
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.ui.DrivingActivity

/**
 * Real driving (owner): a big-button screen, speed with a spoken warning, messages read out in full with a voice
 * reply, one-tap navigation home / to work. Calls are handled by the Driving life mode (decline + text).
 */
object Driving {

    private val prefs get() = FaizaApp.instance.prefs

    /** The driving screen is open. */
    @Volatile var screenOpen = false

    fun active(ctx: Context): Boolean = screenOpen || runCatching { ModeCenter.current(ctx)?.id == "driving" }.getOrDefault(false)

    /** "আম্মু WhatsApp-এ লিখেছে: কখন আসবি?" — long texts are cut so she doesn't read an essay at the wheel. */
    fun messageLine(bangla: Boolean, sender: String, app: String, text: String): String {
        val t = text.trim().replace(Regex("\\s+"), " ").let { if (it.length > 220) it.take(200).substringBeforeLast(' ') + "…" else it }
        return if (bangla) "$sender $app-এ লিখেছে: $t" else "$sender on $app: $t"
    }

    /** Spoken warning when the speed stays over the limit (km/h). Null = fine / already warned recently. */
    class SpeedWatch(private val limit: () -> Int, private val gapMs: Long = 45_000) {
        private var overSince = -1L // -1 = not over the limit
        private var lastWarn = -1L  // -1 = never warned
        fun onSpeed(kmh: Double, now: Long): Boolean {
            val lim = limit()
            if (lim <= 0 || kmh < lim + 3) { overSince = -1L; return false }
            if (overSince < 0) overSince = now
            if (now - overSince < 4_000 || (lastWarn >= 0 && now - lastWarn < gapMs)) return false
            lastWarn = now
            return true
        }
    }

    fun speedWarning(bangla: Boolean, kmh: Int, limit: Int): String =
        if (bangla) "সাবধান, স্পিড $kmh — $limit-এর বেশি। একটু আস্তে চালান।" else "Careful, you're at $kmh, over $limit. Please slow down."

    fun navigate(ctx: Context, destination: String): Boolean {
        if (destination.isBlank()) return false
        val q = Uri.encode(destination)
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage("com.google.android.apps.maps")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: ActivityNotFoundException) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        }
    }

    fun skill() = Skill(
        "driving", "🚗", "Driving", "\"আমি গাড়ি চালাচ্ছি\" · \"বাসায় নিয়ে চলো\" · \"স্পিড লিমিট ৬০ করো\"",
        "Driving assistant. action=start (big driving screen + Driving mode: calls get a text, messages read aloud) | " +
                "stop | home | work (navigate there) | set_home / set_work (place = address the user said) | " +
                "speed_limit (kmh; 0 = off). While driving keep every reply short; read messages and offer to reply.",
        listOf(
            ToolParam("action", ParamType.STRING, "start | stop | home | work | set_home | set_work | speed_limit"),
            ToolParam("place", ParamType.STRING, "address for set_home / set_work", required = false),
            ToolParam("kmh", ParamType.INTEGER, "speed limit", required = false)
        )
    ) { ctx, args ->
        when (args.require("action").lowercase()) {
            "start" -> {
                runCatching { ModeCenter.setManual(ctx, "driving") }
                ctx.startActivity(Intent(ctx, DrivingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Driving mode is on: big screen open, calls get a text, messages will be read out. Say one short line, wish a safe trip."
            }
            "stop" -> {
                runCatching { ModeCenter.setManual(ctx, null) }
                screenOpen = false
                "Driving mode is off."
            }
            "home", "work" -> {
                val place = if (args.require("action").lowercase() == "home") prefs.homeAddress else prefs.workAddress
                if (place.isBlank()) "ERROR: the ${args.str("action")} address isn't saved — ask the user for it and call set_${args.str("action")}."
                else if (navigate(ctx, place)) "Navigation to ${args.str("action")} ($place) started." else "Couldn't open Maps."
            }
            "set_home" -> { prefs.homeAddress = args.require("place"); "Home address saved." }
            "set_work" -> { prefs.workAddress = args.require("place"); "Work address saved." }
            "speed_limit" -> { prefs.drivingSpeedLimit = (args.int("kmh") ?: 0).coerceIn(0, 200); "Speed warning at ${prefs.drivingSpeedLimit} km/h." }
            else -> "ERROR: unknown action"
        }
    }
}
