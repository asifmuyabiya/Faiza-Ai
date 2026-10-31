package com.faiza.ai.ai.skills

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.contacts.SimHelper
import com.faiza.ai.ai.macros.MacroCenter
import com.faiza.ai.ai.sos.SosManager
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FaizaAccessibilityService.Companion.normalize
import com.faiza.ai.service.FaizaNotificationListener
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale

object BuiltInSkills {

    private const val NOT_CONFIRMED =
        "NOT SENT. First read the recipient and the exact message back to the user and ask " +
                "\"Shall I send it?\". Only after they say yes, call this again with confirmed=true."

    private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val SEND_LABELS = listOf("Send", "পাঠান", "भेजें", "Enviar")
    private val SEARCH_LABELS = listOf("search", "অনুসন্ধান", "সার্চ", "खोजें", "ask meta ai or search")

    /** Safety skills: always available (even in the free trial), never switched off. */
    val safety: List<Skill> = listOf(
        Skill(
            "activate_sos", "🆘", "SOS", "\"SOS\", \"বাঁচাও\"",
            "EMERGENCY: start SOS mode — after a 5-second countdown, text the user's SOS contacts their " +
                    "location and then call them one by one. Use immediately if the user is in danger or asks for help."
        ) { ctx, _ -> activateSos(ctx) },
        Skill(
            "cancel_sos", "✋", "Cancel SOS", "\"Cancel SOS\"",
            "Cancel SOS mode (during the countdown, or stop further texts/calls)."
        ) { _, _ -> if (SosManager.cancel()) "OK: SOS cancelled" else "No SOS is running." }
    )

    private val ALT_NAMES = ToolParam(
        "alternate_names", ParamType.STRING,
        "Other spellings of the name, comma-separated, in BOTH Bengali/Hindi and English letters " +
                "(e.g. for \"চাঁদ\": \"Chand, Chaand, চাঁদ\")",
        required = false
    )

    val all: List<Skill> = listOf(
        // ===== Apps & people =====
        Skill(
            "open_app", "📱", "Open apps", "\"Open YouTube\", \"WhatsApp খোলো\"",
            "Open an installed app by its name.",
            listOf(ToolParam("app_name", ParamType.STRING, "App name, e.g. WhatsApp, YouTube, Camera"))
        ) { ctx, args -> openApp(ctx, args.require("app_name")) },

        Skill(
            "find_contact", "👥", "Find contacts", "\"Is Rahim in my contacts?\"",
            "Search the phone's contacts by name (any language). Returns the best matches with numbers.",
            listOf(ToolParam("name", ParamType.STRING, "Name as the user said it"), ALT_NAMES)
        ) { ctx, args ->
            val matches = ContactFinder.search(ctx, ContactFinder.queries(args.require("name"), args.str("alternate_names")))
            if (matches.isEmpty()) "No matching contacts."
            else matches.joinToString("\n") { "${it.name}: ${it.number}" }
        },

        Skill(
            "call_contact", "📞", "Phone calls", "\"Call Mom\", \"আম্মুকে ফোন দাও\"",
            "Place a phone call to a contact name or phone number. On dual-SIM phones, if it replies " +
                    "NEEDS_SIM_CHOICE, ask the user which SIM and call again with sim and remember_sim=true.",
            listOf(
                ToolParam("contact", ParamType.STRING, "Contact name or phone number"),
                ALT_NAMES,
                ToolParam("sim", ParamType.INTEGER, "1 or 2 — which SIM to call from (dual-SIM phones)", required = false),
                ToolParam("remember_sim", ParamType.BOOLEAN, "true = always use this SIM from now on", required = false)
            )
        ) { ctx, args ->
            callContact(ctx, args.require("contact"), args.str("alternate_names"), args.int("sim"), args.bool("remember_sim"))
        },

        Skill(
            "send_sms", "💬", "Send SMS", "Reads the message back before sending",
            "Send an SMS text message. Use confirmed=true when the user clearly said who and what (if they want to be asked first, the tool replies NOT SENT).",
            listOf(
                ToolParam("contact", ParamType.STRING, "Contact name or phone number"),
                ToolParam("message", ParamType.STRING, "Exact message text"),
                ToolParam("confirmed", ParamType.BOOLEAN, "true when the user clearly asked to send this (or confirmed it)"),
                ALT_NAMES
            )
        ) { ctx, args ->
            sendSms(ctx, args.require("contact"), args.str("alternate_names"), args.require("message"), args.bool("confirmed"))
        },

        Skill(
            "open_whatsapp_chat", "🟢", "Open WhatsApp chats", "\"চাঁদের চ্যাটে ঢুকো\"",
            "Open the WhatsApp chat of a person or group. Checks the user's My people list FIRST (names and nicknames " +
                    "like \"বউ\", uses their saved WhatsApp number directly — works even if WhatsApp has them under " +
                    "another name), then the phone book, then searches inside WhatsApp. Pass the name exactly as the user " +
                    "said it (\"বউ\", \"আইরিন\" — not translated), and the same name in English letters / English " +
                    "in alternate_names (\"wife, Bou\", \"Irin\").",
            listOf(ToolParam("contact", ParamType.STRING, "Person or group name as the user said it"), ALT_NAMES)
        ) { ctx, args -> openWhatsAppChat(ctx, args.require("contact"), args.str("alternate_names")) },

        Skill(
            "take_photo", "📸", "Take a photo", "\"আমার একটা ছবি তোলো\"",
            "Open the camera and take a photo: front camera for a selfie / \"আমার ছবি\", back camera for things around. " +
                    "Counts down so the user can pose, saves to the gallery (Pictures/Faiza AI) and shows it.",
            listOf(
                ToolParam("camera", ParamType.STRING, "front or back", required = false, enum = listOf("front", "back")),
                ToolParam("countdown", ParamType.INTEGER, "Seconds to count down (default 3)", required = false)
            )
        ) { ctx, args ->
            if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@Skill "ERROR: camera permission is off — ask the user to allow Camera for Faiza in Settings → Phone access"
            }
            val wait = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
            com.faiza.ai.ui.PhotoActivity.pending = wait
            launch(ctx, Intent(ctx, com.faiza.ai.ui.PhotoActivity::class.java)
                .putExtra(com.faiza.ai.ui.PhotoActivity.EXTRA_FRONT, args.str("camera") != "back")
                .putExtra(com.faiza.ai.ui.PhotoActivity.EXTRA_COUNTDOWN, (args.int("countdown") ?: 3).coerceIn(0, 10)))
            val uri = kotlinx.coroutines.withTimeoutOrNull(40_000) { wait.await() }
            if (uri != null) {
                com.faiza.ai.ai.files.FileSkills.setLastCreated("Faiza photo.jpg", uri)
                "OK: photo taken and saved to the gallery (Pictures/Faiza AI). To send it, use share_file with number 0."
            } else "ERROR: the photo wasn't taken (camera closed or busy)"
        },

