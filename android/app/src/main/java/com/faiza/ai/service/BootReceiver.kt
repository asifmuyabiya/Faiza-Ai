package com.faiza.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.faiza.ai.FaizaApp

/**
 * Starts Faiza AI automatically on device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = FaizaApp.instance.prefs

            // Only auto-start if user has valid access and wake word is enabled
            if (prefs.hasValidAccess() && prefs.isWakeWordEnabled && prefs.isStartOnBoot) {
                val serviceIntent = Intent(context, FloatingOrbService::class.java)
                runCatching { context.startForegroundService(serviceIntent) }
            }
        }
    }
}
