package com.faiza.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.faiza.ai.ai.memory.MemoryStore
import com.faiza.ai.license.LicenseManager
import com.faiza.ai.util.CrashReporter
import com.faiza.ai.util.PrefsManager

class FaizaApp : Application() {

    companion object {
        lateinit var instance: FaizaApp
            private set

        const val CHANNEL_FOREGROUND = "faiza_foreground"
        const val CHANNEL_ALERTS = "faiza_alerts"
        /**
         * Family edition (owner, 2026-10-31: "আমি আর আমার বউ ব্যবহার করছি — সব খোলা থাকবে"): every Premium feature
         * is open in every build, no license, no trial limit. The "Premium" labels stay. Set to BuildConfig.DEBUG
         * again if Faiza is ever sold (then the release APK asks for a license).
         */
        val UNLOCK_ALL_FOR_TESTING: Boolean = true

        const val APP_NAME = "Faiza AI"
        const val VERSION = "3.0.0"

        /** License server: FAIZA_SERVER_URL in android/gradle.properties (release); the PC for the debug APK. */
        const val SERVER_URL = BuildConfig.SERVER_URL

        /** The release was built without a real server address: licenses can't be checked yet. */
        val serverMissing: Boolean get() = SERVER_URL.contains("CHANGE-ME")
    }

    lateinit var prefs: PrefsManager
    lateinit var licenseManager: LicenseManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.install(this)

        prefs = PrefsManager(this)
        licenseManager = LicenseManager(this)
        MemoryStore.init(this)
        // Android forgets alarms after a reboot or an update; set scheduled tasks again.
        runCatching { com.faiza.ai.ai.routines.RoutineCenter.rescheduleAll(this) }
        runCatching { com.faiza.ai.ai.macros.MacroCenter.seedBuiltIns() }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Foreground service channel
            val foregroundChannel = NotificationChannel(
                CHANNEL_FOREGROUND,
                "Faiza AI Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Faiza AI is running in the background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(foregroundChannel)

            // Alerts channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Faiza AI Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts from Faiza AI"
            }
            manager.createNotificationChannel(alertsChannel)
        }
    }
}
