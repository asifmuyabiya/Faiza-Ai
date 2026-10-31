package com.faiza.ai.ai.wake

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Smart battery for "Hey Faiza" (owner: "ব্যাটারি আরও কম খরচ"): with the screen off she only keeps listening while
 * charging or above [MIN_BATTERY]%. Screen on: always listening (as before).
 */
object WakePower {

    const val MIN_BATTERY = 30

    /** Pure rule (tested). */
    fun allowScreenOff(screenOnly: Boolean, smart: Boolean, batteryPct: Int, charging: Boolean): Boolean = when {
        screenOnly -> false
        !smart -> true
        else -> charging || batteryPct >= MIN_BATTERY
    }

    fun battery(ctx: Context): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 100 to true
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return (if (level < 0) 100 else level * 100 / scale) to charging
    }
}
