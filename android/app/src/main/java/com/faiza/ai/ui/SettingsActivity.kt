package com.faiza.ai.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.faiza.ai.service.FloatingOrbService
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.guardian.VoicePrint
import com.faiza.ai.ai.memory.BackupManager
import com.faiza.ai.ai.wake.DownloadableModel
import com.faiza.ai.ai.wake.WakeModel
import com.faiza.ai.license.LicenseManager
import com.faiza.ai.util.PermissionHelper
import com.faiza.ai.util.PrefsManager
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var licenseManager: LicenseManager
    private lateinit var profileSections: ProfileSections

    /** null = not decided yet (open the list automatically if something required is missing). */
    private var permissionsExpanded: Boolean? = null

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeBackup(it) }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PrefsManager(this)
        permissionHelper = PermissionHelper(this)
        licenseManager = LicenseManager(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        setupPermissions()
        profileSections = ProfileSections(
            activity = this,
            onSaveBackup = {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
                createBackup.launch("faiza-backup-$date.json")
            },
            onRestoreBackup = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        )
        profileSections.build()
        setupVoiceAndLanguage()
        setupListening()
        setupAnnouncements()
        setupAiEngine()
        setupLicense()
        findViewById<View>(R.id.rowHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        findViewById<View>(R.id.rowDriving).setOnClickListener {
            startActivity(Intent(this, DrivingActivity::class.java))
        }
        findViewById<View>(R.id.rowKhata).setOnClickListener {
            startActivity(Intent(this, KhataActivity::class.java))
        }
        findViewById<View>(R.id.rowPrayer).setOnClickListener {
            startActivity(Intent(this, PrayerActivity::class.java))
        }
        findViewById<View>(R.id.rowGaming).setOnClickListener {
            startActivity(Intent(this, GamingActivity::class.java))
        }
        findViewById<View>(R.id.rowCodeStudio).setOnClickListener {
            startActivity(Intent(this, CodeStudioActivity::class.java))
        }
        findViewById<View>(R.id.rowResearch).setOnClickListener {
            startActivity(Intent(this, ResearchActivity::class.java))
        }
        findViewById<View>(R.id.rowAvatar).setOnClickListener {
            startActivity(Intent(this, AvatarSetupActivity::class.java))
        }
        findViewById<View>(R.id.rowOrb).setOnClickListener {
            startActivity(Intent(this, OrbSettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.tvVersion).text = "${FaizaApp.APP_NAME} · Version ${FaizaApp.VERSION}"
        // MAYA-style hub: the main page lists the sections; each opens on its own page.
        SettingsHub.arrange(this, intent.getStringExtra(SettingsHub.EXTRA_SECTION))
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshVoiceLabels()
        profileSections.refresh()
        refreshVoiceGuardianRow()
        refreshAiEngine()
        refreshDefaultAssistant()
        refreshBackupCounts()
    }

    // ========== ASSISTANT: VOICE & LANGUAGE ==========

    private fun setupVoiceAndLanguage() {
        findViewById<View>(R.id.rowVoice).setOnClickListener {
            val persona = Persona.from(prefs.selectedPersona)
            val voices = persona.liveVoiceChoices
            var picked = voices.indexOf(prefs.liveVoiceFor(persona.id, persona.liveVoice)).coerceAtLeast(0)
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Voice for ${persona.displayName}")
                .setMessage("Tap a voice to hear it.")
                .setSingleChoiceItems(voices.map { com.faiza.ai.ai.voiceLabel(it) }.toTypedArray(), picked) { _, which ->
                    picked = which
                    preview(voices[which], com.faiza.ai.ai.SpeakingStyle.from(prefs.speakingStyle))
                }
                .setPositiveButton("Use") { _, _ ->
                    prefs.setLiveVoice(persona.id, voices[picked])
                    refreshVoiceLabels()
                }
                .setNegativeButton("Cancel", null)
                .setOnDismissListener { com.faiza.ai.ai.VoicePreview.stop() }
                .show()
        }
        findViewById<View>(R.id.rowSpeakingStyle).setOnClickListener { showStyleDialog() }
        findViewById<View>(R.id.rowLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<View>(R.id.rowDefaultAssistant).setOnClickListener { showDefaultAssistantHelp() }
        refreshVoiceLabels()
    }

    private fun refreshDefaultAssistant() {
        val on = com.faiza.ai.assist.AssistLauncher.isDefaultAssistant(this)
        findViewById<TextView>(R.id.subDefaultAssistant).apply {
            text = if (on) "On · hold power or home to call her" else "Off · tap to set up"
            setTextColor(getColor(if (on) R.color.success else R.color.text_secondary))
        }
    }

    private fun showDefaultAssistantHelp() {
        val on = com.faiza.ai.assist.AssistLauncher.isDefaultAssistant(this)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(if (on) "Faiza is your assistant" else "Make Faiza your assistant")
            .setMessage(
                (if (on) "Hold the power or home button to call her.\n\n" else "") +
                        "1 · In the next screen, tap \"Digital assistant app\" (or \"Default assistant app\").\n" +
                        "2 · Choose Faiza AI.\n" +
                        "3 · On some phones, also set Settings → System → Gestures → \"Press and hold power button\" " +
                        "to Digital assistant."
            )
            .setPositiveButton("Open settings") { _, _ -> com.faiza.ai.assist.AssistLauncher.openAssistantSettings(this) }
            .setNegativeButton("Close", null)
            .show()
    }

    // ===== Speaking style (STONIC video) =====

    private fun preview(voice: String, style: com.faiza.ai.ai.SpeakingStyle) {
        val persona = Persona.from(prefs.selectedPersona)
        val line = com.faiza.ai.ai.SpeakingStyle.previewLine(style, AppLanguage.from(prefs.language), prefs.assistantName(persona))
        Toast.makeText(this, "Preparing a sample…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                com.faiza.ai.ai.VoicePreview.play(voice, style.previewDirection, line)
            } catch (e: com.faiza.ai.ai.VoicePreview.PreviewException) {
                Toast.makeText(this@SettingsActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Couldn't play the sample — check the internet", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showStyleDialog() {
        val persona = Persona.from(prefs.selectedPersona)
        val styles = com.faiza.ai.ai.SpeakingStyle.values().toList()
        var picked = com.faiza.ai.ai.SpeakingStyle.from(prefs.speakingStyle)
        val currentVoice = prefs.liveVoiceFor(persona.id, persona.liveVoice)
        val radios = mutableListOf<RadioButton>()

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), 0)
        }
        styles.forEach { style ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            val radio = RadioButton(this).apply {
                isChecked = style == picked
                buttonTintList = getColorStateList(R.color.primary_light)
            }
            radios += radio
            row.addView(radio)
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4); marginEnd = dp(8)
                }
                addView(TextView(context).apply {
                    text = style.title
                    textSize = 15f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(getColor(R.color.text_primary))
                })
                addView(TextView(context).apply {
                    val voice = style.voiceFor(persona)
                    text = style.subtitle + (voice?.let { " · voice $it" } ?: "")
                    textSize = 12f
                    setTextColor(getColor(R.color.text_secondary))
                })
            })
            row.addView(ImageButton(this).apply {
                setImageResource(R.drawable.ic_play)
                imageTintList = getColorStateList(R.color.tile_violet)
                setBackgroundResource(R.drawable.bg_tile_violet)
                setPadding(dp(9), dp(9), dp(9), dp(9))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Hear ${style.title}"
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                setOnClickListener { preview(style.voiceFor(persona) ?: currentVoice, style) }
            })
            val select = View.OnClickListener {
                picked = style
                radios.forEachIndexed { i, r -> r.isChecked = styles[i] == style }
            }
            row.setOnClickListener(select)
            radio.setOnClickListener(select)
            list.addView(row)
        }

        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Speaking style")
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton("Use") { _, _ ->
                prefs.speakingStyle = picked.id
                picked.voiceFor(persona)?.let { voice ->
                    if (voice != currentVoice) {
                        prefs.setLiveVoice(persona.id, voice)
                        Toast.makeText(this, "Voice set to $voice — change it anytime under Voice", Toast.LENGTH_LONG).show()
                    }
                }
                refreshVoiceLabels()
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { com.faiza.ai.ai.VoicePreview.stop() }
            .show()
    }

    private fun refreshVoiceLabels() {
        val persona = Persona.from(prefs.selectedPersona)
        findViewById<TextView>(R.id.subSpeakingStyle).text =
            com.faiza.ai.ai.SpeakingStyle.from(prefs.speakingStyle).let {
                if (it == com.faiza.ai.ai.SpeakingStyle.NORMAL) "Natural · tap to hear the styles" else it.title
            }
        findViewById<TextView>(R.id.subVoice).text =
            "${com.faiza.ai.ai.voiceLabel(prefs.liveVoiceFor(persona.id, persona.liveVoice))} · for ${persona.displayName}"
        findViewById<TextView>(R.id.subLanguage).text = AppLanguage.from(prefs.language).displayName
        findViewById<TextView>(R.id.subLiveModel).text = prefs.liveModel
    }

    private fun showLanguageDialog() {
        val all = AppLanguage.values()
        val languages = all.map { it.displayName }.toTypedArray()
        val values = all.map { it.code }.toTypedArray()
        val current = values.indexOf(prefs.language).coerceAtLeast(0)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Language")
            .setSingleChoiceItems(languages, current) { dialog, which ->
                prefs.language = values[which]
                refreshVoiceLabels()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== LISTENING ==========

    private fun setupListening() {
        val isPro = licenseManager.isPro()

        // Live conversation
        val swLive = findViewById<SwitchMaterial>(R.id.swLiveVoice)
        swLive.isChecked = prefs.isLiveVoiceEnabled
        swLive.setOnCheckedChangeListener { _, on ->
            prefs.isLiveVoiceEnabled = on
            if (on && prefs.geminiApiKey.isNullOrBlank()) {
                Toast.makeText(this, "Live conversation needs a Gemini API key — add it under AI engine", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<View>(R.id.rowLiveVoice).setOnClickListener { swLive.toggle() }

        // Wake word
        val swWakeScreen = findViewById<SwitchMaterial>(R.id.swWakeScreenOnly)
        swWakeScreen.isChecked = prefs.isWakeScreenOnly
        swWakeScreen.setOnCheckedChangeListener { _, on -> prefs.isWakeScreenOnly = on }
        findViewById<View>(R.id.rowWakeScreenOnly).setOnClickListener { swWakeScreen.toggle() }
        val swWakeBattery = findViewById<SwitchMaterial>(R.id.swWakeSmartBattery)
        swWakeBattery.isChecked = prefs.isWakeSmartBattery
        swWakeBattery.setOnCheckedChangeListener { _, on -> prefs.isWakeSmartBattery = on }
        findViewById<View>(R.id.rowWakeSmartBattery).setOnClickListener { swWakeBattery.toggle() }
        val swWake = findViewById<SwitchMaterial>(R.id.swWakeWord)
        swWake.isChecked = prefs.isWakeWordEnabled
        swWake.setOnCheckedChangeListener { _, isChecked ->
            prefs.isWakeWordEnabled = isChecked
            if (isChecked) WakeModel.ensureDownloaded(this)
        }
        findViewById<View>(R.id.rowWakeWord).setOnClickListener {
            val s = WakeModel.state.value
            if (prefs.isWakeWordEnabled && (s is DownloadableModel.State.Failed || s is DownloadableModel.State.Missing)) {
                WakeModel.ensureDownloaded(this)
            } else swWake.toggle()
        }
        WakeModel.refresh(this)
        if (prefs.isWakeWordEnabled) WakeModel.ensureDownloaded(this)
        lifecycleScope.launch {
            WakeModel.state.collect { state ->
                val status = findViewById<TextView>(R.id.subWakeWord)
                val name = prefs.assistantName()
                when (state) {
                    is DownloadableModel.State.Ready -> {
                        status.text = "Say \"Hey $name\" · works offline"
                        status.setTextColor(getColor(R.color.text_secondary))
                    }
                    is DownloadableModel.State.Downloading -> {
                        status.text = "Downloading voice model… ${state.percent}%"
                        status.setTextColor(getColor(R.color.primary_light))
                    }
                    is DownloadableModel.State.Failed -> {
                        status.text = "Download failed · tap to try again"
                        status.setTextColor(getColor(R.color.danger))
                    }
                    DownloadableModel.State.Missing -> {
                        status.text = "Say \"Hey $name\" · 36 MB one-time download"
                        status.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            }
        }

        // Voice Guardian
        val swGuardian = findViewById<SwitchMaterial>(R.id.swVoiceGuardian)
        swGuardian.isChecked = prefs.isVoiceGuardianEnabled
        swGuardian.setOnCheckedChangeListener { _, isChecked ->
            if (!isPro) {
                swGuardian.isChecked = false
                showProRequired("Voice Guardian")
                return@setOnCheckedChangeListener
            }
            prefs.isVoiceGuardianEnabled = isChecked
            refreshVoiceGuardianRow()
            // Turning it on without a recorded voice: go straight to setup.
            if (isChecked && VoicePrint.load(prefs).none { it.isOwner }) {
                startActivity(Intent(this, VoiceGuardianActivity::class.java))
            }
        }
        findViewById<View>(R.id.rowVoiceGuardian).setOnClickListener {
            if (!isPro) showProRequired("Voice Guardian")
            else startActivity(Intent(this, VoiceGuardianActivity::class.java))
        }

        // Echo Guard
        val swEcho = findViewById<SwitchMaterial>(R.id.swEchoGuard)
        swEcho.isChecked = !prefs.isBargeIn
        swEcho.setOnCheckedChangeListener { _, isChecked ->
            if (!isPro) {
                swEcho.isChecked = false
                showProRequired("Echo Guard")
                return@setOnCheckedChangeListener
            }
            prefs.isEchoGuardEnabled = isChecked
            prefs.isBargeIn = !isChecked
            findViewById<SwitchMaterial>(R.id.swBargeIn).isChecked = !isChecked
        }
        findViewById<View>(R.id.rowEchoGuard).setOnClickListener { swEcho.toggle() }

        // "Stop when I talk" — one clear switch for interrupting her (Echo Guard is its opposite).
        val swBarge = findViewById<SwitchMaterial>(R.id.swBargeIn)
        swBarge.isChecked = prefs.isBargeIn
        swBarge.setOnCheckedChangeListener { _, on ->
            prefs.isBargeIn = on
            prefs.isEchoGuardEnabled = !on
            findViewById<SwitchMaterial>(R.id.swEchoGuard).isChecked = !on
            findViewById<TextView>(R.id.subBargeIn).text =
                if (on) "On: talk over her and she stops to listen" else "Off: she always finishes her sentence"
            FloatingOrbService.instance?.onAudioSettingsChanged()
        }
        findViewById<TextView>(R.id.subBargeIn).text =
            if (prefs.isBargeIn) "On: talk over her and she stops to listen" else "Off: she always finishes her sentence"
        findViewById<View>(R.id.rowBargeIn).setOnClickListener { swBarge.toggle() }

        val swLoud = findViewById<SwitchMaterial>(R.id.swLouderVoice)
        swLoud.isChecked = prefs.isLouderVoice
        swLoud.setOnCheckedChangeListener { _, on -> prefs.isLouderVoice = on }
        findViewById<View>(R.id.rowLouderVoice).setOnClickListener { swLoud.toggle() }

        // Call loudspeaker (her volume separate from videos) and soft-voice boost.
        val swCall = findViewById<SwitchMaterial>(R.id.swCallSpeaker)
        swCall.isChecked = prefs.isCallSpeaker
        swCall.setOnCheckedChangeListener { _, on ->
            prefs.isCallSpeaker = on
            FloatingOrbService.instance?.onAudioSettingsChanged()
        }
        findViewById<View>(R.id.rowCallSpeaker).setOnClickListener { swCall.toggle() }
        val swLoudspeaker = findViewById<SwitchMaterial>(R.id.swLoudspeaker)
        swLoudspeaker.isChecked = prefs.isLoudspeaker
        swLoudspeaker.setOnCheckedChangeListener { _, on -> prefs.isLoudspeaker = on }
        findViewById<View>(R.id.rowLoudspeaker).setOnClickListener { swLoudspeaker.toggle() }
        val softLabels = arrayOf("Off — normal hearing", "Soft voices (recommended)", "Whispers (quiet rooms only)")
        val subSoft = findViewById<TextView>(R.id.subSoftVoice)
        subSoft.text = softLabels[prefs.micBoostLevel]
        findViewById<View>(R.id.rowSoftVoice).setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Hear soft voices")
                .setSingleChoiceItems(softLabels, prefs.micBoostLevel) { d, which ->
                    prefs.micBoostLevel = which
                    subSoft.text = softLabels[which]
                    FloatingOrbService.instance?.onAudioSettingsChanged()
                    d.dismiss()
                }
                .show()
        }
        findViewById<View>(R.id.rowOwnerLock).setOnClickListener { startActivity(Intent(this, OwnerLockActivity::class.java)) }
        findViewById<View>(R.id.rowTouchGuard).setOnClickListener { startActivity(Intent(this, TouchGuardActivity::class.java)) }
        findViewById<View>(R.id.rowEventTriggers).setOnClickListener { startActivity(Intent(this, EventTriggersActivity::class.java)) }
        val swDelete = findViewById<SwitchMaterial>(R.id.swDeleteGuard)
        swDelete.isChecked = prefs.isDeleteGuard
        swDelete.setOnCheckedChangeListener { _, on -> prefs.isDeleteGuard = on }
        findViewById<View>(R.id.rowDeleteGuard).setOnClickListener { swDelete.toggle() }
        val swConfirm = findViewById<SwitchMaterial>(R.id.swConfirmSend)
        swConfirm.isChecked = prefs.isConfirmBeforeSend
        swConfirm.setOnCheckedChangeListener { _, on -> prefs.isConfirmBeforeSend = on; FloatingOrbService.instance?.onAudioSettingsChanged() }
        findViewById<View>(R.id.rowConfirmSend).setOnClickListener { swConfirm.toggle() }

        findViewById<View>(R.id.badgeVoiceGuardian).visibility = if (isPro) View.GONE else View.VISIBLE
        findViewById<View>(R.id.badgeEchoGuard).visibility = if (isPro) View.GONE else View.VISIBLE
    }

    private fun refreshVoiceGuardianRow() {
        val sw = findViewById<SwitchMaterial>(R.id.swVoiceGuardian)
        if (sw.isChecked != prefs.isVoiceGuardianEnabled) sw.isChecked = prefs.isVoiceGuardianEnabled
        val profiles = VoicePrint.load(prefs)
        val voices = if (profiles.size == 1) "1 voice" else "${profiles.size} voices"
        findViewById<TextView>(R.id.subVoiceGuardian).text = when {
            profiles.none { it.isOwner } -> "Obeys only your voice"
            !prefs.isVoiceGuardianEnabled -> "Off · $voices saved"
            else -> "On · $voices · ${prefs.voiceGuardianThreshold}% match"
        }
    }

    // ========== ANNOUNCEMENTS ==========

    private fun setupAnnouncements() {
        val swMessages = findViewById<SwitchMaterial>(R.id.swMessages)
        swMessages.isChecked = prefs.isProactiveMode
        swMessages.setOnCheckedChangeListener { _, isChecked ->
            prefs.isProactiveMode = isChecked
            if (isChecked) promptIfMissing("notifications")
        }
        findViewById<View>(R.id.rowMessages).setOnClickListener { swMessages.toggle() }

        val swCalls = findViewById<SwitchMaterial>(R.id.swCalls)
        swCalls.isChecked = prefs.isCallAnnouncementEnabled
        swCalls.setOnCheckedChangeListener { _, isChecked ->
            prefs.isCallAnnouncementEnabled = isChecked
            if (isChecked) promptIfMissing("notifications")
        }
        findViewById<View>(R.id.rowCalls).setOnClickListener { swCalls.toggle() }

        val swHeadset = findViewById<SwitchMaterial>(R.id.swHeadsetTrigger)
        swHeadset.isChecked = prefs.isHeadsetTrigger
        swHeadset.setOnCheckedChangeListener { _, isChecked -> prefs.isHeadsetTrigger = isChecked }
        findViewById<View>(R.id.rowHeadsetTrigger).setOnClickListener { swHeadset.toggle() }
        val swFast = findViewById<SwitchMaterial>(R.id.swFastTools)
        swFast.isChecked = prefs.isFastTools
        swFast.setOnCheckedChangeListener { _, on -> prefs.isFastTools = on }
        findViewById<View>(R.id.rowFastTools).setOnClickListener { swFast.toggle() }

        fun bindSwitch(rowId: Int, swId: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
            val sw = findViewById<SwitchMaterial>(swId)
            sw.isChecked = get()
            sw.setOnCheckedChangeListener { _, on -> set(on) }
            findViewById<View>(rowId).setOnClickListener { sw.toggle() }
        }
        bindSwitch(R.id.rowQuietMode, R.id.swQuietMode, { com.faiza.ai.util.QuietMode.manualActive() }) {
            com.faiza.ai.util.QuietMode.set(it)
        }
        bindSwitch(R.id.rowQuietFollowPhone, R.id.swQuietFollowPhone, { prefs.isQuietFollowPhone }) { prefs.isQuietFollowPhone = it }
        bindSwitch(R.id.rowQuietHeadphones, R.id.swQuietHeadphones, { prefs.isQuietHeadphonesOk }) { prefs.isQuietHeadphonesOk = it }
    }

    private fun promptIfMissing(permissionId: String) {
        val perm = permissionHelper.getAllPermissions().firstOrNull { it.id == permissionId } ?: return
        if (!perm.isGranted()) showPermissionDialog(perm)
    }

    // ========== AI ENGINE ==========

    private fun setupAiEngine() {
        findViewById<View>(R.id.rowApiKey).setOnClickListener {
            ProviderDialogs.showProviderSetup(this, prefs.activeProvider) { refreshAiEngine() }
        }
        findViewById<View>(R.id.rowProvider).setOnClickListener {
            ProviderDialogs.showProviderPicker(this) { refreshAiEngine() }
        }
        findViewById<View>(R.id.rowLiveModel).setOnClickListener { showLiveModelDialog() }
        val swAuto = findViewById<SwitchMaterial>(R.id.swAutoSwitch)
        swAuto.isChecked = prefs.isAutoSwitchModels
        swAuto.setOnCheckedChangeListener { _, on -> prefs.isAutoSwitchModels = on }
        findViewById<View>(R.id.rowAutoSwitch).setOnClickListener { swAuto.toggle() }
        findViewById<View>(R.id.rowShareBackup).setOnClickListener { shareBackup() }
        refreshAiEngine()
    }

    private fun refreshAiEngine() {
        val type = prefs.activeProvider
        val status = findViewById<TextView>(R.id.subApiKey)
        if (prefs.isAiConfigured()) {
            val key = prefs.apiKeyFor(type).orEmpty()
            status.text = if (key.isNotEmpty()) "Connected · ${key.take(6)}…" else "Connected"
            status.setTextColor(getColor(R.color.success))
        } else {
            status.text = "Not added yet · tap to add"
            status.setTextColor(getColor(R.color.warning))
        }
        findViewById<TextView>(R.id.subProvider).text =
            "${type.displayName} · ${prefs.modelFor(type).ifBlank { "no model" }}"
    }

    private fun showLiveModelDialog() {
        val input = EditText(this).apply {
            setText(prefs.liveModel)
            setSelection(text.length)
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            textSize = 14f
        }
        val form = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Live model")
            .setMessage("The model name from Google AI Studio, e.g. gemini-3.1-flash-live-preview.")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                prefs.liveModel = input.text.toString().trim()
                refreshVoiceLabels()
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.liveModel = PrefsManager.DEFAULT_LIVE_MODEL
                refreshVoiceLabels()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== LICENSE ==========

    private fun setupLicense() {
        val plan = prefs.licensePlan ?: "free"
        val key = prefs.licenseKey.orEmpty()
        val expiry = prefs.licenseExpiry.orEmpty()
        findViewById<TextView>(R.id.subLicense).text = when {
            key.isNotEmpty() -> {
                val masked = if (key.length > 14) "${key.take(14)}…" else key
                "${plan.replaceFirstChar { it.uppercase() }} · " +
                        (if (expiry.isNotEmpty()) "expires $expiry" else "never expires") + " · $masked"
            }
            FaizaApp.UNLOCK_ALL_FOR_TESTING -> "⭐ Premium · সব ফিচার খোলা (Family edition)"
            else -> "Free trial · tap to see plans"
        }
        findViewById<View>(R.id.rowLicense).setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }
        findViewById<TextView>(R.id.subUpdate).text =
            "Version ${com.faiza.ai.util.UpdateChecker.currentVersionName(this)} · tap to check"
        findViewById<View>(R.id.rowUpdate).setOnClickListener { checkForUpdate() }
        findViewById<View>(R.id.rowMoveLicense).setOnClickListener {
            if (key.isEmpty()) {
                Toast.makeText(this, "Activate a license first", Toast.LENGTH_SHORT).show()
            } else showChangeDeviceDialog()
        }
    }

    private fun checkForUpdate() {
        val sub = findViewById<TextView>(R.id.subUpdate)
        sub.text = "Checking…"
        lifecycleScope.launch {
            val info = com.faiza.ai.util.UpdateChecker.check(this@SettingsActivity, force = true)
            val current = com.faiza.ai.util.UpdateChecker.currentVersionName(this@SettingsActivity)
            when {
                info == null -> {
                    sub.text = "Version $current · couldn't reach the update server"
                }
                com.faiza.ai.util.UpdateChecker.isNewer(com.faiza.ai.util.UpdateChecker.currentVersionCode(this@SettingsActivity), info) -> {
                    sub.text = "Version ${info.versionName} is available"
                    UpdateDialog.show(this@SettingsActivity, info)
                }
                else -> sub.text = "Version $current · you're up to date"
            }
        }
    }

    private fun showChangeDeviceDialog() {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Move license?")
            .setMessage("This releases your license from this phone so you can activate it on another one.\n\nYou can do this once every 30 days.")
            .setPositiveButton("Move") { _, _ ->
                Toast.makeText(this, "Move requested. Activate your key on the new phone.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProRequired(feature: String) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Pro feature")
            .setMessage("$feature is part of the Pro and Lifetime plans.")
            .setPositiveButton("See plans") { _, _ ->
                startActivity(Intent(this, LicenseActivity::class.java))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    // ========== BACKUP ==========

    /** MAYA "Share backup": send the file straight to WhatsApp, Drive, email… */
    private fun shareBackup() {
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date())
                    val file = java.io.File(cacheDir, "faiza-backup-$date.json")
                    file.writeText(BackupManager.export(prefs))
                    androidx.core.content.FileProvider.getUriForFile(this@SettingsActivity, "$packageName.files", file)
                }.getOrNull()
            }
            if (uri == null) {
                Toast.makeText(this@SettingsActivity, "Couldn't prepare the backup", Toast.LENGTH_LONG).show()
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Share backup"))
        }
    }

    private fun refreshBackupCounts() {
        val c = BackupManager.currentCounts(prefs)
        findViewById<TextView>(R.id.subSaveBackup).text =
            "${c.facts} memories · ${c.lines} chats · ${c.contacts} contacts"
    }

    private fun writeBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.export(prefs)
                    (contentResolver.openOutputStream(uri, "wt") ?: contentResolver.openOutputStream(uri))!!
                        .bufferedWriter().use { it.write(json) }
                }.isSuccess
            }
            Toast.makeText(
                this@SettingsActivity,
                if (ok) "Backup saved" else "Couldn't save the backup there — try another folder",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun readBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 20 * 1024 * 1024) throw BackupManager.InvalidBackupException("This file is too big to be a backup.")
                        String(bytes, Charsets.UTF_8)
                    }
                    BackupManager.read(text)
                }
            }
            result.onFailure { e ->
                val msg = (e as? BackupManager.InvalidBackupException)?.message ?: "Couldn't open that file."
                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
            }
            result.onSuccess { backup ->
                val sum = BackupManager.summary(backup)
                val date = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH)
                    .format(java.util.Date(sum.createdAt))
                AlertDialog.Builder(this@SettingsActivity, R.style.Theme_FaizaAI_Dialog)
                    .setTitle("Restore this backup?")
                    .setMessage(
                        "Saved $date\n${sum.facts} memories · ${sum.lines} conversation lines · ${sum.contacts} contacts\n\n" +
                                "This replaces Faiza's current memories, profile and routines."
                    )
                    .setPositiveButton("Restore") { _, _ ->
                        BackupManager.restore(backup, prefs)
                        Toast.makeText(this@SettingsActivity, "Backup restored", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    // ========== PERMISSIONS ==========

    private val permissionIcons = mapOf(
        "overlay" to R.drawable.ic_orb,
        "microphone" to R.drawable.ic_mic,
        "phone" to R.drawable.ic_phone,
        "contacts" to R.drawable.ic_person,
        "sms" to R.drawable.ic_chat,
        "notifications" to R.drawable.ic_bell,
        "accessibility" to R.drawable.ic_accessibility,
        "battery" to R.drawable.ic_battery,
        "camera" to R.drawable.ic_camera,
        "post_notifications" to R.drawable.ic_bell,
        "location" to R.drawable.ic_globe,
        "call_log" to R.drawable.ic_phone
    )

    private fun setupPermissions() {
        val list = findViewById<LinearLayout>(R.id.permissionsList)
        list.removeAllViews()
        for (perm in permissionHelper.getAllPermissions()) {
            list.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.divider))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                    .apply { marginStart = dp(70) }
            })
            list.addView(createPermissionRow(perm))
        }
        findViewById<View>(R.id.rowPermissions).setOnClickListener {
            setPermissionsExpanded(list.visibility != View.VISIBLE)
        }
        refreshPermissionStatus()
    }

    private fun setPermissionsExpanded(expanded: Boolean) {
        permissionsExpanded = expanded
        findViewById<View>(R.id.permissionsList).visibility = if (expanded) View.VISIBLE else View.GONE
        findViewById<View>(R.id.ivPermissionsExpand).animate().rotation(if (expanded) 180f else 0f).setDuration(180).start()
    }

    private fun createPermissionRow(perm: PermissionHelper.PermissionItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundResource(selectableBackground())
            tag = perm.id
            setOnClickListener { if (!perm.isGranted()) showPermissionDialog(perm) }
        }
        row.addView(ImageView(this).apply {
            setImageResource(permissionIcons[perm.id] ?: R.drawable.ic_info)
            setBackgroundResource(R.drawable.bg_tile_slate)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            imageTintList = getColorStateList(R.color.tile_slate)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        row.addView(TextView(this).apply {
            text = perm.name
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
                marginEnd = dp(8)
            }
        })
        row.addView(TextView(this).apply {
            tag = "${perm.id}_status"
            textSize = 11f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setPadding(dp(10), dp(3), dp(10), dp(3))
        })
        return row
    }

    private fun selectableBackground(): Int {
        val out = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun refreshPermissionStatus() {
        val list = findViewById<LinearLayout>(R.id.permissionsList)
        val permissions = permissionHelper.getAllPermissions()
        var granted = 0
        var requiredMissing = 0
        for (perm in permissions) {
            val isGranted = perm.isGranted()
            if (isGranted) granted++ else if (perm.isRequired) requiredMissing++
            list.findViewWithTag<TextView>("${perm.id}_status")?.apply {
                when {
                    isGranted -> {
                        text = "Allowed"
                        setTextColor(getColor(R.color.success))
                        setBackgroundResource(R.drawable.bg_pill_success)
                    }
                    perm.isRequired -> {
                        text = "Required"
                        setTextColor(getColor(R.color.warning))
                        setBackgroundResource(R.drawable.bg_pill_warning)
                    }
                    else -> {
                        text = "Optional"
                        setTextColor(getColor(R.color.text_secondary))
                        setBackgroundResource(R.drawable.bg_pill)
                    }
                }
            }
        }
        val total = permissions.size.coerceAtLeast(1)
        val summary = findViewById<TextView>(R.id.subPermissions)
        summary.text = when {
            requiredMissing > 0 -> "$granted of ${permissions.size} allowed · $requiredMissing required"
            granted == permissions.size -> "All ${permissions.size} allowed"
            else -> "$granted of ${permissions.size} allowed · the rest are optional"
        }
        summary.setTextColor(getColor(if (requiredMissing > 0) R.color.warning else R.color.text_secondary))
        val progress = findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressPermissions)
        progress.progress = granted * 100 / total
        progress.setIndicatorColor(getColor(if (requiredMissing > 0) R.color.warning else R.color.success))
        // First time: open the list only when something important is missing.
        if (permissionsExpanded == null) setPermissionsExpanded(
            requiredMissing > 0 || intent.getStringExtra(SettingsHub.EXTRA_SECTION) == SettingsHub.PHONE_ACCESS
        )
    }

    private fun showPermissionDialog(perm: PermissionHelper.PermissionItem) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(perm.name)
            .setMessage(perm.description)
            .setPositiveButton("Allow") { _, _ -> perm.request(this) }
            .setNegativeButton("Later", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionHelper.REQUEST_CODE_OVERLAY ||
            requestCode == PermissionHelper.REQUEST_CODE_BATTERY) {
            refreshPermissionStatus()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQUEST_CODE_PERMISSIONS) refreshPermissionStatus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
