package com.faiza.ai.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.faiza.ai.FaizaApp

/**
 * Quiet mode (the owner's idea): out in public or in a meeting, Faiza must not start talking by herself.
 *
 * - Manual quiet (button, notification, or "চুপ থাকো, বাইরে যাচ্ছি"): no unprompted speech AND no always-on
 *   listening / wake word, so voices around you can't wake her. Tapping her still works.
 * - Follow the phone: while the phone is on silent / vibrate / Do Not Disturb, no unprompted speech.
 * - Headphones exception: with earphones in, announcements are private, so she may still speak them.
 */
object QuietMode {

    /** Pure decision, so it is unit-tested. */
    data class Decision(val blocksSpeech: Boolean, val blocksListening: Boolean)

    fun decide(
        manualOn: Boolean, until: Long, now: Long,
        followPhone: Boolean, phoneSilent: Boolean,
        allowHeadphones: Boolean, headphones: Boolean,
        lifeModeOn: Boolean = false
    ): Decision {
        val manual = manualOn && (until == 0L || now < until)
        val quietNow = manual || (followPhone && phoneSilent) || lifeModeOn
        val speech = quietNow && !(allowHeadphones && headphones)
        return Decision(blocksSpeech = speech, blocksListening = manual)
    }

    private val prefs get() = FaizaApp.instance.prefs

    /** Manual quiet mode is on (switches itself off when its time is up). */
    fun manualActive(): Boolean {
        if (!prefs.isQuietMode) return false
        val until = prefs.quietUntil
        if (until != 0L && System.currentTimeMillis() >= until) {
            prefs.isQuietMode = false
            return false
        }
        return true
    }

    fun phoneSilent(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        am?.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                (nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL)
    }.getOrDefault(false)

    fun headphonesConnected(ctx: Context): Boolean = runCatching {
        val types = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET
        )
        ctx.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type in types } == true
    }.getOrDefault(false)

    fun current(ctx: Context): Decision = decide(
        manualOn = manualActive(), until = prefs.quietUntil, now = System.currentTimeMillis(),
        followPhone = prefs.isQuietFollowPhone, phoneSilent = phoneSilent(ctx),
        allowHeadphones = prefs.isQuietHeadphonesOk, headphones = headphonesConnected(ctx),
        lifeModeOn = runCatching { com.faiza.ai.ai.modes.ModeCenter.currentId(ctx) != null }.getOrDefault(false)
    )

    /** She may speak without being asked (announcements, reminders, proactive chat). */
    fun canSpeakUnprompted(ctx: Context) = !current(ctx).blocksSpeech

    /** Turns manual quiet on (for [minutes], or until switched off) or off. */
    fun set(on: Boolean, minutes: Int? = null) {
        prefs.quietUntil = if (on && minutes != null && minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        prefs.isQuietMode = on
    }

    fun statusText(): String {
        if (!manualActive()) return ""
        val until = prefs.quietUntil
        return if (until == 0L) "Quiet mode — tap 🌙 to wake her"
        else "Quiet until " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(until))
    }
}
