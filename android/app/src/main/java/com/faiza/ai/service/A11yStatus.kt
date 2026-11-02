package com.faiza.ai.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.delay

/**
 * Is Faiza's screen service really working? Android can leave the Accessibility switch ON while it has stopped the
 * service (after an app update, battery saving, or a crash). The Permissions screen used to look only at the switch
 * and the tools only at the running service, so Faiza kept saying "turn Accessibility on" while it already was.
 */
object A11yStatus {

    enum class State { OFF, ON_NOT_RUNNING, RUNNING }

    /** Is our service ticked in Settings → Accessibility? */
    fun enabledInSettings(ctx: Context): Boolean = runCatching {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(ctx, FaizaAccessibilityService::class.java)
        list.split(':').any { entry ->
            val c = ComponentName.unflattenFromString(entry.trim())
            c != null && c.packageName == me.packageName && c.className == me.className
        }
    }.getOrDefault(false)

    fun state(ctx: Context): State = when {
        FaizaAccessibilityService.instance != null -> State.RUNNING
        enabledInSettings(ctx) -> State.ON_NOT_RUNNING
        else -> State.OFF
    }

    /** What the tools answer, so Faiza tells the user the right fix (pure, unit-tested). */
    fun message(state: State): String = when (state) {
        State.RUNNING -> "OK"
        State.OFF -> "ERROR: Faiza's Accessibility service is switched OFF, so she can't see or control the screen. " +
                "Ask the user to turn on \"Faiza AI\" in Settings → Accessibility (I opened that page)."
        State.ON_NOT_RUNNING -> "ERROR: Accessibility IS switched on, but Android has stopped Faiza's screen service " +
                "(this happens after an app update, battery saving or a crash). Do NOT tell the user to turn it on — it " +
                "is on. Tell them in their language: Settings → Accessibility → Faiza AI, switch it OFF once and then ON " +
                "again, then ask you again. (I opened that page.)"
    }

    @Volatile private var lastOpenedAt = 0L

    /** Opens Settings → Accessibility (at most once a minute, so a retry loop can't keep throwing it up). */
    fun openSettings(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpenedAt < 60_000) return
        lastOpenedAt = now
        Handler(Looper.getMainLooper()).post {
            runCatching {
                ctx.applicationContext.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * The running service, waiting a moment if the switch is on but Android hasn't connected it yet (right after an
     * update it can take a second or two). Null = not available; [errorText] then says why and what to do.
     */
    suspend fun awaitService(ctx: Context, waitMs: Long = 2_500): FaizaAccessibilityService? {
        FaizaAccessibilityService.instance?.let { return it }
        if (enabledInSettings(ctx)) {
            val until = SystemClock.elapsedRealtime() + waitMs
            while (SystemClock.elapsedRealtime() < until) {
                delay(150)
                FaizaAccessibilityService.instance?.let { return it }
            }
        }
        return null
    }

    /** Error text for the current state, and opens the settings page so the user can fix it. */
    fun errorText(ctx: Context): String {
        val s = state(ctx)
        if (s != State.RUNNING) openSettings(ctx)
        return message(s)
    }
}
