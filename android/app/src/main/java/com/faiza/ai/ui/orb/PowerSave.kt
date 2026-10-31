package com.faiza.ai.ui.orb

import android.content.Context
import android.os.PowerManager

/** Is the phone in Battery Saver? Checked at most once a second (animations ask every frame). */
object PowerSave {
    private var checkedAt = 0L
    private var cached = false

    fun on(ctx: Context): Boolean {
        // Gaming mode: keep the orb's animation light so the game gets the GPU.
        if (com.faiza.ai.ai.game.GameMode.active.value) return true
        val now = System.currentTimeMillis()
        if (now - checkedAt > 1_000) {
            checkedAt = now
            cached = runCatching { ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
        }
        return cached
    }
}