        Skill(
            "press_send", "📨", "Press Send", "Sends what's typed in the open chat",
            "Tap the Send button in the chat that is open now (WhatsApp, Messenger, SMS…), after type_text.",
            emptyList(),
            needsAccessibility = true
        ) { _, _ ->
            pressSendVerified(accessibility(), "the open chat")
        },

        Skill(
            "whatsapp_message", "✉️", "WhatsApp messages", "Opens the chat, types & sends",
            "Send a WhatsApp message to a person or group. Use confirmed=true when the user clearly said who and what (if they want to be asked first, the tool replies NOT SENT). Finds the " +
                    "person in My people first (nicknames too, their WhatsApp number), like open_whatsapp_chat; pass " +
                    "the name as the user said it plus English-letter spellings in alternate_names.",
            listOf(
                ToolParam("contact", ParamType.STRING, "Person or group name, or phone number"),
                ToolParam("message", ParamType.STRING, "Exact message text"),
                ToolParam("confirmed", ParamType.BOOLEAN, "true when the user clearly asked to send this (or confirmed it)"),
                ALT_NAMES
            )
        ) { ctx, args ->
            whatsappMessage(ctx, args.require("contact"), args.str("alternate_names"), args.require("message"), args.bool("confirmed"))
        },

        // ===== Screen control =====
        Skill(
            "read_screen", "👁️", "Read the screen", "Sees every button and text",
            "Read the current screen as a numbered list of elements (buttons, texts, text boxes) with " +
                    "their positions. Call this before tapping, and again after actions to check the result.",
            needsAccessibility = true
        ) { _, _ -> accessibility().describeScreen() },

        Skill(
            "tap_element", "👆", "Tap exactly", "Taps the numbered item from read_screen",
            "Tap an element by its number from the most recent read_screen.",
            listOf(ToolParam("number", ParamType.INTEGER, "Element number from read_screen")),
            needsAccessibility = true
        ) { _, args ->
            val a = accessibility()
            val n = args.int("number") ?: throw SkillException("missing 'number'")
            val e = a.elementByNumber(n) ?: return@Skill "ERROR: no element [$n]. Call read_screen again."
            MacroCenter.noteTap(e, a)
            tapReport(a.tapVerified(e), "[$n] ${e.label}")
        },

