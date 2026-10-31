package com.faiza.ai.ai.modes

import android.Manifest
import android.app.Notification
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.sos.LocationHelper
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.ModesActivity
import com.faiza.ai.util.ActivityLog
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** How Life modes behave (Settings in the Modes screen). */
data class ModeSettings(
    val autoDriving: Boolean = true,
    val autoGaming: Boolean = false,
    val autoPrayer: Boolean = false,
    val sleepEnabled: Boolean = false,
    val sleepStart: Int = 23 * 60,
    val sleepEnd: Int = 7 * 60,
    val textCallers: Boolean = true,
    /** "auto" (app language), "bn", "en" or "both". */
    val lang: String = "auto"
)

/** Life modes on the phone: calls, car Bluetooth, games, prayer times, summaries. */
object ModeCenter {

    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.faiza.ai.util.CrashReporter.guard)
    private val prefs get() = FaizaApp.instance.prefs

    // ===== Storage =====

    fun settings(): ModeSettings = runCatching { gson.fromJson(prefs.modeSettingsJson, ModeSettings::class.java) }
        .getOrNull() ?: ModeSettings()

    fun saveSettings(s: ModeSettings) {
        prefs.modeSettingsJson = gson.toJson(s)
    }

    @Suppress("SENSELESS_COMPARISON")
    fun customModes(): List<LifeMode> = runCatching {
        gson.fromJson<List<LifeMode>>(prefs.customModesJson ?: return emptyList(), object : TypeToken<List<LifeMode>>() {}.type)
    }.getOrNull().orEmpty().filter { it != null && it.id != null && it.name != null && it.messageBn != null }
        .map { it.copy(emoji = it.emoji ?: "⭐", subtitle = it.subtitle ?: "Your own mode", messageEn = it.messageEn ?: it.messageBn) }

    fun saveCustomModes(list: List<LifeMode>) {
        prefs.customModesJson = gson.toJson(list)
    }

    fun allModes(): List<LifeMode> = LifeModes.BUILT_IN + customModes()

    fun mode(id: String?): LifeMode? = allModes().firstOrNull { it.id == id }

    /** The user's own wording for a mode's message, if they changed it. */
    fun messageOverride(id: String): String? = overrides()[id]

    fun setMessageOverride(id: String, text: String?) {
        val m = overrides().toMutableMap()
        if (text.isNullOrBlank()) m.remove(id) else m[id] = text
        prefs.modeOverridesJson = gson.toJson(m)
    }

    private fun overrides(): Map<String, String> = runCatching {
        gson.fromJson<Map<String, String>>(prefs.modeOverridesJson ?: return emptyMap(), object : TypeToken<Map<String, String>>() {}.type)
    }.getOrNull().orEmpty()

    fun calls(): List<ModeCall> = runCatching {
        gson.fromJson<List<ModeCall>>(prefs.modeCallsJson ?: return emptyList(), object : TypeToken<List<ModeCall>>() {}.type)
    }.getOrNull().orEmpty()

    private fun addCall(c: ModeCall) {
        prefs.modeCallsJson = gson.toJson((listOf(c) + calls()).take(60))
    }

    // ===== Which mode is on =====

    private fun minuteOfDay(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    private fun gameInFront(ctx: Context): Boolean {
        val pkg = FaizaAccessibilityService.instance?.foregroundPackage?.takeIf { it.isNotEmpty() } ?: return false
        return runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).category == ApplicationInfo.CATEGORY_GAME }
            .getOrDefault(false)
    }

    fun currentId(ctx: Context): String? {
        val s = settings()
        val until = prefs.modeManualUntil
        var manual = prefs.modeManualId
        if (manual != null && until != 0L && System.currentTimeMillis() >= until) {
            prefs.modeManualId = null
            manual = null
        }
        return LifeModes.effective(
            manualId = manual, manualUntil = until, now = System.currentTimeMillis(),
            carConnected = prefs.isCarConnected, autoDriving = s.autoDriving,
            gameInFront = s.autoGaming && gameInFront(ctx), autoGaming = s.autoGaming,
            inPrayerTime = s.autoPrayer && LifeModes.inPrayerWindow(minuteOfDay(), prayerTimesToday()), autoPrayer = s.autoPrayer,
            sleepEnabled = s.sleepEnabled, minuteOfDay = minuteOfDay(), sleepStart = s.sleepStart, sleepEnd = s.sleepEnd
        )
    }

    fun current(ctx: Context): LifeMode? = mode(currentId(ctx))

    /** Switch a mode on by hand (for [minutes], or until switched off). Returns a short status. */
    fun setManual(ctx: Context, id: String?, minutes: Int? = null): String {
        if (id == null) {
            val was = current(ctx)
            prefs.modeManualId = null
            prefs.modeManualUntil = 0L
            val text = finish(ctx, was) ?: "No mode was on."
            refreshUi()
            return text
        }
        val m = mode(id) ?: return "No mode called $id."
        prefs.modeManualId = m.id
        prefs.modeManualUntil = if (minutes != null && minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        checkTransition(ctx)
        val until = if (minutes != null && minutes > 0) " for $minutes minutes" else ""
        return "${m.emoji} ${m.name} mode is on$until. Calls get a polite text; VIPs and repeat callers still ring."
    }

    /** Notices a mode starting or ending (also the automatic ones) and gives the summary at the end. */
    fun checkTransition(ctx: Context) {
        val now = currentId(ctx)
        val last = prefs.modeLastActive
        if (now == last) return
        prefs.modeLastActive = now
        if (now != null) {
            prefs.modeStartedAt = System.currentTimeMillis()
            val m = mode(now)
            if (m != null && last == null) ActivityLog.add("mode", "${m.emoji} ${m.name} mode on", m.subtitle)
            refreshUi()
        } else {
            val summary = finish(ctx, mode(last))
            if (summary != null) FloatingOrbService.instance?.onLifeModeEnded(summary) else refreshUi()
        }
    }

    /** Mode ended: what happened while it was on. */
    private fun finish(ctx: Context, ended: LifeMode?): String? {
        ended ?: return null
        val since = prefs.modeStartedAt
        val during = calls().filter { it.at >= since }
        prefs.modeLastActive = null
        val text = if (during.isEmpty()) "${ended.name} mode is off. No one called."
        else "${ended.name} mode is off. While it was on: ${LifeModes.summary(during)}."
        ActivityLog.add("mode", "${ended.emoji} ${ended.name} mode off", if (during.isEmpty()) "No calls" else LifeModes.summary(during))
        if (during.isNotEmpty()) notify(ctx, "While you were in ${ended.name} mode", LifeModes.summary(during), 7700)
        return text + if (during.isNotEmpty()) " Offer to call them back." else ""
    }

    private fun refreshUi() {
        FloatingOrbService.instance?.onLifeModeChanged()
    }

    // ===== Calls =====

    private var pendingRing: Runnable? = null
    private var ringNumber: String? = null
    private var lastRingHandledAt = 0L

    /** PHONE_STATE (from the manifest receiver and the running service; duplicates are ignored). */
    fun onPhoneState(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.let { ringNumber = it }
                if (pendingRing != null || System.currentTimeMillis() - lastRingHandledAt < 4_000) return
                // Android sends the number in a second broadcast a moment later: wait briefly for it.
                pendingRing = Runnable {
                    pendingRing = null
                    lastRingHandledAt = System.currentTimeMillis()
                    runCatching { handleRinging(app, ringNumber) }
                }.also { main.postDelayed(it, 700) }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                pendingRing?.let { main.removeCallbacks(it) }
                pendingRing = null
                ringNumber = null
            }
        }
    }

    private fun handleRinging(ctx: Context, number: String?) {
        val mode = current(ctx) ?: return
        val favorites = FavoriteContacts.load(prefs)
        val fav = number?.let { n -> favorites.firstOrNull { PhoneNumbers.sameNumber(it.number, n) || PhoneNumbers.sameNumber(it.whatsappNumber, n) } }
        val name = fav?.name ?: number?.let { lookupName(ctx, it) } ?: ""
        val lastDeclined = number?.let { n -> calls().firstOrNull { it.declined && PhoneNumbers.sameNumber(it.number, n) }?.at }

        when (LifeModes.decideCall(fav?.vip == true, lastDeclined, System.currentTimeMillis())) {
            LifeModes.CallAction.LET_RING_VIP -> {
                addCall(ModeCall(System.currentTimeMillis(), number.orEmpty(), name, mode.id, declined = false, texted = false))
                ActivityLog.add("mode", "${name.ifBlank { "VIP" }} called", "VIP — let it ring (${mode.name} mode)")
            }
            LifeModes.CallAction.LET_RING_REPEAT -> {
                addCall(ModeCall(System.currentTimeMillis(), number.orEmpty(), name, mode.id, declined = false, texted = false))
                ActivityLog.add("mode", "${name.ifBlank { number ?: "Someone" }} called again", "Second call within 3 minutes — let it ring")
            }
            LifeModes.CallAction.DECLINE -> {
                val declined = decline(ctx)
                val texted = declined && number != null && settings().textCallers && sendText(ctx, number, mode)
                addCall(ModeCall(System.currentTimeMillis(), number.orEmpty(), name, mode.id, declined, texted))
                ActivityLog.add(
                    "mode", "${name.ifBlank { number ?: "Unknown caller" }} called",
                    when {
                        !declined -> "Couldn't decline — allow \"Answer & manage calls\" (${mode.name} mode)"
                        texted -> "Declined and texted (${mode.name} mode)"
                        else -> "Declined (${mode.name} mode)"
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission")
    private fun decline(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching { ctx.getSystemService(TelecomManager::class.java)?.endCall() == true }.getOrDefault(false)
    }

    private fun language(): String = settings().lang.let { l ->
        if (l != "auto") l else if (com.faiza.ai.ai.AppLanguage.from(prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI) "bn" else "en"
    }

    fun messageFor(mode: LifeMode): String = LifeModes.message(
        mode, language(), prefs.userName.orEmpty(),
        prefs.assistantName(Persona.from(prefs.selectedPersona)), messageOverride(mode.id)
    )

    @Suppress("DEPRECATION")
    private fun sendText(ctx: Context, number: String, mode: LifeMode): Boolean {
        if (number.count { it.isDigit() } < 7) return false // short codes / hidden numbers
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            val text = messageFor(mode)
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            true
        }.getOrDefault(false)
    }

    private fun lookupName(ctx: Context, number: String): String? = runCatching {
        ctx.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** WhatsApp / Messenger calls arrive as call notifications: press their Decline button. */
    fun onNotification(ctx: Context, sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.category != Notification.CATEGORY_CALL) return
        if (sbn.packageName !in setOf("com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.imo.android.imoim")) return
        val mode = current(ctx) ?: return
        val caller = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val favorites = FavoriteContacts.load(prefs)
        if (favorites.any { f -> f.vip && f.allNames.any { it.equals(caller, ignoreCase = true) } }) return
        val decline = n.actions?.firstOrNull { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            listOf("decline", "reject", "dismiss", "প্রত্যাখ্যান", "অস্বীকার", "अस्वीकार").any { t.contains(it) }
        } ?: return
        if (runCatching { decline.actionIntent.send(); true }.getOrDefault(false)) {
            addCall(ModeCall(System.currentTimeMillis(), "", caller, mode.id, declined = true, texted = false))
            ActivityLog.add("mode", "$caller called on ${if (sbn.packageName.contains("whatsapp")) "WhatsApp" else "an app"}", "Declined (${mode.name} mode)")
        }
    }

    // ===== Car Bluetooth (Driving) =====

    fun onBluetooth(ctx: Context, intent: Intent) {
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val cls = runCatching { device?.bluetoothClass?.deviceClass }.getOrNull() ?: return
        val isCar = cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        if (!isCar) return
        prefs.isCarConnected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
        checkTransition(ctx.applicationContext)
    }

    // ===== Prayer times (Aladhan, cached per day) =====

    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    fun prayerTimesToday(): List<Int> {
        val cached = prefs.prayerTimesJson
        val online = if (cached == null) emptyList() else runCatching {
            val o = JsonParser.parseString(cached).asJsonObject
            if (o.get("day").asString != dayFmt.format(Date())) emptyList()
            else o.getAsJsonArray("times").map { it.asInt }
        }.getOrDefault(emptyList())
        if (online.isNotEmpty()) return online
        // No internet copy for today: work them out on the phone.
        return runCatching {
            com.faiza.ai.ai.prayer.PrayerCenter.timesFor(FaizaApp.instance).prayers().map { it.second }.filter { it >= 0 }
        }.getOrDefault(emptyList())
    }

    /** Fetches today's prayer times for where the phone is (Karachi method, used in Bangladesh). */
    fun refreshPrayerTimes(ctx: Context) {
        if (!settings().autoPrayer || prayerTimesToday().isNotEmpty()) return
        val loc = LocationHelper.lastKnown(ctx) ?: return
        scope.launch {
            val times = withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://api.aladhan.com/v1/timings?latitude=${loc.latitude}&longitude=${loc.longitude}&method=1&school=1"
                    AIProviders.http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                        val t = JsonParser.parseString(r.body?.string().orEmpty()).asJsonObject
                            .getAsJsonObject("data").getAsJsonObject("timings")
                        listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha").mapNotNull { k -> t.get(k)?.asString?.let(LifeModes::parseClock) }
                    }
                }.getOrNull()
            }
            if (!times.isNullOrEmpty()) {
                prefs.prayerTimesJson = com.google.gson.JsonObject().apply {
                    addProperty("day", dayFmt.format(Date()))
                    add("times", gson.toJsonTree(times))
                }.toString()
            }
        }
    }

    // ===== Notifications =====

    private fun notify(ctx: Context, title: String, text: String, id: Int) {
        val open = android.app.PendingIntent.getActivity(
            ctx, 9, Intent(ctx, ModesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    // ===== Voice =====

    fun skills(): List<Skill> = listOf(
        Skill(
            "life_mode", "🚗", "Life modes", "\"I'm driving\", \"ঘুমাতে যাচ্ছি\"",
            "Turn a Life mode on or off. While on, calls are declined with a polite text (VIPs and repeat callers still " +
                    "ring) and you stay quiet. Modes: " + allModes().joinToString { "${it.id} (${it.name})" } + ". " +
                    "Use \"off\" when the user is done (you'll get a summary of who called — tell them and offer to call back).",
            listOf(
                ToolParam("mode", ParamType.STRING, "Mode id from the list, or \"off\""),
                ToolParam("minutes", ParamType.INTEGER, "Only for this many minutes (empty = until switched off)", required = false)
            )
        ) { ctx, args ->
            val id = args.require("mode").lowercase().trim()
            if (id == "off" || id == "none") "OK: " + setManual(ctx, null)
            else {
                val m = mode(id) ?: allModes().firstOrNull { it.name.equals(id, ignoreCase = true) }
                ?: return@Skill "ERROR: no mode \"$id\". Modes: ${allModes().joinToString { it.id }}"
                "OK: " + setManual(ctx, m.id, args.int("minutes")?.takeIf { it in 1..(24 * 60) }) +
                        " Say so in one short sentence, then stay quiet."
            }
        }
    )
}

/** Manifest receiver: calls ringing, car Bluetooth connecting (both work even when Faiza isn't open). */
class LifeModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            when (intent.action) {
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> ModeCenter.onPhoneState(context, intent)
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> ModeCenter.onBluetooth(context, intent)
            }
        }
    }
}
