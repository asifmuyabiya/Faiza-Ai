package com.faiza.ai.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.ai.modes.LifeMode
import com.faiza.ai.ai.modes.LifeModes
import com.faiza.ai.ai.modes.ModeCenter
import com.faiza.ai.ai.modes.ModeSettings
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Life modes (the owner's idea): Driving, Sleeping, Prayer… and the switches that turn them on by themselves. */
class ModesActivity : AppCompatActivity() {

    private val callPerms: Array<String>
        get() = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG) // Android only shares the caller's number with this
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 28) add(Manifest.permission.ANSWER_PHONE_CALLS)
        }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modes)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnActiveOff).setOnClickListener {
            Toast.makeText(this, ModeCenter.setManual(this, null), Toast.LENGTH_LONG).show()
            render()
        }
        findViewById<View>(R.id.cardPerms).setOnClickListener { requestPermissions(missing(callPerms), 41) }
        findViewById<View>(R.id.btnNewMode).setOnClickListener { createMode() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun missing(perms: Array<String>) =
        perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    private fun render() {
        ModeCenter.checkTransition(this)
        val active = ModeCenter.current(this)
        findViewById<View>(R.id.cardActive).visibility = if (active != null) View.VISIBLE else View.GONE
        if (active != null) {
            val since = com.faiza.ai.FaizaApp.instance.prefs.modeStartedAt
            val calls = ModeCenter.calls().filter { it.at >= since }
            val until = com.faiza.ai.FaizaApp.instance.prefs.modeManualUntil
            findViewById<TextView>(R.id.tvActiveEmoji).text = active.emoji
            findViewById<TextView>(R.id.tvActiveTitle).text = "${active.name} mode is on"
            findViewById<TextView>(R.id.tvActiveSub).text = buildString {
                if (until > 0 && com.faiza.ai.FaizaApp.instance.prefs.modeManualId == active.id) append("Until ${clock(until)} · ")
                append(if (calls.isEmpty()) "No calls yet" else LifeModes.summary(calls))
            }
            // An automatic mode (car, schedule) can't be switched off here — only its switch below.
            findViewById<View>(R.id.btnActiveOff).visibility =
                if (com.faiza.ai.FaizaApp.instance.prefs.modeManualId == active.id) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.cardPerms).visibility = if (missing(callPerms).isEmpty()) View.GONE else View.VISIBLE

        renderModes(active)
        renderAuto()
        renderCallers()
        renderRecent()
    }

    // ===== Modes =====

    private fun renderModes(active: LifeMode?) {
        val list = findViewById<LinearLayout>(R.id.modeList)
        list.removeAllViews()
        ModeCenter.allModes().forEachIndexed { i, m ->
            if (i > 0) list.addView(divider())
            val on = active?.id == m.id
            list.addView(row(
                emoji = m.emoji, title = m.name + if (on) "  · ON" else "",
                subtitle = m.subtitle, highlight = on,
                trailing = TextView(this).apply {
                    text = "✏️"
                    textSize = 18f
                    setPadding(dp(12), dp(8), dp(4), dp(8))
                    setBackgroundResource(ripple())
                    setOnClickListener { editMessage(m) }
                },
                onClick = { if (on) confirmOff(m) else startMode(m) }
            ))
        }
    }

    private fun startMode(m: LifeMode) {
        val labels = arrayOf("Until I turn it off", "30 minutes", "1 hour", "2 hours", "Until morning (8 hours)")
        val minutes = listOf(null, 30, 60, 120, 480)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("${m.emoji} ${m.name}")
            .setItems(labels) { _, which ->
                Toast.makeText(this, ModeCenter.setManual(this, m.id, minutes[which]), Toast.LENGTH_SHORT).show()
                if (missing(callPerms).isNotEmpty()) requestPermissions(missing(callPerms), 41)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmOff(m: LifeMode) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("${m.emoji} ${m.name} is on")
            .setPositiveButton("Turn off") { _, _ ->
                Toast.makeText(this, ModeCenter.setManual(this, null), Toast.LENGTH_LONG).show()
                render()
            }
            .setNegativeButton("Keep on", null)
            .show()
    }

    /** See and change what the caller gets. */
    private fun editMessage(m: LifeMode) {
        val input = input(ModeCenter.messageOverride(m.id) ?: "", "Leave empty to use the default").apply {
            minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val preview = TextView(this).apply {
            text = "They get now:\n" + ModeCenter.messageFor(m)
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(12), 0, 0)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
            addView(preview)
        }
        val b = AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("${m.emoji} ${m.name} — text for callers")
            .setMessage("Use {name} for your name and {ai} for hers.")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                ModeCenter.setMessageOverride(m.id, input.text.toString().trim())
                render()
            }
            .setNegativeButton("Cancel", null)
        if (!m.builtIn) b.setNeutralButton("Delete mode") { _, _ ->
            ModeCenter.saveCustomModes(ModeCenter.customModes().filter { it.id != m.id })
            ModeCenter.setMessageOverride(m.id, null)
            if (com.faiza.ai.FaizaApp.instance.prefs.modeManualId == m.id) ModeCenter.setManual(this, null)
            render()
        }
        b.show()
    }

    private fun createMode() {
        val name = input("", "Name, e.g. Class")
        val emoji = input("", "Emoji, e.g. 🎓")
        val message = input("", "What callers get, e.g. {name} is in class and will call back.").apply {
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            listOf(name, emoji, message).forEach {
                addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8) })
            }
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Create a mode")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) {
                    Toast.makeText(this, "Give the mode a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val msg = message.text.toString().trim().ifEmpty { "{name} is busy right now and will call you back later. — {ai}" }
                ModeCenter.saveCustomModes(ModeCenter.customModes() + LifeModes.custom(n.take(24), emoji.text.toString().trim().take(4), msg))
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Automatic =====

    private fun renderAuto() {
        val s = ModeCenter.settings()
        val list = findViewById<LinearLayout>(R.id.autoList)
        list.removeAllViews()
        list.addView(switchRow("🚗", "Driving when the car connects", "Car Bluetooth on → Driving mode, off when you leave", s.autoDriving) {
            ModeCenter.saveSettings(ModeCenter.settings().copy(autoDriving = it))
            if (it && Build.VERSION.SDK_INT >= 31) requestPermissions(missing(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)), 42)
        })
        list.addView(divider())
        list.addView(switchRow(
            "😴", "Sleeping every night",
            "${minuteText(s.sleepStart)} – ${minuteText(s.sleepEnd)} · tap to change", s.sleepEnabled,
            onClick = { pickSleep() }
        ) { ModeCenter.saveSettings(ModeCenter.settings().copy(sleepEnabled = it)) })
        list.addView(divider())
        val times = ModeCenter.prayerTimesToday()
        list.addView(switchRow(
            "🕌", "Prayer at namaz times",
            if (times.isEmpty()) "20 minutes from each prayer time (uses your location)"
            else "Today: " + times.joinToString("  ") { minuteText(it) }, s.autoPrayer
        ) {
            ModeCenter.saveSettings(ModeCenter.settings().copy(autoPrayer = it))
            if (it) {
                requestPermissions(missing(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)), 43)
                ModeCenter.refreshPrayerTimes(this)
                Toast.makeText(this, "Getting today's prayer times…", Toast.LENGTH_SHORT).show()
            }
        })
        list.addView(divider())
        list.addView(switchRow("🎮", "Gaming while a game is open", "Needs Faiza's accessibility service", s.autoGaming) {
            ModeCenter.saveSettings(ModeCenter.settings().copy(autoGaming = it))
            if (it && FaizaAccessibilityService.instance == null) {
                Toast.makeText(this, "Turn on Faiza in Accessibility so she can see which app is open", Toast.LENGTH_LONG).show()
                runCatching { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
        })
    }

    private fun pickSleep() {
        val s = ModeCenter.settings()
        TimePickerDialog(this, R.style.Theme_FaizaAI_Dialog, { _, h, m ->
            val start = h * 60 + m
            Toast.makeText(this, "Now pick when you wake up", Toast.LENGTH_SHORT).show()
            TimePickerDialog(this, R.style.Theme_FaizaAI_Dialog, { _, h2, m2 ->
                ModeCenter.saveSettings(ModeCenter.settings().copy(sleepStart = start, sleepEnd = h2 * 60 + m2, sleepEnabled = true))
                render()
            }, s.sleepEnd / 60, s.sleepEnd % 60, false).show()
        }, s.sleepStart / 60, s.sleepStart % 60, false).show()
    }

    // ===== Callers =====

    private fun renderCallers() {
        val s = ModeCenter.settings()
        val list = findViewById<LinearLayout>(R.id.callerList)
        list.removeAllViews()
        list.addView(switchRow("💬", "Text the caller", "A polite SMS after declining (normal SMS rates)", s.textCallers) {
            ModeCenter.saveSettings(ModeCenter.settings().copy(textCallers = it))
        })
        list.addView(divider())
        val langs = listOf("auto", "bn", "en", "both")
        val labels = arrayOf("Same as the app", "বাংলা", "English", "বাংলা + English")
        list.addView(row("🌐", "Message language", labels[langs.indexOf(s.lang).coerceAtLeast(0)], onClick = {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Message language")
                .setSingleChoiceItems(labels, langs.indexOf(s.lang).coerceAtLeast(0)) { d, which ->
                    ModeCenter.saveSettings(ModeCenter.settings().copy(lang = langs[which]))
                    d.dismiss()
                    render()
                }
                .show()
        }))
        list.addView(divider())
        val vips = com.faiza.ai.ai.contacts.FavoriteContacts.load(com.faiza.ai.FaizaApp.instance.prefs).filter { it.vip }
        list.addView(row(
            "⭐", "VIPs always ring",
            if (vips.isEmpty()) "Mark people as VIP in My people" else vips.joinToString { it.name },
            onClick = { startActivity(Intent(this, ContactsActivity::class.java)) }
        ))
    }

    private fun renderRecent() {
        val calls = ModeCenter.calls().take(12)
        val list = findViewById<LinearLayout>(R.id.recentList)
        list.removeAllViews()
        calls.forEachIndexed { i, c ->
            if (i > 0) list.addView(divider())
            val mode = ModeCenter.mode(c.modeId)
            val what = when {
                !c.declined -> "rang through"
                c.texted -> "declined · texted"
                else -> "declined"
            }
            list.addView(row(
                mode?.emoji ?: "📞", c.name.ifBlank { c.number.ifBlank { "Unknown" } },
                "${clock(c.at)} · ${mode?.name ?: c.modeId} · $what",
                onClick = {
                    if (c.number.isNotBlank()) runCatching {
                        startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + c.number)))
                    }
                }
            ))
        }
        findViewById<View>(R.id.cardRecent).visibility = if (calls.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvRecentEmpty).visibility = if (calls.isEmpty()) View.VISIBLE else View.GONE
    }

    // ===== Helpers =====

    private fun clock(ms: Long) = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(ms))

    private fun minuteText(min: Int): String =
        SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(0).let {
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, min / 60); set(java.util.Calendar.MINUTE, min % 60)
            }.time
        })

    private fun input(value: String, hint: String) = EditText(this).apply {
        setText(value)
        this.hint = hint
        textSize = 15f
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_muted))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun switchRow(emoji: String, title: String, subtitle: String, checked: Boolean, onClick: (() -> Unit)? = null, onToggle: (Boolean) -> Unit): View {
        val sw = SwitchMaterial(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onToggle(on); render() }
        }
        return row(emoji, title, subtitle, trailing = sw, onClick = onClick ?: { sw.toggle() })
    }

    private fun row(
        emoji: String, title: String, subtitle: String, highlight: Boolean = false,
        trailing: View? = null, onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = emoji
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundResource(if (highlight) R.drawable.bg_tile_violet else R.drawable.bg_tile_slate)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(8)
            }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(if (highlight) R.color.primary_light else R.color.text_primary))
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(3), 0, 0)
            })
        })
        trailing?.let { row.addView(it) }
        return row
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(70) }
    }

    private fun ripple(): Int {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
