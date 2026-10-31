package com.faiza.ai.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
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
import androidx.core.content.FileProvider
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.guardian.Intruder
import com.faiza.ai.ai.guardian.OwnerGuard
import com.faiza.ai.ai.guardian.OwnerLock
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Owner lock settings (secret code) and the intruder photos. Opening it needs the phone's own PIN / fingerprint. */
class OwnerLockActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private var unlocked = false

    private val confirm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            unlocked = true
            render()
        } else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_lock)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnDeleteAll).setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Delete all intruder photos?")
                .setPositiveButton("Delete") { _, _ -> OwnerGuard.intruders().forEach { OwnerGuard.deleteIntruder(it) }; render() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // A stranger holding the phone must not be able to switch this off or read the code.
        val km = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val ask = if (km?.isDeviceSecure == true)
            km.createConfirmDeviceCredentialIntent("Owner lock", "Confirm it's you to see or change Faiza's owner lock")
        else null
        if (ask != null && savedInstanceState == null) confirm.launch(ask) else if (ask == null) askCodeThen()
    }

    /** Phone has no screen lock: ask for the current secret code instead (if one is set). */
    private fun askCodeThen() {
        val code = prefs.ownerLockCode
        if (!prefs.isOwnerLock || code.isNullOrBlank()) {
            unlocked = true
            render()
            return
        }
        val input = input("", "Your secret code").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Owner lock")
            .setMessage("Type your secret code.")
            .setView(padded(input))
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (OwnerLock.matches(input.text.toString(), code)) {
                    unlocked = true
                    render()
                } else {
                    Toast.makeText(this, "Wrong code", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun render() {
        if (!unlocked) return
        val list = findViewById<LinearLayout>(R.id.settingsList)
        list.removeAllViews()

        val code = prefs.ownerLockCode.orEmpty()
        list.addView(switchRow("🔐", "Owner lock", if (prefs.isOwnerLock) "On" else "Off", prefs.isOwnerLock) { on ->
            if (on && OwnerLock.skeleton(code).length < OwnerLock.MIN_CODE_SOUNDS) {
                Toast.makeText(this, "First set a secret code", Toast.LENGTH_SHORT).show()
                render()
                editCode(turnOn = true)
                return@switchRow
            }
            prefs.isOwnerLock = on
            if (on) {
                OwnerGuard.markVerified() // you, setting it up, are the owner
                askPermissions()
            }
            render()
        })
        list.addView(divider())
        list.addView(row("🗝️", "Secret code", if (code.isBlank()) "Not set" else "•".repeat(code.length.coerceAtMost(12)) + "  · tap to change") {
            editCode(turnOn = false)
        })
        list.addView(divider())
        val askIds = listOf(OwnerLock.ASK_UNLOCK, OwnerLock.ASK_DAILY, OwnerLock.ASK_ALWAYS)
        val askLabels = arrayOf("After each phone unlock", "Once a day", "Every conversation")
        val askIdx = askIds.indexOf(prefs.ownerLockAsk).coerceAtLeast(0)
        list.addView(row("⏱️", "When to ask for the code", askLabels[askIdx]) {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Ask for the code")
                .setSingleChoiceItems(askLabels, askIdx) { d, which ->
                    prefs.ownerLockAsk = askIds[which]
                    d.dismiss()
                    render()
                }
                .show()
        })
        list.addView(divider())
        val sos = FavoriteContacts.load(prefs).firstOrNull { it.sos }
        list.addView(switchRow(
            "💬", "Text a trusted person",
            if (sos == null) "Mark someone as SOS in My people · they get the time and location"
            else "Sends ${sos.name} the time and location when the phone locks",
            prefs.ownerLockSms
        ) { on ->
            prefs.ownerLockSms = on
            if (on) requestPermissions(missing(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)), 52)
            render()
        })
        list.addView(divider())
        list.addView(row(
            "🎙️", "Recognise my voice instead",
            "With Voice Guardian set to \"Only me\", she knows your voice and won't ask for the code"
        ) { startActivity(Intent(this, VoiceGuardianActivity::class.java)) })

        val needs = buildList {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) add("camera (for the photo)")
            if (FaizaAccessibilityService.instance == null) add("accessibility (to lock the phone)")
        }
        if (prefs.isOwnerLock && needs.isNotEmpty()) {
            list.addView(divider())
            list.addView(row("⚠️", "Needs ${needs.joinToString(" and ")}", "Tap to allow") { askPermissions() })
        }

        renderIntruders()
    }

    private fun askPermissions() {
        val m = missing(arrayOf(Manifest.permission.CAMERA))
        if (m.isNotEmpty()) requestPermissions(m, 51)
        else if (FaizaAccessibilityService.instance == null) {
            Toast.makeText(this, "Turn on Faiza in Accessibility so she can lock the phone", Toast.LENGTH_LONG).show()
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }

    private fun editCode(turnOn: Boolean) {
        val input = input(prefs.ownerLockCode.orEmpty(), "e.g. Mavia shashwata pochonder")
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Secret code")
            .setMessage("2–4 words only you know. Say it to Faiza in any language or spelling. Don't use your name or anything people hear you say every day.")
            .setView(padded(input))
            .setPositiveButton("Save") { _, _ ->
                val c = input.text.toString().trim()
                if (OwnerLock.skeleton(c).length < OwnerLock.MIN_CODE_SOUNDS) {
                    Toast.makeText(this, "Too short — use 2–3 words", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.ownerLockCode = c
                if (turnOn) {
                    prefs.isOwnerLock = true
                    askPermissions()
                }
                OwnerGuard.markVerified()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderIntruders() {
        val items = OwnerGuard.intruders()
        val list = findViewById<LinearLayout>(R.id.intruderList)
        list.removeAllViews()
        items.forEachIndexed { i, it ->
            if (i > 0) list.addView(divider())
            list.addView(intruderRow(it))
        }
        findViewById<View>(R.id.cardIntruders).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvIntrudersEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnDeleteAll).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun intruderRow(it: Intruder): View {
        val file = File(it.path)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(ripple())
            setOnClickListener { _ ->
                runCatching {
                    val uri = FileProvider.getUriForFile(this@OwnerLockActivity, "$packageName.files", file)
                    startActivity(Intent(this@OwnerLockActivity, ImageViewerActivity::class.java).setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
            setOnLongClickListener { _ ->
                AlertDialog.Builder(this@OwnerLockActivity, R.style.Theme_FaizaAI_Dialog)
                    .setTitle("Delete this photo?")
                    .setPositiveButton("Delete") { _, _ -> OwnerGuard.deleteIntruder(it); render() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
        row.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            runCatching {
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 8 }))
            }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
            addView(TextView(context).apply {
                text = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(it.at))
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = (if (it.strike >= OwnerLock.LOCK_AT_STRIKE) "Second try · phone locked" else "First try · warned") +
                        if (it.lat != null) " · 📍 map" else ""
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                if (it.lat != null) setOnClickListener { _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com/?q=${it.lat},${it.lon}")))
                    }
                }
            })
        })
        return row
    }

    // ===== Helpers =====

    private fun missing(perms: Array<String>) =
        perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()

    private fun input(value: String, hint: String) = EditText(this).apply {
        setText(value)
        this.hint = hint
        textSize = 15f
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_muted))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    private fun padded(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun switchRow(emoji: String, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit): View {
        val sw = SwitchMaterial(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, on -> onToggle(on) }
        }
        return row(emoji, title, subtitle, sw) { sw.toggle() }
    }

    private fun row(emoji: String, title: String, subtitle: String, trailing: View? = null, onClick: () -> Unit): View {
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
            setBackgroundResource(R.drawable.bg_tile_slate)
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
                setTextColor(getColor(R.color.text_primary))
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
