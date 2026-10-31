package com.faiza.ai.ui

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

/** MAYA "Event triggers": what Faiza announces on her own (power, battery, headphones, network, apps). */
class EventTriggersActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(col)
        setContentView(root)

        col.addView(topBar())
        col.addView(caption("Faiza speaks up on her own when the phone does something. Each line is short."))

        col.addView(section("📣 Announcements"))
        col.addView(card(
            sw("Event announcements", "Master switch — off means she never brings any of this up", { prefs.isEventTriggers }, { prefs.isEventTriggers = it }),
            sw("Speak while she's asleep", "Uses the phone's own voice when no session is live. Stays quiet on silent and in Do Not Disturb.", { prefs.isSpeakWhileAsleep }, { prefs.isSpeakWhileAsleep = it }),
            sw("Speak on silent too", "Ignore silent mode and Do Not Disturb. Calls still silence her.", { prefs.isSpeakOnSilent }, { prefs.isSpeakOnSilent = it })
        ))
        col.addView(caption("💡 These only change what Faiza says. Nothing here alters a phone setting."))

        col.addView(section("🌦️ Weather & meals"))
        col.addView(card(
            sw("Weather heads-ups", "Morning forecast, rain coming, very hot — each said once", { prefs.isWeatherAlerts }, { prefs.isWeatherAlerts = it }),
            sw("Meal-time check-ins", "Asks about breakfast 9:30, lunch 2:30, snack 5:30, dinner 10 pm (edit in Routines)",
                { prefs.isMealReminders }, { com.faiza.ai.ai.routines.MealReminders.apply(this, it) })
        ))

        col.addView(section("🔋 Power & battery"))
        col.addView(card(
            sw("Charger plugged in", null, { prefs.trigChargerIn }, { prefs.trigChargerIn = it }),
            sw("Charger unplugged", null, { prefs.trigChargerOut }, { prefs.trigChargerOut = it }),
            sw("Battery full", null, { prefs.trigBatteryFull }, { prefs.trigBatteryFull = it }),
            sw("Battery low (20%)", "Once per discharge, not every reading", { prefs.trigBatteryLow }, { prefs.trigBatteryLow = it }),
            sw("Battery critical (10%)", "Once per discharge, not every reading", { prefs.trigBatteryCritical }, { prefs.trigBatteryCritical = it })
        ))

        col.addView(section("🎧 Headphones & Bluetooth"))
        col.addView(card(
            sw("Headphones plugged in", null, { prefs.trigHeadphonesIn }, { prefs.trigHeadphonesIn = it }),
            sw("Headphones unplugged", null, { prefs.trigHeadphonesOut }, { prefs.trigHeadphonesOut = it }),
            sw("Bluetooth device connected", null, { prefs.trigBtOn }, { prefs.trigBtOn = it }),
            sw("Bluetooth device disconnected", null, { prefs.trigBtOff }, { prefs.trigBtOff = it })
        ))

        col.addView(section("📶 Network"))
        col.addView(card(
            sw("Wi-Fi connected", "Off by default — networks change all day", { prefs.trigWifiOn }, { prefs.trigWifiOn = it }),
            sw("Wi-Fi lost", "Off by default — networks change all day", { prefs.trigWifiOff }, { prefs.trigWifiOff = it }),
            sw("Airplane mode on", null, { prefs.trigAirplaneOn }, { prefs.trigAirplaneOn = it }),
            sw("Airplane mode off", null, { prefs.trigAirplaneOff }, { prefs.trigAirplaneOff = it })
        ))

        col.addView(section("⚙️ System"))
        col.addView(card(
            sw("Phone put on silent", "Off by default — you just did it yourself", { prefs.trigPhoneSilent }, { prefs.trigPhoneSilent = it }),
            sw("Ringer back on", "Off by default — you just did it yourself", { prefs.trigRingerBack }, { prefs.trigRingerBack = it }),
            sw("App installed", "New apps only, not Play Store updates", { prefs.trigAppInstalled }, { prefs.trigAppInstalled = it }),
            sw("App uninstalled", "Real uninstalls only, not updates", { prefs.trigAppUninstalled }, { prefs.trigAppUninstalled = it })
        ))
    }

    // ===== Builders =====

    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(10))
        }
        bar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            background = null
            imageTintList = getColorStateList(R.color.text_primary)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        bar.addView(TextView(this).apply {
            text = "Event triggers"
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            setPadding(dp(8), 0, 0, 0)
        })
        return bar
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(getColor(R.color.text_secondary))
        setPadding(dp(6), dp(20), 0, dp(6))
        isAllCaps = true
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.text_secondary))
        setPadding(dp(6), dp(10), dp(6), 0)
        setLineSpacing(0f, 1.2f)
    }

    private fun card(vararg rows: View): View {
        val cv = MaterialCardView(this).apply {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { i, r ->
            if (i > 0) box.addView(divider())
            box.addView(r)
        }
        cv.addView(box)
        return cv
    }

    private fun sw(title: String, sub: String?, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(16), dp(10), dp(14), dp(10))
        }
        val switch = SwitchMaterial(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, on -> set(on) }
        }
        row.setOnClickListener { switch.toggle() }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
            })
            if (sub != null) addView(TextView(context).apply {
                text = sub
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(2), 0, 0)
            })
        })
        row.addView(switch)
        return row
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(16) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
