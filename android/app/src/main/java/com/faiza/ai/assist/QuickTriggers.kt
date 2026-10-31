package com.faiza.ai.assist

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent
import com.faiza.ai.service.FloatingOrbService

/**
 * Quick ways to call Faiza without saying her name (owner: "বাটন চাপলেই ফাইজা"):
 * a Quick Settings tile, a double press on the earphone button, and app-icon shortcuts (res/xml/shortcuts.xml).
 */
class FaizaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (FloatingOrbService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = "Tap to talk"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Starting the see-through AssistActivity also closes the notification shade.
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

/**
 * Earphone button: one press still plays/pauses music as usual; a quick SECOND press calls Faiza
 * (and is kept from the music app). Pure logic so it can be tested.
 */
class HeadsetDoublePress(private val windowMs: Long = 450) {

    private var lastDown = 0L
    private var swallowUp = false

    /** Returns true when this key event belongs to the double press (swallow it). [onTrigger] fires once. */
    fun onKey(keyCode: Int, action: Int, time: Long, onTrigger: () -> Unit): Boolean {
        if (keyCode !in KEYS) return false
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (lastDown != 0L && time - lastDown <= windowMs) {
                    lastDown = 0L
                    swallowUp = true
                    onTrigger()
                    return true
                }
                lastDown = time
                return false
            }
            KeyEvent.ACTION_UP -> if (swallowUp) { swallowUp = false; return true }
        }
        return false
    }

    companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE
        )
    }
}
