package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ui.orb.OrbDesign
import com.faiza.ai.ui.orb.OrbMode
import com.faiza.ai.ui.orb.OrbTheme
import com.faiza.ai.ui.orb.OrbView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

/** Orb design, colour, size and behaviour (video 25:56–29:11), with a live preview. */
class OrbSettingsActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private val isPro get() = FaizaApp.instance.licenseManager.isPro()
    private val handler = Handler(Looper.getMainLooper())

    private val states = listOf(
        OrbMode.LISTENING to "Listening",
        OrbMode.SPEAKING to "Speaking",
        OrbMode.WORKING to "Working",
        OrbMode.IDLE to "Sleeping"
    )
    private var stateIndex = 0
    private val cycle = object : Runnable {
        override fun run() {
            val (mode, label) = states[stateIndex]
            findViewById<OrbView>(R.id.orbPreview).mode = mode
            findViewById<TextView>(R.id.tvPreviewState).text = "Preview · $label"
            stateIndex = (stateIndex + 1) % states.size
            handler.postDelayed(this, 2_600)
        }
    }

    private val designChips = mutableListOf<Pair<OrbDesign, View>>()
    private val themeDots = mutableListOf<Pair<OrbTheme, View>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orb_settings)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<OrbView>(R.id.orbPreview).setIconRes(R.drawable.ic_wave)
        buildDesigns()
        buildThemes()
        setupSize()

        bind(R.id.switchOrbEnabled, { prefs.isOrbEnabled }) { prefs.isOrbEnabled = it }
        bind(R.id.switchOrbHide, { prefs.isOrbHiddenInApps }) { on ->
            prefs.isOrbHiddenInApps = on
            if (on && com.faiza.ai.service.FaizaAccessibilityService.instance == null) {
                android.widget.Toast.makeText(
                    this, "Turn on Accessibility for Faiza so she can tell which app is open", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
        bind(R.id.switchEdgeGlow, { prefs.isOrbEdgeGlow }) { prefs.isOrbEdgeGlow = it }
        bind(R.id.switchEdgeRgb, { prefs.isEdgeGlowRgb }) { prefs.isEdgeGlowRgb = it }
        bind(R.id.switchRealisticTyping, { prefs.isRealisticTyping }) { prefs.isRealisticTyping = it }
        setupTypingOptions()
        bind(R.id.switchScreenRecording, { prefs.isScreenRecordingMode }) { prefs.isScreenRecordingMode = it }
        bind(R.id.switchStartOnBoot, { prefs.isStartOnBoot }) { prefs.isStartOnBoot = it }

        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        stateIndex = 0
        handler.post(cycle)
    }

    override fun onPause() {
        handler.removeCallbacks(cycle)
        super.onPause()
    }

    private fun bind(id: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val s = findViewById<SwitchMaterial>(id)
        s.isChecked = get()
        s.setOnCheckedChangeListener { _, on -> set(on) }
    }

    // ===== Preview =====

    private fun refreshPreview() {
        val preview = findViewById<OrbView>(R.id.orbPreview)
        val design = OrbDesign.from(prefs.floatingOrbDesign)
        preview.design = if (design.isPro && !isPro) OrbDesign.SPHERE else design
        val theme = OrbTheme.from(prefs.orbTheme)
        preview.theme = theme
        val px = (150 * resources.displayMetrics.density * prefs.orbSizePercent / 100f).toInt()
        preview.layoutParams = (preview.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            width = px
            height = px
            gravity = Gravity.CENTER
        }
        designChips.forEach { (d, chip) ->
            chip.setBackgroundResource(if (d.id == prefs.floatingOrbDesign) R.drawable.bg_choice_selected else R.drawable.bg_choice)
            (chip.tag as? OrbView)?.theme = theme
        }
        themeDots.forEach { (t, dot) -> dot.background = dotBackground(t, t.id == prefs.orbTheme) }
    }

    // ===== Designs =====

    private fun buildDesigns() {
        val list = findViewById<LinearLayout>(R.id.designList)
        val theme = OrbTheme.from(prefs.orbTheme)
        OrbDesign.values().forEach { design ->
            val art = OrbView(this).apply {
                this.design = design
                this.theme = theme
                setIconRes(R.drawable.ic_wave)
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            }
            val locked = design.isPro && !isPro
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                tag = art
                addView(art)
                addView(TextView(context).apply {
                    text = design.title
                    textSize = 13f
                    setTextColor(getColor(R.color.text_primary))
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = if (locked) "PRO" else design.subtitle
                    textSize = 11f
                    setTextColor(getColor(if (locked) R.color.warning else R.color.text_muted))
                    gravity = Gravity.CENTER
                    maxLines = 1
                })
                layoutParams = LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
                isClickable = true
                setOnClickListener {
                    if (locked) {
                        AlertDialog.Builder(this@OrbSettingsActivity, R.style.Theme_FaizaAI_Dialog)
                            .setTitle("Pro design")
                            .setMessage("${design.title} is a Pro orb. The Sphere and Pulse reactor are free.")
                            .setPositiveButton("View plans") { _, _ -> startActivity(Intent(context, LicenseActivity::class.java)) }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        prefs.floatingOrbDesign = design.id
                        refreshPreview()
                    }
                }
            }
            designChips += design to chip
            list.addView(chip)
        }
    }

    // ===== Colours =====

    private fun dotBackground(theme: OrbTheme, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(theme.main)
        if (selected) setStroke(dp(3), Color.WHITE) else setStroke(dp(1), getColor(R.color.stroke))
    }

    private fun buildThemes() {
        val list = findViewById<LinearLayout>(R.id.themeList)
        OrbTheme.values().forEach { theme ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12); topMargin = dp(4) }
                contentDescription = theme.title
                isClickable = true
                setOnClickListener {
                    prefs.orbTheme = theme.id
                    refreshPreview()
                }
            }
            themeDots += theme to dot
            list.addView(dot)
        }
    }

    // ===== Size =====

    private fun setupSize() {
        val slider = findViewById<Slider>(R.id.sliderOrbSize)
        val label = findViewById<TextView>(R.id.tvOrbSize)
        slider.value = prefs.orbSizePercent.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        label.text = "${prefs.orbSizePercent}%"
        slider.addOnChangeListener { _, value, fromUser ->
            label.text = "${value.toInt()}%"
            if (fromUser) {
                prefs.orbSizePercent = value.toInt()
                refreshPreview()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ===== Realistic typing: speed and apps (MAYA) =====

    private fun setupTypingOptions() {
        val speeds = listOf("slow" to "Slow", "normal" to "Normal", "fast" to "Fast")
        val apps = listOf(
            "com.google.android.keep" to "Google Keep", "com.google.android.apps.docs.editors.docs" to "Google Docs",
            "com.samsung.android.app.notes" to "Samsung Notes", "com.whatsapp" to "WhatsApp",
            "com.facebook.orca" to "Messenger", "com.google.android.gm" to "Gmail",
            "com.google.android.apps.messaging" to "Messages", "com.openai.chatgpt" to "ChatGPT",
            "com.google.android.apps.bard" to "Gemini", "com.termux" to "Termux"
        )
        fun refresh() {
            findViewById<android.widget.TextView>(R.id.subTypingSpeed).text =
                speeds.firstOrNull { it.first == prefs.typingSpeed }?.second ?: "Normal"
            val chosen = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            findViewById<android.widget.TextView>(R.id.subTypingApps).text = when {
                chosen.isEmpty() -> "Every app"
                else -> chosen.joinToString { p -> apps.firstOrNull { it.first == p }?.second ?: p.substringAfterLast('.') }
            }
        }
        findViewById<android.view.View>(R.id.rowTypingSpeed).setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Typing speed")
                .setSingleChoiceItems(speeds.map { it.second }.toTypedArray(),
                    speeds.indexOfFirst { it.first == prefs.typingSpeed }.coerceAtLeast(1)) { d, which ->
                    prefs.typingSpeed = speeds[which].first
                    refresh()
                    d.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<android.view.View>(R.id.rowTypingApps).setOnClickListener {
            val chosen = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            val checked = apps.map { it.first in chosen }.toBooleanArray()
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Type like a person in…")
                .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, on ->
                    if (on) chosen += apps[which].first else chosen -= apps[which].first
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.typingApps = chosen.joinToString(",")
                    refresh()
                }
                .setNeutralButton("Every app") { _, _ ->
                    prefs.typingApps = ""
                    refresh()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        refresh()
    }
}
