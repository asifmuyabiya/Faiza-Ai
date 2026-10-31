import os, sys
android = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android'
main = os.path.join(android, 'app', 'src', 'main', 'java', 'com', 'faiza', 'ai')
sys.path.insert(0, os.path.join(android, 'tools'))


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


open(os.path.join(main, 'ai', 'wake', 'WakePower.kt'), 'w', encoding='utf-8').write('''package com.faiza.ai.ai.wake

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
''')

p = 'service/FloatingOrbService.kt'
edit(p, '''            // "Hey Faiza only while the screen is on": rest the mic and processor while it's off.
            if (!prefs.isWakeScreenOnly || live != null) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    wakeEngine?.stop()
                    wakeRestartJob?.cancel()
                    speechRecognizer?.cancel()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startListeningForWakeWord()
            }''', '''            // Screen off (or battery/charger changed while off): keep listening only if allowed — rest the mic otherwise.
            if (live != null || isProcessing || isActiveListening) return
            if (wakeAllowedNow()) startListeningForWakeWord()
            else {
                wakeEngine?.stop()
                wakeRestartJob?.cancel()
                speechRecognizer?.cancel()
            }''')
edit(p, '''                addAction(Intent.ACTION_USER_PRESENT)
            })''', '''                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
            })''')
edit(p, '''        if (prefs.isWakeScreenOnly &&
            (getSystemService(POWER_SERVICE) as PowerManager).isInteractive.not()
        ) return // screen off: listening resumes when it turns on''', '''        if (!wakeAllowedNow()) return // screen off + battery rule: listening resumes when the screen turns on / charger''')
edit(p, '''    private fun startListeningForWakeWord() {''', '''    /** Screen on: yes. Screen off: per "Screen on only" / smart battery (charging or 30%+). */
    private fun wakeAllowedNow(): Boolean {
        if ((getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return true
        val (pct, charging) = com.faiza.ai.ai.wake.WakePower.battery(this)
        return com.faiza.ai.ai.wake.WakePower.allowScreenOff(prefs.isWakeScreenOnly, prefs.isWakeSmartBattery, pct, charging)
    }

    private fun startListeningForWakeWord() {''')

edit('util/PrefsManager.kt', '''    /** Send only the everyday tools in full''', '''    /** Screen off: listen for "Hey Faiza" only while charging or 30%+ battery. */
    var isWakeSmartBattery: Boolean
        get() = prefs.getBoolean("wake_smart_battery", true)
        set(value) = prefs.edit().putBoolean("wake_smart_battery", value).apply()

    /** Send only the everyday tools in full''')

edit('ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowWakeScreenOnly).setOnClickListener { swWakeScreen.toggle() }''', '''        findViewById<View>(R.id.rowWakeScreenOnly).setOnClickListener { swWakeScreen.toggle() }
        val swWakeBattery = findViewById<SwitchMaterial>(R.id.swWakeSmartBattery)
        swWakeBattery.isChecked = prefs.isWakeSmartBattery
        swWakeBattery.setOnCheckedChangeListener { _, on -> prefs.isWakeSmartBattery = on }
        findViewById<View>(R.id.rowWakeSmartBattery).setOnClickListener { swWakeBattery.toggle() }''')

import settings_rows as sr
sr.apply('rowWakeScreenOnly', [sr.row('wakeSmartBattery', 'ic_battery', 'green', 'Smart battery listening',
                                      'Screen off: listens for "Hey Faiza" only while charging or above 30%', 'switch')])
print('ok')
