package com.faiza.ai.ui

import android.graphics.Typeface
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
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.Closeness
import java.util.Calendar

/** MAYA "Secret settings": pick Friendly / Romantic / Intense (18+). Intense is gated by licence + age + confirmations. */
class SecretSettingsActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secret)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.levelList)
        list.removeAllViews()
        val current = prefs.closenessLevel
        Closeness.values().forEach { c ->
            if (c.level > 0) list.addView(gap())
            list.addView(card(c, c.level == current))
        }
    }

    private fun card(c: Closeness, selected: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(if (selected) R.drawable.bg_input_focused else R.drawable.bg_input)
            setOnClickListener { choose(c) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = c.title
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (selected) head.addView(TextView(this).apply {
            text = "✓"
            textSize = 18f
            setTextColor(getColor(R.color.primary_light))
        })
        box.addView(head)
        box.addView(TextView(this).apply {
            text = c.summary
            textSize = 14f
            setPadding(0, dp(6), 0, 0)
            setLineSpacing(0f, 1.25f)
            setTextColor(getColor(R.color.text_secondary))
        })
        return box
    }

    private fun choose(c: Closeness) {
        if (c == Closeness.INTENSE) {
            unlockIntense()
            return
        }
        prefs.closenessLevel = c.level
        Toast.makeText(this, "${c.title} — takes effect next time she starts", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun unlockIntense() {
        if (!FaizaApp.instance.licenseManager.isPro()) {
            Toast.makeText(this, "Intense (18+) needs a licence", Toast.LENGTH_LONG).show()
            return
        }
        val year = prefs.adultBirthYear
        if (year > 0 && Closeness.isAdult(year)) {
            confirmTwice()
            return
        }
        val input = EditText(this).apply {
            hint = "Birth year, e.g. 1998"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Confirm your age")
            .setMessage("Intense (18+) is for adults only. What year were you born?")
            .setView(LinearLayout(this).apply {
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            })
            .setPositiveButton("Next") { _, _ ->
                val y = input.text.toString().toIntOrNull() ?: 0
                if (Closeness.isAdult(y)) {
                    prefs.adultBirthYear = y
                    confirmTwice()
                } else {
                    Toast.makeText(this, "You must be 18 or older", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmTwice() {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Turn on Intense (18+)?")
            .setMessage("She will be more open and forward in words. You confirm you are 18+ and want this.")
            .setPositiveButton("Yes, I'm 18+") { _, _ ->
                AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                    .setTitle("One more time")
                    .setMessage("Really turn on Intense (18+)? Google's own filter still applies.")
                    .setPositiveButton("Turn it on") { _, _ ->
                        prefs.closenessLevel = Closeness.INTENSE.level
                        Toast.makeText(this, "Intense (18+) on — next time she starts", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun gap() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @Suppress("unused")
    private fun ripple(): Int {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }
}
