package com.faiza.ai.ui

import android.Manifest
import android.content.Intent
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
import com.faiza.ai.ai.sos.SosManager
import kotlinx.coroutines.launch

/** SOS mode (video 25:01): big button, contacts, extra message, test, permissions. */
class SosActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    private data class Need(val title: String, val why: String, val permissions: Array<String>, val required: Boolean)

    private val needs = listOf(
        Need("Send SMS", "Texts your location to your SOS contacts", arrayOf(Manifest.permission.SEND_SMS), true),
        Need("Phone calls", "Calls your SOS contacts one by one", arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE), true),
        Need("Location", "Puts a Google Maps link in the text", arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), true),
        Need("Call log", "Knows if a call was answered, so it can retry", arrayOf(Manifest.permission.READ_CALL_LOG), false)
    )

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        renderPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnSos).setOnClickListener {
            if (SosManager.isActive) {
                SosManager.cancel()
                Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show()
            } else if (!SosManager.start(this)) {
                Toast.makeText(this, "Add at least one SOS contact first", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, ContactsActivity::class.java))
            }
        }
        findViewById<View>(R.id.rowSosContacts).setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
        findViewById<View>(R.id.rowSosMessage).setOnClickListener { editMessage() }
        findViewById<View>(R.id.btnSosTest).setOnClickListener { testSos() }

        lifecycleScope.launch {
            SosManager.state.collect { renderState(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        renderContacts()
        renderPermissions()
        findViewById<TextView>(R.id.tvSosMessage).text =
            prefs.sosMessage.ifBlank { "Optional, e.g. blood group or medical info" }
    }

    private fun renderState(state: SosManager.State) {
        val button = findViewById<TextView>(R.id.tvSosButton)
        val status = findViewById<TextView>(R.id.tvSosStatus)
        when (state) {
            is SosManager.State.Countdown -> {
                button.text = state.secondsLeft.toString()
                status.text = "Sending in ${state.secondsLeft}… tap the button to cancel"
                status.setTextColor(getColor(R.color.danger))
            }
            is SosManager.State.Running -> {
                button.text = "STOP"
                status.text = state.step
                status.setTextColor(getColor(R.color.danger))
            }
            is SosManager.State.Finished -> {
                button.text = "SOS"
                status.text = state.summary
                status.setTextColor(getColor(R.color.success))
            }
            SosManager.State.Cancelled -> {
                button.text = "SOS"
                status.text = "SOS cancelled"
                status.setTextColor(getColor(R.color.text_secondary))
            }
            SosManager.State.Idle -> {
                button.text = "SOS"
                status.text = "Tap to send SOS"
                status.setTextColor(getColor(R.color.text_primary))
            }
        }
    }

    private fun renderContacts() {
        val list = SosManager.contacts()
        findViewById<TextView>(R.id.tvSosContacts).text =
            if (list.isEmpty()) "None yet — tap to add" else list.mapIndexed { i, c -> "${i + 1}. ${c.name}" }.joinToString("   ")
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun renderPermissions() {
        val container = findViewById<LinearLayout>(R.id.sosPermissions)
        container.removeAllViews()
        needs.forEachIndexed { i, need ->
            if (i > 0) container.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.stroke))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(16) }
            })
            val ok = need.permissions.any { granted(it) }
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = need.title + if (need.required) "" else " (optional)"
                        textSize = 15f
                        setTextColor(getColor(R.color.text_primary))
                    })
                    addView(TextView(context).apply {
                        text = need.why
                        textSize = 13f
                        setTextColor(getColor(R.color.text_secondary))
                    })
                })
                addView(TextView(context).apply {
                    textSize = 12f
                    setPadding(dp(10), dp(4), dp(10), dp(4))
                    if (ok) {
                        text = "Allowed"
                        setTextColor(getColor(R.color.success))
                        setBackgroundResource(R.drawable.bg_pill_success)
                    } else {
                        text = "Allow"
                        setTextColor(getColor(R.color.warning))
                        setBackgroundResource(R.drawable.bg_pill_warning)
                        setOnClickListener { requestPermissions.launch(need.permissions) }
                    }
                })
            })
        }
    }

    private fun editMessage() {
        val input = EditText(this).apply {
            setText(prefs.sosMessage)
            hint = "e.g. Blood group B+. I have asthma."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 15f
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
            .setTitle("Extra SOS message")
            .setMessage("Added to every SOS text (max 200 characters).")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                prefs.sosMessage = input.text.toString()
                findViewById<TextView>(R.id.tvSosMessage).text =
                    prefs.sosMessage.ifBlank { "Optional, e.g. blood group or medical info" }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Shows exactly what would be sent, with the real location, without texting or calling anyone. */
    private fun testSos() {
        val contacts = SosManager.contacts()
        val loading = AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Test SOS")
            .setMessage("Getting your location…")
            .setNegativeButton("Close", null)
            .show()
        lifecycleScope.launch {
            val text = SosManager.preview(this@SosActivity)
            if (!loading.isShowing) return@launch
            val missing = needs.filter { it.required && it.permissions.none { p -> granted(p) } }.map { it.title }
            loading.setMessage(buildString {
                append("Nothing was sent. A real SOS would text ")
                append(if (contacts.isEmpty()) "NOBODY (add SOS contacts!)" else contacts.joinToString { it.name })
                append(":\n\n").append(text)
                if (missing.isNotEmpty()) append("\n\n⚠ Still needed: ").append(missing.joinToString())
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
