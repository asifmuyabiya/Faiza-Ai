package com.faiza.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.guardian.PhraseRecorder
import com.faiza.ai.ai.guardian.VoicePrint
import com.faiza.ai.ai.guardian.VoiceProfile
import com.faiza.ai.ai.wake.DownloadableModel
import com.faiza.ai.ai.wake.SpeakerModelFiles
import com.faiza.ai.ai.wake.WakeModel
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.util.PrefsManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Voice Guardian setup (video 19:08–24:07): record voices, test the match, choose who's allowed. */
class VoiceGuardianActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private var modelsReady = false
    private var pendingAction: (() -> Unit)? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAction?.invoke()
        else Toast.makeText(this, "Microphone permission is needed to record your voice", Toast.LENGTH_LONG).show()
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_guardian)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val enabled = findViewById<SwitchMaterial>(R.id.switchVgEnabled)
        enabled.isChecked = prefs.isVoiceGuardianEnabled
        enabled.setOnCheckedChangeListener { _, on ->
            prefs.isVoiceGuardianEnabled = on
            renderStatus()
            if (on && VoicePrint.load(prefs).none { it.isOwner }) {
                Toast.makeText(this, "Now record your voice so Faiza knows it's you", Toast.LENGTH_LONG).show()
            }
        }

        val away = findViewById<SwitchMaterial>(R.id.switchVgAway)
        away.isChecked = prefs.isVoiceGuardianAway
        away.setOnCheckedChangeListener { _, on -> prefs.isVoiceGuardianAway = on }

        findViewById<View>(R.id.rowVgMode).setOnClickListener { pickMode() }

        val slider = findViewById<Slider>(R.id.sliderVgThreshold)
        slider.value = prefs.voiceGuardianThreshold.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        slider.addOnChangeListener { _, value, fromUser ->
            findViewById<TextView>(R.id.tvVgThreshold).text = "${value.toInt()}%"
            if (fromUser) prefs.voiceGuardianThreshold = value.toInt()
        }
        findViewById<TextView>(R.id.tvVgThreshold).text = "${prefs.voiceGuardianThreshold}%"

        findViewById<View>(R.id.btnRecordOwner).setOnClickListener {
            withMic { enrol(VoiceProfile.ROLE_OWNER, ownerDefaultName()) }
        }
        findViewById<View>(R.id.btnAddFamily).setOnClickListener {
            withMic { askFamilyName() }
        }
        findViewById<View>(R.id.btnTestVoice).setOnClickListener { withMic { testVoice() } }

        // Both offline models are needed: speech (shared with "Hey Maya") + speaker voice prints.
        WakeModel.ensureDownloaded(this)
        SpeakerModelFiles.ensureDownloaded(this)
        lifecycleScope.launch {
            WakeModel.state.combine(SpeakerModelFiles.state) { a, b -> a to b }.collect { (a, b) ->
                modelsReady = a is DownloadableModel.State.Ready && b is DownloadableModel.State.Ready
                renderStatus(a, b)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Faiza's own listening would fight us for the microphone while recording.
        FloatingOrbService.instance?.pauseMic()
        renderMode()
        renderVoices()
        renderStatus()
    }

    override fun onPause() {
        super.onPause()
        FloatingOrbService.instance?.resumeMic()
    }

    // ===== Status & lists =====

    private fun renderStatus(
        speech: DownloadableModel.State = WakeModel.state.value,
        speaker: DownloadableModel.State = SpeakerModelFiles.state.value
    ) {
        val status = findViewById<TextView>(R.id.tvVgStatus)
        val hasOwner = VoicePrint.load(prefs).any { it.isOwner }
        val (text, ok) = when {
            speech is DownloadableModel.State.Failed || speaker is DownloadableModel.State.Failed ->
                "Voice model download failed — check internet and reopen this screen" to false
            speech is DownloadableModel.State.Downloading || speaker is DownloadableModel.State.Downloading -> {
                val p1 = (speech as? DownloadableModel.State.Downloading)?.percent ?: if (speech is DownloadableModel.State.Ready) 100 else 0
                val p2 = (speaker as? DownloadableModel.State.Downloading)?.percent ?: if (speaker is DownloadableModel.State.Ready) 100 else 0
                "Downloading voice models (one time, ~${if (speech is DownloadableModel.State.Ready) "13" else "49"} MB)… ${(p1 + p2) / 2}%" to false
            }
            !modelsReady -> "Getting voice models ready…" to false
            !hasOwner -> "Record your voice to start using Voice Guardian" to false
            !prefs.isVoiceGuardianEnabled -> "Voice Guardian is off" to false
            prefs.voiceGuardianMode == PrefsManager.VG_MODE_EVERYONE -> "On, but everyone is allowed" to false
            else -> "Protecting your phone · works in Live voice conversations" to true
        }
        status.text = text
        status.setTextColor(getColor(if (ok) R.color.success else R.color.warning))
        status.setBackgroundResource(if (ok) R.drawable.bg_pill_success else R.drawable.bg_pill_warning)

        val canRecord = modelsReady
        listOf(R.id.btnRecordOwner, R.id.btnAddFamily, R.id.btnTestVoice).forEach { findViewById<View>(it).isEnabled = canRecord }
        findViewById<MaterialButton>(R.id.btnRecordOwner).text = if (hasOwner) "Re-record my voice" else "Record my voice"
    }

    private fun renderMode() {
        findViewById<TextView>(R.id.tvVgMode).text = modeLabel(prefs.voiceGuardianMode)
    }

    private fun modeLabel(mode: String) = when (mode) {
        PrefsManager.VG_MODE_EVERYONE -> "Everyone"
        PrefsManager.VG_MODE_FAMILY -> "Me + family"
        else -> "Only me"
    }

    private fun pickMode() {
        val modes = arrayOf(PrefsManager.VG_MODE_OWNER, PrefsManager.VG_MODE_FAMILY, PrefsManager.VG_MODE_EVERYONE)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Who can talk to Faiza")
            .setSingleChoiceItems(modes.map { modeLabel(it) }.toTypedArray(), modes.indexOf(prefs.voiceGuardianMode).coerceAtLeast(0)) { d, which ->
                prefs.voiceGuardianMode = modes[which]
                renderMode()
                renderStatus()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderVoices() {
        val list = findViewById<LinearLayout>(R.id.voicesList)
        list.removeAllViews()
        val profiles = VoicePrint.load(prefs).sortedByDescending { it.isOwner }
        if (profiles.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No voices yet. Record your voice first."
                textSize = 13f
                setTextColor(getColor(R.color.text_muted))
                setPadding(dp(16), dp(18), dp(16), dp(18))
            })
            return
        }
        profiles.forEachIndexed { i, p ->
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.stroke))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(74) }
            })
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(8), dp(12))
                addView(ImageView(context).apply {
                    setImageResource(if (p.isOwner) R.drawable.ic_shield else R.drawable.ic_person)
                    setBackgroundResource(R.drawable.bg_icon_tile)
                    imageTintList = getColorStateList(R.color.primary_light)
                    setPadding(dp(11), dp(11), dp(11), dp(11))
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
                    addView(TextView(context).apply {
                        text = p.name
                        textSize = 15f
                        setTextColor(getColor(R.color.text_primary))
                    })
                    addView(TextView(context).apply {
                        text = if (p.isOwner) "Owner" else "Family"
                        textSize = 13f
                        setTextColor(getColor(R.color.text_secondary))
                    })
                })
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    imageTintList = getColorStateList(R.color.text_muted)
                    contentDescription = "Delete ${p.name}"
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                    setOnClickListener {
                        AlertDialog.Builder(this@VoiceGuardianActivity, R.style.Theme_FaizaAI_Dialog)
                            .setTitle("Delete ${p.name}'s voice?")
                            .setPositiveButton("Delete") { _, _ ->
                                VoicePrint.save(prefs, VoicePrint.load(prefs).filter { it.id != p.id })
                                renderVoices()
                                renderStatus()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
            })
        }
    }

    // ===== Enrolment =====

    private fun ownerDefaultName(): String =
        VoicePrint.load(prefs).firstOrNull { it.isOwner }?.name
            ?: prefs.userName?.takeIf { it.isNotBlank() } ?: "Owner"

    private fun askFamilyName() {
        val input = EditText(this).apply {
            hint = "Name, e.g. Ammu"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val form = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Add a family member")
            .setMessage("They should hold the phone and read 3 short lines.")
            .setView(form)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                else enrol(VoiceProfile.ROLE_FAMILY, name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun phrases(): List<String> {
        val name = prefs.assistantName()
        return listOf(
            "\"Hey $name, recognize my voice.\"\n(or: \"হে $name, আমার গলা চিনে রাখো।\")",
            "\"Tell me what the weather is like today.\"\n(or: \"আজকের আবহাওয়া কেমন বলো।\")",
            "\"1, 2, 3, 4, 5, 6, 7\"\n(count slowly)"
        )
    }

    /** Three-step recording → averaged voice print → saved profile. */
    private fun enrol(role: String, name: String) {
        val samples = mutableListOf<VoicePrint.Print>()
        RecordDialog(
            title = if (role == VoiceProfile.ROLE_OWNER) "Record your voice" else "Record $name's voice",
            steps = phrases(),
            onSample = { print -> samples += print },
            onDone = done@{
                val combined = VoicePrint.combine(samples)
                if (combined == null) {
                    Toast.makeText(this, "Couldn't make a voice print. Please try again.", Toast.LENGTH_LONG).show()
                    return@done
                }
                val profile = VoiceProfile(name = name, role = role, vector = combined.vector.toList())
                val others = VoicePrint.load(prefs).filter { !(role == VoiceProfile.ROLE_OWNER && it.isOwner) }
                VoicePrint.save(prefs, others + profile)
                if (role == VoiceProfile.ROLE_OWNER && !prefs.isVoiceGuardianEnabled) {
                    prefs.isVoiceGuardianEnabled = true
                    findViewById<SwitchMaterial>(R.id.switchVgEnabled).isChecked = true
                }
                renderVoices()
                renderStatus()
                Toast.makeText(this, "$name's voice saved. Now try \"Test my voice\".", Toast.LENGTH_LONG).show()
            }
        ).show()
    }

    private fun testVoice() {
        val profiles = VoicePrint.load(prefs)
        if (profiles.isEmpty()) {
            Toast.makeText(this, "Record a voice first", Toast.LENGTH_SHORT).show()
            return
        }
        RecordDialog(
            title = "Test my voice",
            steps = listOf("Say anything for 3–5 seconds, e.g.\n\"Hey ${prefs.assistantName()}, how are you today?\""),
            onSample = { print ->
                val match = VoicePrint.bestMatch(VoicePrint.normalize(print.vector), profiles)
                val result = findViewById<TextView>(R.id.tvTestResult)
                result.visibility = View.VISIBLE
                if (match == null) {
                    result.text = "Couldn't compare — try again"
                    result.setTextColor(getColor(R.color.warning))
                } else {
                    val limit = prefs.voiceGuardianThreshold
                    val pass = match.percent >= limit
                    result.text = "${match.profile.name}: ${match.percent}% match\n" +
                            if (pass) "Recognized ✓ (limit $limit%)" else "Below your $limit% limit — Faiza wouldn't obey this"
                    result.setTextColor(getColor(if (pass) R.color.success else R.color.danger))
                }
            },
            onDone = {}
        ).show()
    }

    private fun withMic(action: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else {
            pendingAction = action
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Dialog that walks through [steps], recording each and turning it into a voice print. */
    private inner class RecordDialog(
        private val title: String,
        private val steps: List<String>,
        private val onSample: (VoicePrint.Print) -> Unit,
        private val onDone: () -> Unit
    ) {
        private var step = 0
        private lateinit var dialog: AlertDialog
        private val stepLabel = TextView(this@VoiceGuardianActivity)
        private val phrase = TextView(this@VoiceGuardianActivity)
        private val status = TextView(this@VoiceGuardianActivity)
        private val button = MaterialButton(this@VoiceGuardianActivity)
        private val recorder = PhraseRecorder(onAutoStop = { finishRecording() })
        private var busy = false

        fun show() {
            stepLabel.apply { textSize = 13f; setTextColor(getColor(R.color.primary_light)) }
            phrase.apply {
                textSize = 18f
                setTextColor(getColor(R.color.text_primary))
                setPadding(0, dp(10), 0, dp(10))
            }
            status.apply { textSize = 13f; setTextColor(getColor(R.color.text_secondary)) }
            button.setOnClickListener { if (recorder.isRecording) finishRecording() else beginRecording() }

            val content = LinearLayout(this@VoiceGuardianActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(4))
                addView(stepLabel)
                addView(phrase)
                addView(status)
                addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            }
            dialog = AlertDialog.Builder(this@VoiceGuardianActivity, R.style.Theme_FaizaAI_Dialog)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setOnDismissListener { recorder.cancel() }
                .show()
            renderStep()
        }

        private fun renderStep() {
            stepLabel.text = if (steps.size > 1) "Step ${step + 1} of ${steps.size}" else "Speak naturally"
            phrase.text = steps[step]
            status.text = "Tap Record, read the line (2.5–5 seconds), then tap Stop."
            status.setTextColor(getColor(R.color.text_secondary))
            button.text = "Record"
            button.setIconResource(R.drawable.ic_mic)
            button.isEnabled = true
        }

        private fun beginRecording() {
            if (busy) return
            if (!recorder.start()) {
                status.text = "Couldn't open the microphone. Close other apps using it and try again."
                status.setTextColor(getColor(R.color.danger))
                return
            }
            status.text = "Listening… speak now"
            status.setTextColor(getColor(R.color.primary_light))
            button.text = "Stop"
            button.setIconResource(R.drawable.ic_stop)
        }

        private fun finishRecording() {
            if (!recorder.isRecording) return
            when (val r = recorder.stop()) {
                is PhraseRecorder.Result.TooShort -> retry("Too short (%.1f s). Speak for 2.5 to 5 seconds.".format(r.durationMs / 1000f))
                is PhraseRecorder.Result.TooQuiet -> retry("I couldn't hear you. Hold the phone closer and speak normally.")
                is PhraseRecorder.Result.Failed -> retry("Recording failed. Please try again.")
                is PhraseRecorder.Result.Ok -> analyse(r.pcm)
            }
        }

        private fun retry(message: String) {
            renderStep()
            status.text = message
            status.setTextColor(getColor(R.color.danger))
        }

        private fun analyse(pcm: ByteArray) {
            busy = true
            button.isEnabled = false
            button.text = "Analysing…"
            status.text = "Making your voice print…"
            lifecycleScope.launch {
                val print = withContext(Dispatchers.Default) { runCatching { VoicePrint.extract(this@VoiceGuardianActivity, pcm) }.getOrNull() }
                busy = false
                if (!dialog.isShowing) return@launch
                if (print == null || print.frames < VoicePrint.MIN_RELIABLE_FRAMES) {
                    retry("Not enough clear speech. Please say the whole line again.")
                    return@launch
                }
                onSample(print)
                step++
                if (step < steps.size) {
                    renderStep()
                    status.text = "Got it ✓  Next line:"
                    status.setTextColor(getColor(R.color.success))
                } else {
                    dialog.dismiss()
                    onDone()
                }
            }
        }
    }
}
