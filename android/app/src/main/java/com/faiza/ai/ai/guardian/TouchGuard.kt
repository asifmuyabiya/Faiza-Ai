package com.faiza.ai.ai.guardian

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.SystemClock
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.util.ActivityLog

/**
 * Touch Guard (MAYA): when armed and you're away, anyone who wakes the screen, picks the phone up or pulls the
 * charger out gets photographed with the front camera; Faiza tells you out loud, warns the holder, and sounds a siren.
 */
object TouchGuard {

    private val prefs get() = FaizaApp.instance.prefs
    private val base: PhraseBase get() = AppLanguage.from(prefs.language).base

    private var registered = false
    private var lastFireAt = 0L
    private var sensorManager: SensorManager? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> fire(ctx, reason = "screen")
                Intent.ACTION_POWER_DISCONNECTED -> fire(ctx, reason = "charger")
            }
        }
    }

    private val motionListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            FaizaApp.instance.let { fire(it, reason = "pickup") }
            // One-shot sensor: re-arm for the next pickup.
            armMotion()
        }
    }

    fun start(ctx: Context) {
        if (registered || !prefs.isTouchGuard) return
        registered = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        runCatching { ctx.registerReceiver(receiver, f) }
        sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        armMotion()
    }

    private fun armMotion() {
        val sm = sensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        runCatching { sm.requestTriggerSensor(motionListener, sensor) }
    }

    fun stop(ctx: Context) {
        if (!registered) return
        registered = false
        runCatching { ctx.unregisterReceiver(receiver) }
        sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let {
            runCatching { sensorManager?.cancelTriggerSensor(motionListener, it) }
        }
    }

    /** Settings changed: arm or disarm. */
    fun refresh(ctx: Context) {
        if (prefs.isTouchGuard) start(ctx) else stop(ctx)
    }

    private fun fire(ctx: Context, reason: String) {
        if (!prefs.isTouchGuard) return
        // Not for the owner: Voice Guardian / Owner lock knowing it's the boss would clear it, but Touch Guard is a
        // simple "I stepped away" watch — one event at a time, with a cooldown so a single pickup fires once.
        val now = SystemClock.elapsedRealtime()
        if (now - lastFireAt < 8_000) return
        lastFireAt = now
        OwnerGuard.capturePhoto(ctx, strike = 1)
        ActivityLog.add("security", "Touch Guard", "Someone ${reasonText(reason)} — photo taken")
        val line = when (base) {
            PhraseBase.BENGALI -> "কে আপনি? এটা আমার বসের ফোন! আমি আপনার ছবি তুলে রেখেছি। ফোনটা রেখে দিন!"
            PhraseBase.HINDI -> "कौन हैं आप? ये मेरे बॉस का फोन है! मैंने आपकी फोटो ले ली है। फोन रख दीजिए!"
            PhraseBase.ENGLISH -> "Who are you? This is my boss's phone! I've taken your photo. Put it down!"
        }
        FloatingOrbService.instance?.touchGuardAlert(line)
    }

    private fun reasonText(reason: String) = when (reason) {
        "charger" -> "pulled the charger out"
        "pickup" -> "picked the phone up"
        else -> "woke the screen"
    }

    /** The siren: the phone's alarm tone, briefly, at alarm volume. */
    fun siren(ctx: Context) {
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(ctx, uri) ?: return
            runCatching {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            r.play()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { r.stop() } }, 4000)
        }
    }
}
