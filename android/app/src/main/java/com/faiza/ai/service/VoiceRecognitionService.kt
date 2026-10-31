package com.faiza.ai.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Dedicated voice recognition service for background wake word detection.
 * Placeholder - full implementation in later phases.
 */
class VoiceRecognitionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