        Skill(
            "tap_on", "🖱️", "Tap by name", "\"Tap on Settings\"",
            "Tap the on-screen item whose text best matches (exact matches preferred).",
            listOf(ToolParam("text", ParamType.STRING, "Visible text or label of the item")),
            needsAccessibility = true
        ) { _, args ->
            val a = accessibility()
            val label = args.require("text")
            val e = a.findBest(label) ?: return@Skill "ERROR: nothing like \"$label\" on screen. Call read_screen to see what's there."
            MacroCenter.noteTap(e, a)
            tapReport(a.tapVerified(e), "\"${e.label}\"")
        },

        Skill(
            "tap_position", "🎯", "Tap a position", "Taps exact screen coordinates",
            "Tap at exact screen coordinates in pixels (use positions from read_screen or look_at_screen).",
            listOf(
                ToolParam("x", ParamType.INTEGER, "Pixels from the left edge"),
                ToolParam("y", ParamType.INTEGER, "Pixels from the top edge")
            ),
            needsAccessibility = true
        ) { _, args ->
            val x = args.int("x") ?: throw SkillException("missing 'x'")
            val y = args.int("y") ?: throw SkillException("missing 'y'")
            tapReport(accessibility().tapPointVerified(x.toFloat(), y.toFloat()), "the point $x,$y")
        },

        Skill(
            "long_press", "✋", "Long press", "Press and hold an item",
            "Press and hold an element (by number from read_screen, or by its text) — e.g. to select a message.",
            listOf(
                ToolParam("number", ParamType.INTEGER, "Element number from read_screen", required = false),
                ToolParam("text", ParamType.STRING, "Visible text of the item", required = false)
            ),
            needsAccessibility = true
        ) { _, args ->
            val a = accessibility()
            val e = args.int("number")?.let { a.elementByNumber(it) }
                ?: args.str("text")?.let { a.findBest(it) }
                ?: return@Skill "ERROR: element not found. Call read_screen first."
            MacroCenter.noteTap(e, a)
            if (a.longPressElement(e)) { delay(700); "OK: long-pressed ${e.label}" } else "ERROR: long press failed"
        },

        Skill(
            "type_text", "⌨️", "Type text", "Types into the text box",
            "Type text into the selected text box (or the first text box on screen). Set submit=true to press " +
                    "Enter/Search/Send afterwards.",
            listOf(
                ToolParam("text", ParamType.STRING, "Text to type"),
                ToolParam("submit", ParamType.BOOLEAN, "Press Enter/Search after typing", required = false)
            ),
            needsAccessibility = true
        ) { _, args ->
            val a = accessibility()
            val text = args.require("text")
            val typed = if (a.typeLikePerson()) a.typeTextHuman(text) else a.typeText(text)
            if (!typed) return@Skill "ERROR: no text box on screen"
            delay(300)
            if (args.bool("submit")) {
                if (!a.pressEnter()) return@Skill "OK: typed, but couldn't press Enter — tap the send/search button instead"
                delay(900)
                return@Skill "OK: typed and submitted"
            }
            "OK: typed"
        },

        Skill(
            "scroll", "↕️", "Scroll & swipe", "Scrolls YouTube, chats, pages",
            "Scroll the screen like a finger. down = see content further down / next video in Shorts or Reels; " +
                    "up = go back up / previous video; left/right = swipe sideways.",
            listOf(
                ToolParam("direction", ParamType.STRING, "Scroll direction", enum = listOf("down", "up", "left", "right")),
                ToolParam("times", ParamType.INTEGER, "How many times (default 1)", required = false)
            ),
            needsAccessibility = true
        ) { _, args ->
            val a = accessibility()
            val direction = args.require("direction")
            val times = (args.int("times") ?: 1).coerceIn(1, 10)
            repeat(times) {
                if (!a.scroll(direction)) return@Skill "ERROR: scroll failed"
                delay(450)
            }
            delay(300)
            "OK: scrolled $direction $times time(s)"
        },

        Skill(
            "phone_control", "🎮", "Phone buttons", "Home, back, recents, lock",
            "Press a system button.",
            listOf(
                ToolParam(
                    "action", ParamType.STRING, "Which button",
                    enum = listOf("home", "back", "recents", "notifications", "quick_settings", "lock_screen")
                )
            ),
            needsAccessibility = true
        ) { _, args -> phoneControl(args.require("action")) },

        Skill(
            "wait", "⏳", "Wait", "Waits for an app to load",
            "Wait a few seconds for something to load before reading the screen again.",
            listOf(ToolParam("seconds", ParamType.INTEGER, "1 to 10"))
        ) { _, args ->
            val s = (args.int("seconds") ?: 2).coerceIn(1, 10)
            delay(s * 1000L)
            "OK: waited $s s"
        },

