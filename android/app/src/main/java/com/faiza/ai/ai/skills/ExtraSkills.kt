package com.faiza.ai.ai.skills

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Ideas from the STONIC video, made for a phone: settings control, teach-a-command, YouTube SEO help. */
object ExtraSkills {

    private val prefs get() = FaizaApp.instance.prefs

    fun skills(): List<Skill> = listOf(
        Skill(
            "phone_settings", "⚙️", "Phone settings", "\"Turn on Do Not Disturb\", \"Brightness 30\"",
            "Change phone settings: brightness (0-100), do not disturb, auto-rotate, ringer (normal/vibrate/silent), " +
                    "and Wi-Fi, Bluetooth, airplane mode, mobile data, hotspot, location, battery saver (these are switched " +
                    "from the quick settings panel).",
            listOf(
                ToolParam("setting", ParamType.STRING, "Which setting", enum = listOf(
                    "brightness", "do_not_disturb", "auto_rotate", "ringer", "wifi", "bluetooth", "airplane_mode",
                    "mobile_data", "hotspot", "location", "battery_saver"
                )),
                ToolParam("value", ParamType.STRING, "on / off, a number for brightness, or normal/vibrate/silent for ringer")
            )
        ) { ctx, args -> setting(ctx, args.require("setting"), args.require("value").lowercase()) },

        Skill(
            "manage_call", "📲", "Answer & end calls", "\"Pick up\", \"Cut the call\"",
            "Control a phone call: answer a ringing call, reject it, end the current call, or switch the call to speaker.",
            listOf(ToolParam("action", ParamType.STRING, "What to do", enum = listOf("answer", "reject", "end", "speaker_on", "speaker_off")))
        ) { ctx, args -> manageCall(ctx, args.require("action")) },

        Skill(
            "quiet_mode", "🌙", "Quiet mode", "\"I'm going out, stay quiet\"",
            "Quiet mode on: you stop speaking on your own (no announcements, reminders out loud or proactive chat) and " +
                    "stop always-on listening, e.g. when the user goes out or is in a meeting. Off: everything back on.",
            listOf(
                ToolParam("on", ParamType.BOOLEAN, "true = go quiet, false = everything back on"),
                ToolParam("minutes", ParamType.INTEGER, "Only for this many minutes (empty = until switched off)", required = false)
            )
        ) { _, args ->
            val on = args.bool("on")
            val minutes = args.int("minutes")?.takeIf { it in 1..(24 * 60) }
            com.faiza.ai.util.QuietMode.set(on, minutes)
            if (on) {
                val until = minutes?.let { " for $it minutes" } ?: ""
                "OK: quiet mode is on$until. Say only one very short goodbye (e.g. \"Okay, I'll stay quiet — tap me when " +
                        "you need me.\") and nothing more; your microphone switches off right after."
            } else "OK: everything is back on — announcements, reminders and listening. Say so in one short sentence."
        },

        Skill(
            "set_mood", "💗", "Mood", "Notices how you feel",
            "Quietly note the user's current mood when it's clear from what they say or how they sound (shown on the " +
                    "home screen). Don't announce it.",
            listOf(
                ToolParam("mood", ParamType.STRING, "Their mood", enum = listOf(
                    "happy", "excited", "calm", "loving", "tired", "sleepy", "sad", "stressed", "angry", "sick", "bored"
                ))
            )
        ) { _, args ->
            prefs.userMood = args.require("mood")
            "OK: mood noted (don't mention it unless it helps)."
        },

        Skill(
            "save_journal", "📔", "Journal", "\"Write in my journal\"",
            "Save a short journal entry about the user's day (in their words, first person, 1-4 sentences).",
            listOf(ToolParam("entry", ParamType.STRING, "The journal entry"))
        ) { _, args ->
            val entry = args.require("entry")
            val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
            com.faiza.ai.ai.memory.MemoryStore.remember("Journal $date: $entry".take(300))
            com.faiza.ai.util.ActivityLog.add("journal", "Journal · $date", entry)
            "OK: saved in the journal for $date."
        },

        Skill(
            "teach_command", "🧑‍🏫", "Teach a command", "\"When I say set up my phone, …\"",
            "Save a new voice command: when the user later says the trigger, you do the instructions " +
                    "(e.g. trigger \"set up my phone\" → \"Set volume to 40%, tell the weather, open YouTube Music\").",
            listOf(
                ToolParam("trigger", ParamType.STRING, "What the user will say (several ways separated by /)"),
                ToolParam("instructions", ParamType.STRING, "What you should do then, step by step"),
                ToolParam("name", ParamType.STRING, "Short name, e.g. \"Phone setup\"", required = false)
            )
        ) { _, args ->
            val trigger = args.require("trigger")
            val skill = CustomSkill(
                name = args.str("name") ?: trigger.substringBefore('/').trim().replaceFirstChar { it.uppercase() },
                trigger = trigger,
                instructions = args.require("instructions")
            )
            val existing = UserSkillStore.customSkills(prefs).filterNot { it.trigger.equals(trigger, ignoreCase = true) }
            UserSkillStore.saveCustomSkills(prefs, existing + skill)
            "OK: learned \"${skill.name}\". From now on, when the user says \"$trigger\", do: ${skill.instructions}. " +
                    "(It's listed in Skills → Custom skills.)"
        },

        Skill(
            "youtube_video_details", "🎬", "YouTube SEO help", "\"Improve my latest video's SEO\"",
            "Get the title, description and tags of the user's latest YouTube video (or one whose title contains the " +
                    "words), so you can write a better title, description and tags. Read your suggestions to the user; " +
                    "only if they approve, apply them in the YouTube Studio app with read_screen, tap and type_text.",
            listOf(ToolParam("title_words", ParamType.STRING, "Words from the video title (empty = latest)", required = false))
        ) { _, args -> videoDetails(args.str("title_words")) }
    )

