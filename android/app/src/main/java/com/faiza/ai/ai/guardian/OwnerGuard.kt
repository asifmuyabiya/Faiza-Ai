package com.faiza.ai.ai.guardian

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.sos.LocationHelper
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.ui.IntruderCaptureActivity
import com.faiza.ai.util.ActivityLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A photo taken when someone without the secret code used Faiza. */
data class Intruder(val at: Long, val path: String, val lat: Double? = null, val lon: Double? = null, val strike: Int = 1)

/** Owner lock on the phone: who must say the code, strikes, intruder photos, the alert text and the lock. */
object OwnerGuard {

    enum class Kind { UNLOCKED, ASK, WARNED, LOCKING }

    data class Outcome(val kind: Kind, val line: String)

    private val prefs get() = FaizaApp.instance.prefs
    private val gson = Gson()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var strikes = 0
    @Volatile private var lastStrikeAt = 0L

    val base: PhraseBase get() = AppLanguage.from(prefs.language).base

    fun needsCode(): Boolean {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return OwnerLock.needsCode(
            prefs.isOwnerLock, prefs.ownerLockCode, prefs.ownerLockAsk,
            prefs.ownerVerifiedAt, prefs.lastPhoneUnlockAt, midnight
        )
    }

    /** Screen went off / phone unlocked: a code said before now no longer counts ("after each unlock"). */
    fun onScreenEvent() {
        prefs.lastPhoneUnlockAt = System.currentTimeMillis()
    }

    /** "Every conversation": the conversation ended, so the next one asks again. */
    fun onConversationEnded() {
        if (prefs.ownerLockAsk == OwnerLock.ASK_ALWAYS) prefs.ownerVerifiedAt = 0L
    }

    fun markVerified() {
        prefs.ownerVerifiedAt = System.currentTimeMillis()
        strikes = 0
        lastStrikeAt = 0L
    }

    fun saysCode(text: String): Boolean {
        val code = prefs.ownerLockCode ?: return false
        return OwnerLock.matches(text, code)
    }

    /**
     * Someone who must say the code said [text]. Right code → unlocked; otherwise a strike:
     * 1st = photo + warning, 2nd = photo + alert text + the phone locks after [lockAfterMs].
     */
    fun attempt(ctx: Context, text: String, lockAfterMs: Long = 6_000L): Outcome {
        if (saysCode(text)) {
            markVerified()
            ActivityLog.add("security", "Owner verified", "Secret code accepted")
            return Outcome(Kind.UNLOCKED, OwnerLock.welcomeLine(base))
        }
        val now = System.currentTimeMillis()
        strikes = OwnerLock.nextStrike(strikes, lastStrikeAt, now)
        lastStrikeAt = now
        capturePhoto(ctx, strikes)
        return if (strikes >= OwnerLock.LOCK_AT_STRIKE) {
            val texted = sendAlert(ctx)
            ActivityLog.add("security", "Phone locked — stranger", "No secret code twice · photo saved")
            lockSoon(lockAfterMs)
            Outcome(Kind.LOCKING, OwnerLock.lockLine(base, texted))
        } else {
            ActivityLog.add("security", "Stranger warned", "Someone used Faiza without the secret code · photo saved")
            Outcome(Kind.WARNED, OwnerLock.warnLine(base))
        }
    }

    fun lockSoon(afterMs: Long) {
        main.postDelayed({
            strikes = 0
            lastStrikeAt = 0L
            FaizaAccessibilityService.instance?.lockScreen()
        }, afterMs)
    }

    // ===== Photo =====

    fun capturePhoto(ctx: Context, strike: Int) {
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        runCatching {
            ctx.startActivity(
                Intent(ctx, IntruderCaptureActivity::class.java)
                    .putExtra(IntruderCaptureActivity.EXTRA_STRIKE, strike)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            )
        }
    }

    fun photoDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: ctx.filesDir, "intruders").apply { mkdirs() }

    fun intruders(): List<Intruder> = runCatching {
        gson.fromJson<List<Intruder>>(prefs.intrudersJson ?: return emptyList(), object : TypeToken<List<Intruder>>() {}.type)
    }.getOrNull().orEmpty().filter { File(it.path).exists() }

    fun addIntruder(ctx: Context, file: File, strike: Int) {
        val loc = LocationHelper.lastKnown(ctx)
        val list = listOf(Intruder(System.currentTimeMillis(), file.absolutePath, loc?.latitude, loc?.longitude, strike)) + intruders()
        list.drop(40).forEach { File(it.path).delete() }
        prefs.intrudersJson = gson.toJson(list.take(40))
    }

    fun deleteIntruder(i: Intruder) {
        File(i.path).delete()
        prefs.intrudersJson = gson.toJson(intruders().filter { it.path != i.path })
    }

    // ===== Alert text =====

    /** Texts the first SOS person in My people (if the owner allowed it). */
    @Suppress("DEPRECATION")
    private fun sendAlert(ctx: Context): Boolean {
        if (!prefs.ownerLockSms) return false
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val to = FavoriteContacts.sosContacts(FavoriteContacts.load(prefs).filter { it.sos }).firstOrNull() ?: return false
        val owner = prefs.userName?.takeIf { it.isNotBlank() } ?: "the owner"
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date())
        val loc = LocationHelper.lastKnown(ctx)?.let { " Location: https://maps.google.com/?q=${it.latitude},${it.longitude}" }.orEmpty()
        val text = "Faiza alert: someone tried to use $owner's phone without the secret code at $time. " +
                "I took their photo and locked the phone.$loc"
        return runCatching {
            val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            sms.sendMultipartTextMessage(to.number, null, sms.divideMessage(text), null, null)
            true
        }.getOrDefault(false)
    }
}
