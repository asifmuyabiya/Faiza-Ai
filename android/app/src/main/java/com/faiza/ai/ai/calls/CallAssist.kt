package com.faiza.ai.ai.calls

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.SmsManager
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.service.FloatingOrbService

/**
 * Call helper (owner: "কে ফোন দিছে বলে দেবে — বস, আম্মু ফোন দিছে — আর বললেই ধরবে, কেটে দেবে, বা 'পরে কল দিচ্ছি'
 * মেসেজ পাঠাবে"). Works for normal calls and WhatsApp / Messenger / imo / Telegram / Viber calls.
 */
object CallAssist {

    data class Ringing(
        val number: String?,
        val name: String?,
        val app: String?, // null = normal phone call
        val sbn: StatusBarNotification? = null,
        val at: Long = System.currentTimeMillis()
    )

    @Volatile private var current: Ringing? = null

    /** The call ringing now; app calls have no "ended" signal, so it expires after a minute. */
    val ringing: Ringing? get() = current?.takeIf { System.currentTimeMillis() - it.at < 60_000 }

    private val prefs get() = FaizaApp.instance.prefs

    val CALL_APPS = mapOf(
        "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "com.facebook.orca" to "Messenger",
        "com.facebook.mlite" to "Messenger Lite", "com.imo.android.imoim" to "imo", "org.telegram.messenger" to "Telegram",
        "com.viber.voip" to "Viber", "com.google.android.apps.tachyon" to "Google Meet"
    )

    fun phoneRinging(number: String?, name: String?) {
        val old = ringing
        current = Ringing(number ?: old?.number, name ?: old?.name, old?.app, old?.sbn)
    }

    fun appRinging(sbn: StatusBarNotification, caller: String?) {
        current = Ringing(null, caller, CALL_APPS[sbn.packageName] ?: "app", sbn)
    }

    fun ended() { current = null }

    /** My People nickname first ("আম্মু"), then the contact / app name. Null = unknown. */
    fun friendlyName(number: String?, shownName: String?): String? {
        val people = runCatching { FavoriteContacts.load(prefs) }.getOrDefault(emptyList())
        val byNumber = number?.let { n -> people.firstOrNull { PhoneNumbers.sameNumber(it.number, n) || PhoneNumbers.sameNumber(it.whatsappNumber, n) } }
        val byName = shownName?.let { s -> people.firstOrNull { p -> p.allNames.any { it.equals(s.trim(), true) } } }
        val p = byNumber ?: byName
        if (p != null) return p.nicknames.firstOrNull { it.isNotBlank() } ?: p.name
        return shownName?.trim()?.takeIf { it.isNotEmpty() && !looksLikeNumber(it) }
    }

    fun looksLikeNumber(s: String) = s.count { it.isDigit() } >= 5 && s.all { it.isDigit() || it in "+-() " }

    /** "বস, আম্মু ফোন দিছে" — the line Faiza says. */
    fun announceLine(bangla: Boolean, address: String, name: String?, app: String?, number: String?): String {
        val who = name ?: number?.takeIf { looksLikeNumber(it) }?.let { spaced(it) }
        return if (bangla) when {
            who == null && app == null -> "$address, একটা অচেনা নাম্বার থেকে কল আসছে"
            who == null -> "$address, $app-এ একটা কল আসছে"
            app == null && name == null -> "$address, $who নাম্বার থেকে কল আসছে"
            app == null -> "$address, $who ফোন দিছে"
            else -> "$address, $who $app-এ কল দিছে"
        } else when {
            who == null && app == null -> "$address, a call from an unknown number"
            who == null -> "$address, a $app call is coming in"
            app == null -> "$address, $who is calling"
            else -> "$address, $who is calling on $app"
        }
    }

    /** "01712345678" → "0171 234 5678" so it's read in groups. */
    fun spaced(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        return d.chunked(4).joinToString(" ")
    }

    fun defaultBusyText(bangla: Boolean): String = prefs.busyReplyText.trim().ifEmpty {
        if (bangla) "এখন একটু ব্যস্ত আছি, একটু পরে কল দিচ্ছি।" else "I'm busy right now, I'll call you back soon."
    }

    // ---------------- Actions ----------------

