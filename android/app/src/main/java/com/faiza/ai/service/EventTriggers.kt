package com.faiza.ai.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.PhraseBase

/**
 * Event triggers (MAYA): Faiza speaks up on her own when the phone does something — charger, battery, headphones,
 * Bluetooth, network, installs. Each line is short. Every trigger has its own switch; a master switch covers all.
 */
object EventTriggers {

    private val prefs get() = FaizaApp.instance.prefs
    private val base: PhraseBase get() = AppLanguage.from(prefs.language).base

    private var lastLowAt = 0L
    private var lastCriticalAt = 0L
    private var lastBatteryPct = -1
    private var fullSpoken = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            runCatching { handle(ctx, intent) }
        }
    }
    private var registered = false

    fun start(ctx: Context) {
        if (registered) return
        registered = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction("android.intent.action.AIRPLANE_MODE")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            else ctx.registerReceiver(receiver, f)
        }
        // Package install / uninstall need their own data-scheme filter.
        val pkgF = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, pkgF, Context.RECEIVER_EXPORTED)
            else ctx.registerReceiver(receiver, pkgF)
        }
    }

    fun stop(ctx: Context) {
        if (!registered) return
        registered = false
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    private fun say(text: String) {
        if (!prefs.isEventTriggers) return
        FloatingOrbService.instance?.announceEvent(text)
    }

    private fun handle(ctx: Context, intent: Intent) {
        if (!prefs.isEventTriggers) return
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> if (prefs.trigChargerIn) say(l("Charging now.", "চার্জ হচ্ছে।", "चार्ज हो रहा है।"))
            Intent.ACTION_POWER_DISCONNECTED -> if (prefs.trigChargerOut) say(l("Charger unplugged.", "চার্জার খুলে গেছে।", "चार्जर निकल गया।"))
            Intent.ACTION_BATTERY_CHANGED -> battery(intent)
            Intent.ACTION_HEADSET_PLUG -> {
                val on = intent.getIntExtra("state", 0) == 1
                if (on && prefs.trigHeadphonesIn) say(l("Headphones connected.", "হেডফোন লেগেছে।", "हेडफोन लग गया।"))
                else if (!on && prefs.trigHeadphonesOut) say(l("Headphones unplugged.", "হেডফোন খুলে গেছে।", "हेडफोन निकल गया।"))
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> if (prefs.trigBtOn) say(l("Bluetooth device connected.", "ব্লুটুথ যুক্ত হয়েছে।", "ब्लूटूथ जुड़ गया।"))
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (prefs.trigBtOff) say(l("Bluetooth device disconnected.", "ব্লুটুথ বিচ্ছিন্ন হয়েছে।", "ब्लूटूथ अलग हो गया।"))
            "android.intent.action.AIRPLANE_MODE" -> {
                val on = intent.getBooleanExtra("state", false)
                if (on && prefs.trigAirplaneOn) say(l("Airplane mode on.", "এয়ারপ্লেন মোড চালু।", "एयरप्लेन मोड चालू।"))
                else if (!on && prefs.trigAirplaneOff) say(l("Airplane mode off.", "এয়ারপ্লেন মোড বন্ধ।", "एयरप्लेन मोड बंद।"))
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION -> wifi(ctx)
            AudioManager.RINGER_MODE_CHANGED_ACTION -> ringer(intent)
            Intent.ACTION_PACKAGE_ADDED -> if (prefs.trigAppInstalled && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
                say(l("An app was installed.", "নতুন একটা অ্যাপ ইনস্টল হয়েছে।", "एक ऐप इंस्टॉल हुआ।"))
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> if (prefs.trigAppUninstalled)
                say(l("An app was uninstalled.", "একটা অ্যাপ আনইনস্টল হয়েছে।", "एक ऐप अनइंस्टॉल हुआ।"))
        }
    }

    private fun battery(intent: Intent) {
        val pct = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 /
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (pct < 0) return
        val charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }
        if (pct >= 100 && charging) {
            if (prefs.trigBatteryFull && !fullSpoken) {
                fullSpoken = true
                say(l("Battery is full.", "ব্যাটারি ফুল হয়ে গেছে।", "बैटरी फुल हो गई।"))
            }
        } else if (pct < 100) fullSpoken = false
        // Low / critical: once per discharge (reset when it rises above the threshold again).
        if (!charging) {
            val now = System.currentTimeMillis()
            if (pct <= 10 && prefs.trigBatteryCritical && (lastBatteryPct > 10 || lastCriticalAt == 0L) && now - lastCriticalAt > 60_000) {
                lastCriticalAt = now
                say(l("Battery critical — ten percent left.", "ব্যাটারি খুব কম, দশ শতাংশ বাকি।", "बैटरी बहुत कम, दस प्रतिशत बची है।"))
            } else if (pct in 11..20 && prefs.trigBatteryLow && (lastBatteryPct > 20 || lastLowAt == 0L) && now - lastLowAt > 60_000) {
                lastLowAt = now
                say(l("Battery low, twenty percent.", "ব্যাটারি কম, বিশ শতাংশ।", "बैटरी कम, बीस प्रतिशत।"))
            }
        }
        lastBatteryPct = pct
    }

    private var lastWifiOn: Boolean? = null
    private fun wifi(ctx: Context) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        val connected = runCatching {
            val n = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(n) ?: return@runCatching false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
        if (connected == lastWifiOn) return
        val was = lastWifiOn
        lastWifiOn = connected
        if (was == null) return // first reading, don't announce
        if (connected && prefs.trigWifiOn) say(l("Wi-Fi connected.", "ওয়াই-ফাই যুক্ত হয়েছে।", "वाई-फाई जुड़ गया।"))
        else if (!connected && prefs.trigWifiOff) say(l("Wi-Fi lost.", "ওয়াই-ফাই চলে গেছে।", "वाई-फाई चला गया।"))
    }

    private var lastRinger = -1
    private fun ringer(intent: Intent) {
        val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        if (mode == lastRinger) return
        val was = lastRinger
        lastRinger = mode
        if (was == -1) return
        if (mode == AudioManager.RINGER_MODE_NORMAL && prefs.trigRingerBack) say(l("Ringer is back on.", "রিং আবার চালু হয়েছে।", "रिंगर वापस चालू।"))
        else if (mode != AudioManager.RINGER_MODE_NORMAL && prefs.trigPhoneSilent) say(l("Phone is on silent now.", "ফোন সাইলেন্ট হয়েছে।", "फोन साइलेंट हो गया।"))
    }

    private fun l(en: String, bn: String, hi: String) = when (base) {
        PhraseBase.BENGALI -> bn
        PhraseBase.HINDI -> hi
        PhraseBase.ENGLISH -> en
    }
}
