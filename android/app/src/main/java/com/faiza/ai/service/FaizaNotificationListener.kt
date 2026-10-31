package com.faiza.ai.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Listens to notifications for Faiza AI features:
 * - Call announcements
 * - Message reading
 * - Notification summaries
 */
class FaizaNotificationListener : NotificationListenerService() {

    companion object {
        var instance: FaizaNotificationListener? = null
            private set

        // Store recent notifications
        val recentNotifications = mutableListOf<NotificationInfo>()
        const val MAX_STORED = 50

        @Volatile
        var onPosted: ((StatusBarNotification) -> Unit)? = null
    }

    data class NotificationInfo(
        val packageName: String,
        val title: String,
        val text: String,
        val timestamp: Long
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val packageName = sbn.packageName

        val info = NotificationInfo(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        synchronized(recentNotifications) {
            recentNotifications.add(0, info)
            if (recentNotifications.size > MAX_STORED) {
                recentNotifications.removeAt(recentNotifications.lastIndex)
            }
        }

        try {
            onPosted?.invoke(sbn)
        } catch (_: Exception) {}
        // "Tell me when…" / auto-reply watches (video 40:31).
        runCatching { com.faiza.ai.ai.routines.RoutineCenter.onNotification(this, sbn) }
        runCatching { com.faiza.ai.ai.modes.ModeCenter.onNotification(this, sbn) }
        runCatching { com.faiza.ai.ai.voice.VoiceNotes.onNotification(packageName, title, text, sbn.postTime) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification dismissed
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

    /**
     * Get recent notifications as readable text
     */
    fun getRecentNotificationsText(count: Int = 5): String {
        return synchronized(recentNotifications) {
            recentNotifications.take(count).joinToString("\n") {
                "${it.title}: ${it.text}"
            }
        }
    }
}