    private fun pressAction(sbn: StatusBarNotification, words: List<String>): Boolean {
        val a = sbn.notification.actions?.firstOrNull { act ->
            val t = act.title?.toString()?.lowercase().orEmpty()
            words.any { t.contains(it) }
        } ?: return false
        return runCatching { a.actionIntent.send(); true }.getOrDefault(false)
    }

    private val ANSWER_WORDS = listOf("answer", "accept", "ধরুন", "উত্তর", "গ্রহণ", "स्वीकार", "जवाब")
    private val DECLINE_WORDS = listOf("decline", "reject", "dismiss", "hang up", "end", "প্রত্যাখ্যান", "অস্বীকার", "কেটে", "अस्वीकार")

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun answer(ctx: Context): String {
        val r = ringing ?: return "NO CALL is ringing right now."
        if (r.sbn != null) return if (pressAction(r.sbn, ANSWER_WORDS)) "Answered the ${r.app} call." else "Couldn't press Answer on the ${r.app} call."
        if (Build.VERSION.SDK_INT < 26) return "This phone can't answer by voice."
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
            return "ERROR: Faiza needs the \"Answer & manage calls\" permission (Settings → Phone access)."
        return runCatching { ctx.getSystemService(TelecomManager::class.java)?.acceptRingingCall(); "Answered." }
            .getOrElse { "Couldn't answer: ${it.message}" }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun decline(ctx: Context): Boolean {
        val r = ringing ?: return false
        if (r.sbn != null) return pressAction(r.sbn, DECLINE_WORDS)
        if (Build.VERSION.SDK_INT < 28) return false
        if (ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching { ctx.getSystemService(TelecomManager::class.java)?.endCall() == true }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    fun sms(ctx: Context, number: String, text: String): Boolean {
        if (number.count { it.isDigit() } < 7) return false
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            true
        }.getOrDefault(false)
    }

    fun skill() = Skill(
        "incoming_call", "📞", "Incoming call", "\"ধরো\" · \"কেটে দাও\" · \"পরে কল দিচ্ছি বলে দাও\"",
        "Handle the call that is ringing NOW (phone, WhatsApp, Messenger, imo…). action=answer | decline | " +
                "reply (decline + send a text; for a normal call by SMS — write the text in the user's words or leave it " +
                "empty for \"busy, I'll call back\") | status. Only when the user tells you what to do.",
        listOf(
            ToolParam("action", ParamType.STRING, "answer | decline | reply | status"),
            ToolParam("text", ParamType.STRING, "the reply text (for reply)", required = false)
        )
    ) { ctx, args ->
        val r = ringing
        val bangla = com.faiza.ai.ai.AppLanguage.from(prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI
        when (args.require("action").lowercase()) {
            "status" -> r?.let { "RINGING: ${it.name ?: it.number ?: "unknown"}" + (it.app?.let { a -> " on $a" } ?: "") } ?: "No call is ringing."
            "answer" -> answer(ctx).also { if (!it.startsWith("NO") && !it.startsWith("ERROR")) FloatingOrbService.instance?.onCallHandled(answered = true) }
            "decline" -> {
                if (r == null) return@Skill "NO CALL is ringing right now."
                if (decline(ctx)) { FloatingOrbService.instance?.onCallHandled(false); "Declined. Say one short line." }
                else "Couldn't decline — Faiza needs the \"Answer & manage calls\" permission."
            }
            "reply" -> {
                if (r == null) return@Skill "NO CALL is ringing right now."
                val text = args.str("text") ?: defaultBusyText(bangla)
                val declined = decline(ctx)
                val sent = r.app == null && r.number != null && sms(ctx, r.number, text)
                FloatingOrbService.instance?.onCallHandled(false)
                when {
                    sent -> "Declined and texted: \"$text\". Tell the user in one short line."
                    r.app != null -> (if (declined) "Declined the ${r.app} call. " else "Couldn't decline. ") +
                            "Can't text from here for ${r.app} calls — offer to send the message on ${r.app} with your messaging tools."
                    !declined -> "Couldn't decline (needs \"Answer & manage calls\" permission)."
                    else -> "Declined, but the SMS couldn't be sent (needs SMS permission or the number is hidden)."
                }
            }
            else -> "ERROR: unknown action"
        }
    }
}