        // ===== Phone features =====
        Skill(
            "set_alarm", "⏰", "Alarms", "\"Wake me up at 6:30\"",
            "Set an alarm at a time of day (24-hour clock).",
            listOf(
                ToolParam("hour", ParamType.INTEGER, "Hour 0-23"),
                ToolParam("minute", ParamType.INTEGER, "Minute 0-59"),
                ToolParam("label", ParamType.STRING, "Optional alarm label", required = false)
            )
        ) { ctx, args ->
            val hour = args.int("hour") ?: throw SkillException("missing 'hour'")
            setAlarm(ctx, hour, args.int("minute") ?: 0, args.str("label"))
        },

        Skill(
            "set_timer", "⏱️", "Timers", "\"Set a 10 minute timer\"",
            "Start a countdown timer.",
            listOf(
                ToolParam("seconds", ParamType.INTEGER, "Timer length in seconds"),
                ToolParam("label", ParamType.STRING, "Optional label", required = false)
            )
        ) { ctx, args ->
            val seconds = args.int("seconds") ?: throw SkillException("missing 'seconds'")
            setTimer(ctx, seconds, args.str("label"))
        },

        Skill(
            "flashlight", "🔦", "Flashlight", "\"Turn on the torch\"",
            "Turn the phone's flashlight on or off.",
            listOf(ToolParam("on", ParamType.BOOLEAN, "true = on, false = off"))
        ) { ctx, args -> flashlight(ctx, args.bool("on")) },

        Skill(
            "set_volume", "🔊", "Volume", "\"Volume 50 percent\"",
            "Set the media volume.",
            listOf(ToolParam("percent", ParamType.INTEGER, "Volume level 0-100"))
        ) { ctx, args -> setVolume(ctx, args.int("percent") ?: throw SkillException("missing 'percent'")) },

        Skill(
            "read_notifications", "🔔", "Read notifications", "\"What notifications do I have?\"",
            "Get the user's most recent notifications.",
            listOf(ToolParam("count", ParamType.INTEGER, "How many (default 5)", required = false))
        ) { ctx, args -> readNotifications(ctx, (args.int("count") ?: 5).coerceIn(1, 20)) },

        Skill(
            "battery_status", "🔋", "Battery status", "\"How much charge is left?\"",
            "Get the battery level and whether the phone is charging."
        ) { ctx, _ -> batteryStatus(ctx) },

        Skill(
            "web_search", "🔎", "Google search", "\"Search for cricket score\"",
            "Open Google search results in the browser for the user to look at. To find an answer yourself, use search_web instead.",
            listOf(ToolParam("query", ParamType.STRING, "Search query"))
        ) { ctx, args ->
            openUri(ctx, "https://www.google.com/search?q=" + Uri.encode(args.require("query")))
            delay(1500)
            "OK: opened Google search results"
        },

        Skill(
            "play_youtube", "▶️", "YouTube", "\"Play Arijit Singh songs\"",
            "Search YouTube for a video or song. Afterwards use read_screen + tap_element to play a result.",
            listOf(ToolParam("query", ParamType.STRING, "What to search on YouTube"))
        ) { ctx, args ->
            openUri(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(args.require("query")))
            delay(2500)
            "OK: YouTube search results are open. To play one, call read_screen and tap the video title."
        },

        Skill(
            "play_music", "🎵", "Play music", "\"Play my favourite song\"",
            "Play a song or music in the user's favourite music app (YouTube, YouTube Music or Spotify). " +
                    "Leave query empty to play the user's favourite song.",
            listOf(ToolParam("query", ParamType.STRING, "Song, artist or mood; empty = favourite song", required = false))
        ) { ctx, args -> playMusic(ctx, args.str("query")) },

        Skill(
            "youtube_channel_stats", "📊", "YouTube channel stats", "\"How many views did my channel get?\"",
            "Get the user's YouTube channel statistics (subscribers, total views, videos) and views of the latest videos.",
        ) { _, _ -> youtubeChannelStats() },

