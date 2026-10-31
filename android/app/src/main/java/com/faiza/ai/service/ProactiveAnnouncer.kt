package com.faiza.ai.service

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.Phrases

/**
 * Speaks up without being asked: announces incoming callers and new chat messages.
 * Caller names come from the dialer's notification (needs Notification Access) or,
 * as a fallback, the phone-state broadcast (needs Phone permission).
 */
class ProactiveAnnouncer(
    private val context: Context,
    private val isBusy: () -> Boolean,
    private val speak: (String) -> Unit,
    /** An incoming call: the line to say ("বস, আম্মু ফোন দিছে"); the service also lets her take orders. */
    private val onCall: (String) -> Unit = speak,
    /** The phone call was picked up (true) or ended (false). */
    private val onCallEnded: (Boolean) -> Unit = {},
    /** Driving: a message to read out in full (line, app, sender). */
    private val onDrivingMessage: ((String, String, String) -> Unit)? = null
) {

    companion object {
        private const val CALL_ANNOUNCE_DELAY_MS = 1500L
        private const val CALL_DEDUPE_MS = 20_000L
        private const val MESSAGE_DEDUPE_MS = 60_000L

        private val MESSAGING_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.facebook.orca" to "Messenger",
            "com.facebook.mlite" to "Messenger Lite",
            "org.telegram.messenger" to "Telegram",
            "com.imo.android.imoim" to "imo",
            "com.viber.voip" to "Viber",
            "com.google.android.apps.messaging" to "SMS",
            "com.samsung.android.messaging" to "SMS"
        )
    }

    private val prefs get() = FaizaApp.instance.prefs
    private val language get() = AppLanguage.from(prefs.language)
    private val handler = Handler(Looper.getMainLooper())

    private var receiverRegistered = false
    private var isRinging = false
    private var pendingNumber: String? = null
    private var lastCallAnnouncedAt = 0L
    private val lastMessageAnnouncedAt = HashMap<String, Long>()

    private val announceCallFallback = Runnable {
        if (isRinging && !callRecentlyAnnounced()) {
            announceCall(pendingNumber?.let { lookupContactName(it) ?: it })
        }
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    @Suppress("DEPRECATION")
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (number != null) pendingNumber = number
                    com.faiza.ai.ai.calls.CallAssist.phoneRinging(number, null)
                    if (!isRinging) {
                        isRinging = true
                        // Give the dialer notification (which carries the caller name) a head start.
                        handler.postDelayed(announceCallFallback, CALL_ANNOUNCE_DELAY_MS)
                    }
                }
                else -> {
                    val offhook = intent.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_OFFHOOK
                    val wasRinging = isRinging
                    isRinging = false
                    pendingNumber = null
                    lastCallAnnouncedAt = 0L
                    handler.removeCallbacks(announceCallFallback)
                    com.faiza.ai.ai.calls.CallAssist.ended()
                    if (wasRinging || offhook) onCallEnded(offhook)
                }
            }
        }
    }

    fun refresh() {
        val wantCalls = prefs.isCallAnnouncementEnabled
        if (wantCalls && !receiverRegistered) {
            ContextCompat.registerReceiver(
                context, callReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } else if (!wantCalls && receiverRegistered) {
            unregisterReceiver()
        }

        // Always listen (cheap): calls, proactive messages, and messages while driving.
        FaizaNotificationListener.onPosted = ::onNotification
    }

    fun stop() {
        unregisterReceiver()
        FaizaNotificationListener.onPosted = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(callReceiver)
        } catch (_: IllegalArgumentException) {}
        receiverRegistered = false
    }

    private fun onNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        // Driving: messages count even when "read my messages" is off.
        handler.post {
            if (sbn.notification.category == Notification.CATEGORY_CALL) {
                handleCallNotification(sbn)
            } else if (prefs.isProactiveMode || com.faiza.ai.ai.driving.Driving.active(context)) {
                handleMessageNotification(sbn)
            }
        }
    }

    private fun handleCallNotification(sbn: StatusBarNotification) {
        if (!prefs.isCallAnnouncementEnabled || callRecentlyAnnounced()) return
        // Ringing calls use a full-screen intent; ongoing-call notifications don't.
        if (!isRinging && sbn.notification.fullScreenIntent == null) return

        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        handler.removeCallbacks(announceCallFallback)
        val ca = com.faiza.ai.ai.calls.CallAssist
        if (sbn.packageName in ca.CALL_APPS) ca.appRinging(sbn, title) else ca.phoneRinging(null, title)
        announceCall(title)
    }

    private fun handleMessageNotification(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val appName = messagingAppName(sbn.packageName) ?: return
        if (isBusy()) return

        val sender = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isEmpty() || text.isEmpty()) return

        // One announcement per sender per minute, so a burst of messages isn't read out ten times.
        val key = "${sbn.packageName}|$sender"
        val now = System.currentTimeMillis()
        lastMessageAnnouncedAt.entries.removeAll { now - it.value > MESSAGE_DEDUPE_MS }
        if (lastMessageAnnouncedAt.containsKey(key)) return
        lastMessageAnnouncedAt[key] = now

        if (com.faiza.ai.ai.driving.Driving.active(context) && onDrivingMessage != null) {
            val bangla = language.base == com.faiza.ai.ai.PhraseBase.BENGALI
            val who = com.faiza.ai.ai.calls.CallAssist.friendlyName(null, com.faiza.ai.ai.voice.VoiceNotes.cleanSender(sender)) ?: sender
            onDrivingMessage.invoke(com.faiza.ai.ai.driving.Driving.messageLine(bangla, who, appName, text), appName, who)
            return
        }
        speak(Phrases.newMessage(language, appName, sender))
    }

    private fun announceCall(caller: String?) {
        lastCallAnnouncedAt = System.currentTimeMillis()
        val ca = com.faiza.ai.ai.calls.CallAssist
        val r = ca.ringing
        val number = r?.number ?: pendingNumber
        val bangla = language.base == com.faiza.ai.ai.PhraseBase.BENGALI
        val address = prefs.userAddress.trim().ifEmpty { if (bangla) "বস" else "Boss" }
        val name = ca.friendlyName(number, caller ?: number?.let { lookupContactName(it) })
        onCall(ca.announceLine(bangla, address, name, r?.app, number))
    }

    private fun callRecentlyAnnounced() =
        System.currentTimeMillis() - lastCallAnnouncedAt < CALL_DEDUPE_MS

    private fun messagingAppName(pkg: String): String? {
        MESSAGING_APPS[pkg]?.let { return it }
        val defaultSms = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
        return if (pkg == defaultSms) "SMS" else null
    }

    private fun lookupContactName(number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: SecurityException) {
            null
        }
    }
}
