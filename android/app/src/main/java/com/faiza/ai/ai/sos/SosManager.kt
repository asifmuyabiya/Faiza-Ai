package com.faiza.ai.ai.sos

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.CallLog
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.contacts.FavoriteContact
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.contacts.PhoneNumbers
import com.faiza.ai.ai.contacts.SimHelper
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.SosActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SOS mode (video 25:01): countdown → location → SMS to up to 5 SOS contacts →
 * call them one by one (retry each once if we can tell the call wasn't answered).
 */
object SosManager {

    const val COUNTDOWN_SECONDS = 5
    private const val NOTIFICATION_ID = 4040
    private const val MAX_CALL_MS = 5 * 60_000L

    sealed class State {
        object Idle : State()
        data class Countdown(val secondsLeft: Int) : State()
        data class Running(val step: String) : State()
        data class Finished(val summary: String) : State()
        object Cancelled : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.faiza.ai.util.CrashReporter.guard)
    @Volatile private var job: Job? = null

    val isActive: Boolean get() = job?.isActive == true

    private val prefs get() = FaizaApp.instance.prefs

    fun contacts(): List<FavoriteContact> = FavoriteContacts.sosContacts(FavoriteContacts.load(prefs))

    /** Starts SOS (no-op if already running). False when there are no SOS contacts. */
    fun start(ctx: Context): Boolean {
        if (isActive) return true
        val list = contacts()
        if (list.isEmpty()) return false
        val app = ctx.applicationContext
        job = scope.launch { run(app, list) }
        return true
    }

    /** Stops SOS. Doesn't hang up a call that's already ringing. */
    fun cancel(): Boolean {
        val j = job
        if (j == null || !j.isActive) return false
        j.cancel()
        _state.value = State.Cancelled
        runCatching { NotificationManagerCompat.from(FaizaApp.instance).cancel(NOTIFICATION_ID) }
        return true
    }

    /** The exact text SOS would send right now — for testing without sending anything. */
    suspend fun preview(ctx: Context): String = buildText(ctx.applicationContext)

    private suspend fun buildText(ctx: Context): String {
        val loc = LocationHelper.best(ctx)
        return SosMessage.build(
            userName = prefs.userName,
            personalLine = prefs.sosMessage,
            place = loc?.let { SosMessage.Place(it.latitude, it.longitude, if (it.hasAccuracy()) it.accuracy else null, it.time) }
        )
    }

    private suspend fun run(ctx: Context, contacts: List<FavoriteContact>) {
        var pausedMic = false
        try {
            val names = contacts.joinToString { it.name }
            for (s in COUNTDOWN_SECONDS downTo 1) {
                _state.value = State.Countdown(s)
                notify(ctx, "SOS in $s…", "Will text and call: $names. Tap Cancel to stop.", cancellable = true)
                delay(1_000)
            }

            step(ctx, "Getting your location…")
            val text = buildText(ctx)

            step(ctx, "Texting ${contacts.size} contact(s)…")
            val texted = withContext(Dispatchers.IO) { sendTexts(ctx, contacts, text) }

            // A phone call needs the microphone, so Faiza stops listening until SOS is done.
            FloatingOrbService.instance?.pauseMic()
            pausedMic = true
            val answeredBy = callInTurn(ctx, contacts)

            val summary = buildString {
                append(if (texted == contacts.size) "Texted all ${contacts.size}" else "Texted $texted of ${contacts.size}")
                if (!text.contains("maps.google.com")) append(" (location unavailable)")
                append(". ")
                append(
                    when {
                        answeredBy != null -> "$answeredBy answered the call."
                        !has(ctx, Manifest.permission.CALL_PHONE) -> "Calls need the Phone permission — the dialer was opened."
                        else -> "Calls placed to everyone."
                    }
                )
            }
            _state.value = State.Finished(summary)
            notify(ctx, "SOS sent", summary, cancellable = false)
        } finally {
            if (pausedMic) FloatingOrbService.instance?.resumeMic()
        }
    }

    private fun step(ctx: Context, text: String) {
        _state.value = State.Running(text)
        notify(ctx, "SOS active", text, cancellable = true)
    }

    private fun has(ctx: Context, permission: String) =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun sendTexts(ctx: Context, contacts: List<FavoriteContact>, text: String): Int {
        if (!has(ctx, Manifest.permission.SEND_SMS)) return 0
        val sms = ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        val parts = sms.divideMessage(text)
        return contacts.count { c ->
            runCatching { sms.sendMultipartTextMessage(c.number, null, parts, null, null) }.isSuccess
        }
    }

    /** Calls each contact in order; returns who answered (when the call log can tell us). */
    private suspend fun callInTurn(ctx: Context, contacts: List<FavoriteContact>): String? {
        if (!has(ctx, Manifest.permission.CALL_PHONE)) {
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(contacts.first().number)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return null
        }
        val canTellAnswered = has(ctx, Manifest.permission.READ_CALL_LOG)
        val attempts = if (canTellAnswered) 2 else 1
        // In an emergency never stop at a "which SIM?" popup: use the saved SIM, else SIM 1.
        val slot = prefs.preferredSim.takeIf { it > 0 } ?: if (SimHelper.sims(ctx).size >= 2) 1 else null

        for (c in contacts) {
            for (attempt in 1..attempts) {
                step(ctx, "Calling ${c.name}" + (if (attempt > 1) " again" else "") + "…")
                val startedAt = System.currentTimeMillis()
                val placed = runCatching {
                    ctx.startActivity(SimHelper.callIntent(ctx, c.number, slot).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (!placed) break
                waitForCallToFinish(ctx)
                delay(2_500) // let the call log catch up
                if (canTellAnswered && wasAnswered(ctx, c.number, startedAt)) return c.name
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private suspend fun waitForCallToFinish(ctx: Context) {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        fun callState(): Int? = try {
            tm?.callState
        } catch (_: SecurityException) {
            null
        }
        if (callState() == null) {
            delay(60_000) // can't watch the call: give it a minute
            return
        }
        var waited = 0L
        while (callState() == TelephonyManager.CALL_STATE_IDLE && waited < 15_000) {
            delay(500)
            waited += 500
        }
        if (callState() == TelephonyManager.CALL_STATE_IDLE) return
        val start = SystemClock.elapsedRealtime()
        while (callState() != TelephonyManager.CALL_STATE_IDLE && SystemClock.elapsedRealtime() - start < MAX_CALL_MS) {
            delay(1_000)
        }
    }

    private fun wasAnswered(ctx: Context, number: String, since: Long): Boolean {
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.TYPE} = ?",
                arrayOf((since - 10_000).toString(), CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    if (PhoneNumbers.sameNumber(c.getString(0).orEmpty(), number)) return c.getLong(1) > 0
                }
            }
        } catch (_: SecurityException) {
        }
        return false
    }

    private fun notify(ctx: Context, title: String, text: String, cancellable: Boolean) {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(cancellable)
        if (cancellable) {
            val cancel = PendingIntent.getBroadcast(
                ctx, 1, Intent(ctx, SosCancelReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Cancel SOS", cancel)
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build()) }
    }
}

/** "Cancel SOS" button in the notification. */
class SosCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SosManager.cancel()
    }
}