        Skill(
            "navigate_to", "🗺️", "Navigation", "\"Navigate to Dhanmondi\"",
            "Start Google Maps navigation to a place.",
            listOf(ToolParam("destination", ParamType.STRING, "Place or address"))
        ) { ctx, args -> navigateTo(ctx, args.require("destination")) }
    )

    // ====== Implementations ======

    private fun accessibility(): FaizaAccessibilityService =
        FaizaAccessibilityService.instance
            ?: throw SkillException("Accessibility Service is off. Ask the user to enable it in Faiza AI → Settings → Permissions.")

    private fun launch(ctx: Context, intent: Intent) {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openUri(ctx: Context, uri: String) = launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    private fun hasPermission(ctx: Context, permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private suspend fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        val wanted = normalize(name)
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }

        val match = apps.firstOrNull { normalize(it.second) == wanted }
            ?: apps.firstOrNull { normalize(it.second).startsWith(wanted) }
            ?: apps.firstOrNull { normalize(it.second).contains(wanted) }
            ?: apps.firstOrNull { normalize(it.second).length >= 3 && wanted.contains(normalize(it.second)) }
            ?: return "ERROR: no installed app called \"$name\""

        val intent = pm.getLaunchIntentForPackage(match.first)
            ?: return "ERROR: ${match.second} can't be opened"
        launch(ctx, intent)
        delay(1500)
        return "OK: opened ${match.second}"
    }

    /** (name, number) of the best contact match, or null if nothing matches well. */
    /** (name, number): favourite contacts first (video 24:26), then the phone book. */
    private suspend fun resolveContact(
        ctx: Context, contact: String, alternates: String?, forWhatsApp: Boolean = false
    ): Pair<String, String>? {
        if (ContactFinder.looksLikeNumber(contact)) return contact to contact
        // "আমার বউকে" → also try "বউ", so nicknames from My people match.
        val queries = ContactFinder.queries(contact, alternates).flatMap { listOf(it, FavoriteContacts.cleanSpokenName(it)) }.distinct()
        FavoriteContacts.bestMatch(FavoriteContacts.load(FaizaApp.instance.prefs), queries)?.let {
            return it.name to if (forWhatsApp) it.whatsappNumber else it.number
        }
        val best = ContactFinder.search(ctx, queries, 1).firstOrNull()
        return best?.takeIf { it.score >= 60 }?.let { it.name to it.number }
    }

    private suspend fun callContact(
        ctx: Context, contact: String, alternates: String?, simArg: Int?, rememberSim: Boolean
    ): String {
        val (name, number) = resolveContact(ctx, contact, alternates)
            ?: return "ERROR: no contact named \"$contact\". Ask the user for the number or another spelling."
        if (!hasPermission(ctx, Manifest.permission.CALL_PHONE)) {
            launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
            return "OK: opened the dialer for $name (Phone permission not granted, user must press call)"
        }
        val prefs = FaizaApp.instance.prefs
        val sims = SimHelper.sims(ctx)
        val slot = simArg?.takeIf { s -> sims.any { it.slot == s } }
            ?: prefs.preferredSim.takeIf { s -> s > 0 && sims.any { it.slot == s } }
        if (sims.size >= 2 && slot == null) {
            return "NEEDS_SIM_CHOICE: this phone has ${sims.size} SIMs (${SimHelper.describe(sims)}). " +
                    "Ask the user which SIM to call $name from, then call call_contact again with sim and remember_sim=true."
        }
        if (rememberSim && slot != null) prefs.preferredSim = slot
        launch(ctx, SimHelper.callIntent(ctx, number, slot))
        val simNote = slot?.let { s -> sims.firstOrNull { it.slot == s }?.let { " from SIM ${it.slot} (${it.label})" } }.orEmpty()
        return "OK: calling $name$simNote"
    }

    private fun activateSos(ctx: Context): String {
        val contacts = FavoriteContacts.sosContacts(FavoriteContacts.load(FaizaApp.instance.prefs))
        if (contacts.isEmpty()) {
            return "ERROR: no SOS contacts are saved. Tell the user to add them in Faiza AI → SOS. If they are in " +
                    "danger right now, they should call the emergency number (999 in Bangladesh, 112 in India) immediately."
        }
        SosManager.start(ctx)
        return "OK: SOS starts in ${SosManager.COUNTDOWN_SECONDS} seconds. It will text ${contacts.joinToString { it.name }} " +
                "the user's location, then call them one by one. Tell the user help is being contacted and they can say cancel to stop."
    }

    @Suppress("DEPRECATION")
    private suspend fun sendSms(ctx: Context, contact: String, alternates: String?, message: String, confirmed: Boolean): String {
        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend && !TaskPlan.running) return NOT_CONFIRMED
        if (!hasPermission(ctx, Manifest.permission.SEND_SMS)) throw SkillException("SMS permission not granted")
        val (name, number) = resolveContact(ctx, contact, alternates)
            ?: return "ERROR: no contact named \"$contact\""
        val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        sms.sendMultipartTextMessage(number, null, sms.divideMessage(message), null, null)
        return "OK: SMS sent to $name"
    }

    /** What a tap really did — never "OK" when the screen didn't change. */
    private fun tapReport(r: FaizaAccessibilityService.TapResult, what: String): String = when (r) {
        FaizaAccessibilityService.TapResult.CHANGED -> "OK: tapped $what — the screen changed"
        FaizaAccessibilityService.TapResult.NO_CHANGE ->
            "NOT CONFIRMED: tapped $what but NOTHING on the screen changed, so it probably did not work. Do NOT tell " +
                    "the user it worked. Call read_screen and tap a different element (e.g. the link text itself), or " +
                    "look_at_screen and tap_position."
        FaizaAccessibilityService.TapResult.FAILED -> "ERROR: the tap could not be performed"
    }

    private fun whatsappLaunchIntent(ctx: Context): Intent? =
        WHATSAPP_PACKAGES.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }

    private fun openWhatsAppUri(ctx: Context, uri: String): Boolean =
        WHATSAPP_PACKAGES.any { pkg ->
            try {
                launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }

    private suspend fun openWhatsAppChat(ctx: Context, contact: String, alternates: String?): String {
        val queries = whatsappSearchQueries(contact, alternates)
        val phoneMatch = runCatching { resolveContact(ctx, contact, alternates, forWhatsApp = true) }.getOrNull()
        if (phoneMatch != null) {
            val phone = toInternational(ctx, phoneMatch.second)
            if (!openWhatsAppUri(ctx, "https://api.whatsapp.com/send?phone=$phone")) return "ERROR: WhatsApp is not installed"
            delay(2500)
            return "OK: opened WhatsApp chat with ${phoneMatch.first}"
        }
        // Not a saved phone contact (or a group): search inside WhatsApp itself.
        return searchWhatsAppChat(ctx, queries)
    }

    /**
     * Names to type into WhatsApp's search when the person isn't in My people / contacts: the cleaned name first
     * ("আমার বউয়ের" → "বউ"), then what the user said, the alternates, and an English-letter spelling of a Bangla name.
     */
    internal fun whatsappSearchQueries(contact: String, alternates: String?): List<String> {
        val said = ContactFinder.queries(contact, alternates)
        val cleaned = said.map { FavoriteContacts.cleanSpokenName(it) }
        val latin = cleaned.filter { n -> n.any { it.code in 0x0980..0x09FF || it.code in 0x0900..0x097F } }
            .map { com.faiza.ai.ai.guardian.OwnerLock.sounds(it) }
            .filter { it.length >= 3 }
        return (cleaned + said + latin).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { FaizaAccessibilityService.normalize(it) }.take(5)
    }

    private suspend fun searchWhatsAppChat(ctx: Context, queries: List<String>): String {
        val a = accessibility()
        val intent = whatsappLaunchIntent(ctx) ?: return "ERROR: WhatsApp is not installed"
        launch(ctx, intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        delay(2200)

        val searchButton = a.snapshot().firstOrNull { e ->
            val l = e.label.lowercase(Locale.ROOT)
            !e.editable && SEARCH_LABELS.any { l == it || l.startsWith(it) }
        } ?: a.snapshot().firstOrNull { it.editable }
        ?: return "ERROR: couldn't find WhatsApp's search. Call read_screen to look at the screen."

        a.tapElement(searchButton)
        delay(900)
        for (q in queries) {
            if (!a.typeText(q)) return "ERROR: couldn't type in WhatsApp search"
            delay(1500)
            val box = a.snapshot().firstOrNull { it.editable }
            val hit = a.findBest(q) { e -> !e.editable && (box == null || e.bounds.top > box.bounds.bottom) }
            if (hit != null) {
                a.tapElement(hit)
                delay(1500)
                return "OK: opened WhatsApp chat \"${hit.label}\""
            }
        }
        return "ERROR: no WhatsApp chat matching \"${queries.first()}\". Ask the user for another spelling."
    }

    private suspend fun whatsappMessage(
        ctx: Context, contact: String, alternates: String?, message: String, confirmed: Boolean
    ): String {
        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend && !TaskPlan.running) return NOT_CONFIRMED
        val phoneMatch = runCatching { resolveContact(ctx, contact, alternates, forWhatsApp = true) }.getOrNull()
        val chatName: String
        if (phoneMatch != null) {
            val phone = toInternational(ctx, phoneMatch.second)
            val uri = "https://api.whatsapp.com/send?phone=$phone&text=" + Uri.encode(message)
            if (!openWhatsAppUri(ctx, uri)) return "ERROR: WhatsApp is not installed"
            chatName = phoneMatch.first
            delay(3000)
        } else {
            val opened = searchWhatsAppChat(ctx, whatsappSearchQueries(contact, alternates))
            if (!opened.startsWith("OK")) return opened
            chatName = contact
            if (!accessibility().typeText(message)) return "ERROR: opened the chat but couldn't type the message"
            delay(500)
        }

        val a = FaizaAccessibilityService.instance
            ?: return "OK: opened the chat with $chatName and typed the message. The user must tap Send " +
                    "(enable Accessibility so Faiza can send automatically)."
        return pressSendVerified(a, chatName)
    }

    /**
     * Presses Send and checks it really went: the message box must be empty afterwards. Tries twice. Never reports
     * "sent" when the text is still sitting in the box.
     */
    private suspend fun pressSendVerified(a: FaizaAccessibilityService, chatName: String): String {
        repeat(2) { attempt ->
            val typed = a.messageBoxText()
            if (typed != null && typed.isBlank() && attempt == 0) {
                return "ERROR: the message box in the chat with $chatName is empty — type the message first (type_text)."
            }
            val pressed = SEND_LABELS.any { a.clickByDescription(it) } ||
                    (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
            if (!pressed) return "ERROR: no Send button found in the chat with $chatName. Call read_screen and tap Send."
            var waited = 0
            while (waited < 1_800) {
                delay(300)
                waited += 300
                val now = a.messageBoxText()
                if (now == null || now.isBlank()) return "OK: message sent to $chatName (the message box is empty now)"
            }
        }
        return "NOT SENT: pressed Send twice but the message is still in the box in the chat with $chatName. " +
                "Do NOT say it was sent. Call read_screen, find the Send button and tap it."
    }

    /** WhatsApp id ("8801XXXXXXXXX@s.whatsapp.net") and name of a contact, for sharing files to their chat. */
    internal suspend fun whatsappJid(ctx: Context, contact: String, alternates: String?): Pair<String, String>? {
        val (name, number) = resolveContact(ctx, contact, alternates, forWhatsApp = true) ?: return null
        return name to toInternational(ctx, number).filter { it.isDigit() } + "@s.whatsapp.net"
    }

    internal val sendLabels: List<String> get() = SEND_LABELS

    /** "01711…" → "8801711…" (for wa.me links from My people). */
    internal fun internationalDigits(ctx: Context, number: String): String = toInternational(ctx, number).filter { it.isDigit() }

    private fun toInternational(ctx: Context, number: String): String {
        val chosen = FaizaApp.instance.prefs.countryDialCode.ifBlank { null }
        val dial = chosen ?: PhoneNumbers.dialCodeForIso(
            ctx.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.lowercase(Locale.ROOT)
        )
        return PhoneNumbers.toInternational(number, dial)
    }

    private fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String?): String {
        if (hour !in 0..23 || minute !in 0..59) return "ERROR: invalid time"
        launch(ctx, Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        })
        return "OK: alarm set for %02d:%02d".format(hour, minute)
    }

    private fun setTimer(ctx: Context, seconds: Int, label: String?): String {
        if (seconds !in 1..86_400) return "ERROR: timer must be between 1 second and 24 hours"
        launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        })
        return "OK: timer started for $seconds seconds"
    }

    private fun flashlight(ctx: Context, on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return "ERROR: no camera"
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "ERROR: this phone has no flashlight"
        cm.setTorchMode(id, on)
        return if (on) "OK: flashlight on" else "OK: flashlight off"
    }

    private fun setVolume(ctx: Context, percent: Int): String {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return "ERROR: no audio service"
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent.coerceIn(0, 100) / 100, AudioManager.FLAG_SHOW_UI)
        return "OK: volume set to ${percent.coerceIn(0, 100)}%"
    }

    private fun readNotifications(ctx: Context, count: Int): String {
        FaizaNotificationListener.instance
            ?: throw SkillException("Notification Access is off. Ask the user to enable it in Faiza AI settings.")
        val items = synchronized(FaizaNotificationListener.recentNotifications) {
            FaizaNotificationListener.recentNotifications
                .filter { it.packageName != ctx.packageName && (it.title.isNotBlank() || it.text.isNotBlank()) }
                .take(count)
                .toList()
        }
        if (items.isEmpty()) return "No recent notifications."
        val pm = ctx.packageManager
        return items.joinToString("\n") { n ->
            val app = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString()
            }.getOrDefault(n.packageName.substringAfterLast('.'))
            "[$app] ${n.title}: ${n.text}"
        }
    }

    private fun batteryStatus(ctx: Context): String {
        val bm = ctx.getSystemService(BatteryManager::class.java) ?: return "ERROR: unavailable"
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery $level%, " + if (bm.isCharging) "charging" else "not charging"
    }

    private fun navigateTo(ctx: Context, destination: String): String {
        val encoded = Uri.encode(destination)
        try {
            launch(
                ctx,
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                    .setPackage("com.google.android.apps.maps")
            )
        } catch (_: ActivityNotFoundException) {
            openUri(ctx, "geo:0,0?q=$encoded")
        }
        return "OK: navigation to $destination started"
    }

    private suspend fun playMusic(ctx: Context, query: String?): String {
        val prefs = FaizaApp.instance.prefs
        val what = query?.takeIf { it.isNotBlank() } ?: prefs.favoriteSong.takeIf { it.isNotBlank() }
            ?: return "ERROR: no favourite song is set. Ask the user what to play (they can set one in Settings → About you)."
        val encoded = Uri.encode(what)
        val opened = when (prefs.favoriteMusicApp) {
            "spotify" -> tryLaunch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).setPackage("com.spotify.music"))
            "youtube_music" -> tryLaunch(
                ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encoded"))
                    .setPackage("com.google.android.apps.youtube.music")
            )
            else -> false
        }
        if (!opened) openUri(ctx, "https://www.youtube.com/results?search_query=$encoded")
        delay(2500)
        return "OK: search results for \"$what\" are open. To start playing, call read_screen and tap the first song."
    }

    private fun tryLaunch(ctx: Context, intent: Intent): Boolean = try {
        launch(ctx, intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private suspend fun youtubeChannelStats(): String = withContext(Dispatchers.IO) {
        val prefs = FaizaApp.instance.prefs
        val handle = prefs.youtubeHandle.trim()
        val key = prefs.youtubeApiKey.trim()
        if (handle.isEmpty()) return@withContext "ERROR: no YouTube channel set. The user can add it in Settings → You."
        if (key.isEmpty()) return@withContext "ERROR: a YouTube API key is needed. The user can add it in Settings → You."
        val api = "https://www.googleapis.com/youtube/v3"
        val at = if (handle.startsWith("@")) handle else "@$handle"

        val channel = youtubeGet("$api/channels?part=snippet,statistics,contentDetails&forHandle=${Uri.encode(at)}&key=$key")
            .getAsJsonArray("items")?.firstOrNull()?.asJsonObject
            ?: return@withContext "ERROR: no YouTube channel found for $at"
        val title = channel.getAsJsonObject("snippet")?.get("title")?.asString ?: at
        val stats = channel.getAsJsonObject("statistics")
        val subs = if (stats?.get("hiddenSubscriberCount")?.asBoolean == true) "hidden"
        else stats?.get("subscriberCount")?.asString ?: "?"
        val summary = StringBuilder(
            "$title: $subs subscribers, ${stats?.get("viewCount")?.asString ?: "?"} total views, " +
                    "${stats?.get("videoCount")?.asString ?: "?"} videos."
        )

        val uploads = channel.getAsJsonObject("contentDetails")?.getAsJsonObject("relatedPlaylists")
            ?.get("uploads")?.asString
        if (uploads != null) {
            val ids = youtubeGet("$api/playlistItems?part=contentDetails&maxResults=5&playlistId=$uploads&key=$key")
                .getAsJsonArray("items")
                ?.mapNotNull { it.asJsonObject.getAsJsonObject("contentDetails")?.get("videoId")?.asString }
                .orEmpty()
            if (ids.isNotEmpty()) {
                summary.append("\nLatest videos:")
                youtubeGet("$api/videos?part=snippet,statistics&id=${ids.joinToString(",")}&key=$key")
                    .getAsJsonArray("items")?.forEach { v ->
                        val o = v.asJsonObject
                        val name = o.getAsJsonObject("snippet")?.get("title")?.asString ?: "video"
                        val views = o.getAsJsonObject("statistics")?.get("viewCount")?.asString ?: "?"
                        val likes = o.getAsJsonObject("statistics")?.get("likeCount")?.asString ?: "?"
                        summary.append("\n- $name: $views views, $likes likes")
                    }
            }
        }
        summary.toString()
    }

    private fun youtubeGet(url: String): JsonObject {
        AIProviders.http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            if (!r.isSuccessful) {
                val msg = json?.getAsJsonObject("error")?.get("message")?.asString ?: "HTTP ${r.code}"
                throw SkillException("YouTube: $msg")
            }
            return json ?: throw SkillException("YouTube sent an unreadable reply")
        }
    }

    private fun phoneControl(action: String): String {
        val a = accessibility()
        val ok = when (action) {
            "home" -> a.pressHome()
            "back" -> a.pressBack()
            "recents" -> a.openRecents()
            "notifications" -> a.openNotifications()
            "quick_settings" -> a.openQuickSettings()
            "lock_screen" -> a.lockScreen()
            else -> return "ERROR: unknown action $action"
        }
        return if (ok) "OK: $action" else "ERROR: $action didn't work on this phone"
    }
}