    // ===== Calls =====

    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun manageCall(ctx: Context, action: String): String {
        val granted = ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted && action in setOf("answer", "reject", "end")) {
            return "NEEDS PERMISSION: allow \"Answer & manage calls\" in Faiza AI → Settings → Phone access, then try again."
        }
        val tm = ctx.getSystemService(android.telecom.TelecomManager::class.java) ?: return "ERROR: no phone service"
        return when (action) {
            "answer" -> { tm.acceptRingingCall(); "OK: call answered" }
            "reject", "end" -> {
                if (Build.VERSION.SDK_INT < 28) return "ERROR: ending calls needs Android 9 or newer"
                if (tm.endCall()) "OK: call ${if (action == "reject") "rejected" else "ended"}" else "There's no call to end."
            }
            "speaker_on", "speaker_off" -> {
                val am = ctx.getSystemService(AudioManager::class.java) ?: return "ERROR: unavailable"
                am.isSpeakerphoneOn = action == "speaker_on"
                "OK: speaker ${if (action == "speaker_on") "on" else "off"}"
            }
            else -> "ERROR: unknown action $action"
        }
    }

    // ===== Phone settings =====

    private fun on(value: String) = value in setOf("on", "true", "enable", "yes", "1", "চালু", "অন", "चालू", "ऑन")

    private suspend fun setting(ctx: Context, setting: String, value: String): String {
        when (setting) {
            "brightness" -> {
                if (!Settings.System.canWrite(ctx)) return needWriteSettings(ctx)
                val pct = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100)
                    ?: return "ERROR: give brightness as a number from 0 to 100"
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (pct * 255 / 100).coerceIn(1, 255))
                return "OK: brightness $pct%"
            }
            "auto_rotate" -> {
                if (!Settings.System.canWrite(ctx)) return needWriteSettings(ctx)
                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on(value)) 1 else 0)
                return "OK: auto-rotate ${if (on(value)) "on" else "off"}"
            }
            "do_not_disturb" -> {
                val nm = ctx.getSystemService(NotificationManager::class.java) ?: return "ERROR: unavailable"
                if (!nm.isNotificationPolicyAccessGranted) return needDndAccess(ctx)
                nm.setInterruptionFilter(
                    if (on(value)) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                )
                return "OK: Do Not Disturb ${if (on(value)) "on" else "off"}"
            }
            "ringer" -> {
                val am = ctx.getSystemService(AudioManager::class.java) ?: return "ERROR: unavailable"
                val mode = when {
                    value.contains("vib") -> AudioManager.RINGER_MODE_VIBRATE
                    value.contains("silent") || value == "off" -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (mode == AudioManager.RINGER_MODE_SILENT && nm?.isNotificationPolicyAccessGranted != true) return needDndAccess(ctx)
                am.ringerMode = mode
                return "OK: ringer ${listOf("silent", "vibrate", "normal")[mode]}"
            }
        }
        // The rest can't be switched by apps directly on new Android: use the quick settings tiles like a finger.
        val labels = when (setting) {
            "wifi" -> listOf("Wi-Fi", "Wi‑Fi", "WiFi", "Internet", "ওয়াই-ফাই", "वाई-फ़ाई")
            "bluetooth" -> listOf("Bluetooth", "ব্লুটুথ", "ब्लूटूथ")
            "airplane_mode" -> listOf("Airplane mode", "Aeroplane mode", "Flight mode", "বিমান মোড", "हवाई जहाज़ मोड")
            "mobile_data" -> listOf("Mobile data", "Mobile Data", "Data", "মোবাইল ডেটা", "मोबाइल डेटा")
            "hotspot" -> listOf("Hotspot", "Mobile Hotspot", "হটস্পট", "हॉटस्पॉट")
            "location" -> listOf("Location", "লোকেশন", "স্থান", "लोकेशन")
            "battery_saver" -> listOf("Battery Saver", "Power saving", "Battery saver", "ব্যাটারি সেভার", "बैटरी सेवर")
            else -> return "ERROR: unknown setting $setting"
        }
        return toggleTile(ctx, setting, labels)
    }

    private suspend fun toggleTile(ctx: Context, setting: String, labels: List<String>): String {
        val a = FaizaAccessibilityService.instance
        if (a == null) {
            openPanel(ctx, setting)
            return "OK: opened the $setting settings — Accessibility is off, so the user has to switch it."
        }
        a.openQuickSettings()
        delay(900)
        val tile = labels.firstNotNullOfOrNull { label -> a.findBest(label) { it.clickable } }
        if (tile == null) {
            a.pressBack()
            delay(300)
            openPanel(ctx, setting)
            return "OK: couldn't find the $setting tile in quick settings, so its settings page is open for the user."
        }
        a.tapElement(tile)
        delay(700)
        a.pressBack()
        delay(250)
        a.pressBack()
        return "OK: tapped the ${tile.label} tile (was ${tile.role}). Tell the user it's switched."
    }

    private fun openPanel(ctx: Context, setting: String) {
        val action = when (setting) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "mobile_data" -> if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "airplane_mode" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "battery_saver" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_WIRELESS_SETTINGS
        }
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun needWriteSettings(ctx: Context): String {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return "NEEDS PERMISSION: I opened the screen to allow \"Modify system settings\" for Faiza AI. Ask the user to " +
                "switch it on, then try again."
    }

    private fun needDndAccess(ctx: Context): String {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return "NEEDS PERMISSION: I opened \"Do Not Disturb access\". Ask the user to allow Faiza AI there, then try again."
    }

    // ===== YouTube SEO =====

    private suspend fun videoDetails(titleWords: String?): String = withContext(Dispatchers.IO) {
        val handle = prefs.youtubeHandle.trim()
        val key = prefs.youtubeApiKey.trim()
        if (handle.isEmpty() || key.isEmpty()) {
            return@withContext "ERROR: add the YouTube channel and YouTube API key in Settings → About you first."
        }
        val api = "https://www.googleapis.com/youtube/v3"
        val at = if (handle.startsWith("@")) handle else "@$handle"
        val uploads = get("$api/channels?part=contentDetails&forHandle=${Uri.encode(at)}&key=$key")
            .getAsJsonArray("items")?.firstOrNull()?.asJsonObject
            ?.getAsJsonObject("contentDetails")?.getAsJsonObject("relatedPlaylists")?.get("uploads")?.asString
            ?: return@withContext "ERROR: no YouTube channel found for $at"
        val ids = get("$api/playlistItems?part=contentDetails&maxResults=15&playlistId=$uploads&key=$key")
            .getAsJsonArray("items")?.mapNotNull {
                it.asJsonObject.getAsJsonObject("contentDetails")?.get("videoId")?.asString
            }.orEmpty()
        if (ids.isEmpty()) return@withContext "The channel has no public videos yet."
        val videos = get("$api/videos?part=snippet,statistics&id=${ids.joinToString(",")}&key=$key")
            .getAsJsonArray("items")?.map { it.asJsonObject }.orEmpty()
        val words = titleWords?.lowercase()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
        val v = videos.firstOrNull { o ->
            words.isEmpty() || words.all { w -> o.getAsJsonObject("snippet")?.get("title")?.asString?.lowercase()?.contains(w) == true }
        } ?: return@withContext "ERROR: none of the latest 15 videos has \"$titleWords\" in its title."
        val s = v.getAsJsonObject("snippet")
        val tags = s.getAsJsonArray("tags")?.joinToString(", ") { it.asString }.orEmpty().ifEmpty { "(none)" }
        val views = v.getAsJsonObject("statistics")?.get("viewCount")?.asString ?: "?"
        "Video: ${s.get("title")?.asString}\nPublished: ${s.get("publishedAt")?.asString?.take(10)} · $views views\n" +
                "Tags: $tags\nDescription:\n${s.get("description")?.asString.orEmpty().take(3_000)}\n\n" +
                "Now write: a better title (under 70 characters, main keyword first), a description (hook in the first " +
                "two lines, then details, keywords and 3-5 hashtags) and 10-15 tags — in the video's language and in " +
                "the style of the user's channel. Read them to the user and ask before applying anything."
    }

    private fun get(url: String): JsonObject =
        AIProviders.http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val json = runCatching { JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject }.getOrNull()
            if (!r.isSuccessful) {
                throw SkillException("YouTube: " + (json?.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${r.code}"))
            }
            json ?: throw SkillException("YouTube sent an unreadable reply")
        }
}
