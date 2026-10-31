package com.faiza.ai.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.service.FloatingOrbService
import com.google.android.material.button.MaterialButton

/** "Faiza rules" (MAYA "Maya Rules"): the user's own standing instructions, followed in every conversation. */
class RulesActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        root.addView(col)
        setContentView(root)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back)
                background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "Faiza rules"
                textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(8), 0, 0, 0)
            })
        })
        col.addView(caption("Write how she should always behave — one rule per line. She follows them in every conversation, voice and chat."))

        val box = EditText(this).apply {
            setText(prefs.userRules)
            hint = "e.g.\nAlways answer in Bangla\nKeep answers short\nCall me before 10 pm only if it's urgent"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 8
            gravity = Gravity.TOP or Gravity.START
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(14) })

        col.addView(caption("Ideas — tap to add:"))
        listOf(
            "Always answer in Bangla",
            "Keep answers short — two sentences",
            "Don't use emojis",
            "Explain things simply, like to a beginner",
            "Ask before opening any app",
            "Call me by my name, not Boss"
        ).forEach { idea ->
            col.addView(TextView(this).apply {
                text = "+  $idea"
                textSize = 14f
                setTextColor(getColor(R.color.primary_light))
                setPadding(dp(6), dp(10), dp(6), dp(4))
                setOnClickListener {
                    val now = box.text.toString().trimEnd()
                    if (!now.lines().any { it.trim().equals(idea, ignoreCase = true) }) {
                        box.setText(if (now.isEmpty()) idea else "$now\n$idea")
                        box.setSelection(box.text.length)
                    }
                }
            })
        }

        col.addView(MaterialButton(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.userRules = box.text.toString().trim()
                // A live conversation picks the rules up when it reconnects with the new prompt.
                FloatingOrbService.instance?.onAudioSettingsChanged()
                val n = prefs.userRules.lines().count { it.isNotBlank() }
                Toast.makeText(this@RulesActivity, if (n == 0) "No rules" else "Saved $n rule(s)", Toast.LENGTH_SHORT).show()
                finish()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(18) }
        })
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.25f)
        setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
