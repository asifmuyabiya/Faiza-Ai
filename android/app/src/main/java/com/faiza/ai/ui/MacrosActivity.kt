package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.ai.macros.Macro
import com.faiza.ai.ai.macros.MacroCenter
import com.faiza.ai.ai.macros.MacroRunner
import com.faiza.ai.ai.macros.MacroStep
import com.faiza.ai.service.FaizaAccessibilityService
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

/** Saved macros (video 33:47–39:10): see the steps, run, rename, delete, choose which text to ask for. */
class MacrosActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private val expanded = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_macros)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        list = findViewById(R.id.macroList)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val macros = MacroCenter.all()
        list.removeAllViews()
        findViewById<View>(R.id.tvEmpty).visibility = if (macros.isEmpty()) View.VISIBLE else View.GONE
        macros.forEach { list.addView(macroCard(it)) }
    }

    // ===== Card =====

    private fun macroCard(m: Macro): View {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(12) }
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(body)

        // Header: icon, name, step count, run button.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(66)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            setBackgroundResource(ripple())
        }
        header.addView(tile(R.drawable.ic_bolt, R.drawable.bg_tile_orange, R.color.tile_orange))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(8)
            }
        }
        texts.addView(TextView(this).apply {
            text = m.name
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            maxLines = 1
        })
        texts.addView(TextView(this).apply {
            text = subtitle(m)
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(texts)
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_play)
            imageTintList = getColorStateList(R.color.tile_violet)
            setBackgroundResource(R.drawable.bg_tile_violet)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Run ${m.name}"
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { run(m) }
        })
        body.addView(header)

        // Details: the steps and actions.
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (m.id in expanded) View.VISIBLE else View.GONE
        }
        details.addView(divider())
        m.steps.forEachIndexed { i, step -> details.addView(stepRow(m, i, step)) }
        details.addView(actionsRow(m))
        body.addView(details)

        header.setOnClickListener {
            val open = details.visibility != View.VISIBLE
            details.visibility = if (open) View.VISIBLE else View.GONE
            if (open) expanded += m.id else expanded -= m.id
        }
        return card
    }

    private fun subtitle(m: Macro): String {
        val steps = if (m.steps.size == 1) "1 step" else "${m.steps.size} steps"
        val runs = when (m.runCount) {
            0 -> "never run"
            1 -> "ran once"
            else -> "ran ${m.runCount} times"
        }
        return "$steps · $runs" + if (m.builtIn) " · built-in" else ""
    }

    private fun stepText(step: MacroStep): String {
        if (step.action != "type_text" || step.askEachTime) return step.description
        val args = step.args()
        val text = args.get("text")?.asString.orEmpty()
        val submit = args.get("submit")?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
        return "Type “${text.take(40)}”" + if (submit) " and press Enter" else ""
    }

    private fun stepRow(m: Macro, index: Int, step: MacroStep): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        row.addView(TextView(this).apply {
            text = "${index + 1}"
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_pill)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        })
        row.addView(TextView(this).apply {
            text = stepText(step)
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(8)
            }
        })
        if (step.action == "type_text") {
            row.addView(TextView(this).apply {
                text = "Ask"
                textSize = 12f
                setTextColor(getColor(R.color.text_muted))
            })
            row.addView(SwitchMaterial(this).apply {
                isChecked = step.askEachTime
                contentDescription = "Ask what to type each time"
                setOnCheckedChangeListener { _, on ->
                    val updated = m.copy(steps = m.steps.toMutableList().also { it[index] = step.copy(askEachTime = on) })
                    MacroCenter.update(updated)
                    render()
                }
            })
        }
        return row
    }

    private fun actionsRow(m: Macro): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        fun button(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(color))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        row.addView(button("Rename", R.color.primary_light) { rename(m) })
        row.addView(button("Delete", R.color.danger) { confirmDelete(m) })
        return row
    }

    // ===== Actions =====

    private fun run(m: Macro) {
        val needsScreen = m.steps.any { it.action in setOf("tap", "long_press", "type_text", "scroll", "phone_control") }
        if (needsScreen && FaizaAccessibilityService.instance == null) {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Turn on Accessibility")
                .setMessage("Faiza needs Accessibility to tap and type for you. Find Faiza AI in the list and turn it on.")
                .setPositiveButton("Open settings") { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        val asks = m.inputSteps
        if (asks.isEmpty()) {
            start(m, emptyList())
            return
        }
        val inputs = asks.map { step ->
            EditText(this).apply {
                hint = step.prompt ?: "What should I type?"
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setHintTextColor(getColor(R.color.text_muted))
                setBackgroundResource(R.drawable.bg_input)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            inputs.forEachIndexed { i, input ->
                addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) topMargin = dp(10) })
            }
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Run “${m.name}”")
            .setView(form)
            .setPositiveButton("Run") { _, _ ->
                val values = inputs.map { it.text.toString().trim() }
                if (values.any { it.isEmpty() }) {
                    Toast.makeText(this, "Fill in what to type first", Toast.LENGTH_SHORT).show()
                } else start(m, values)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun start(m: Macro, inputs: List<String>) {
        val app = applicationContext
        Toast.makeText(this, "Running “${m.name}”…", Toast.LENGTH_SHORT).show()
        // Step aside so the macro works on the app the user was in (or the one it opens).
        moveTaskToBack(true)
        MacroCenter.appScope.launch {
            val result = MacroRunner.run(app, m, inputs)
            val message = when {
                result.startsWith("OK") -> "“${m.name}” finished"
                result.startsWith("STOPPED") -> result.substringBefore(". Steps").substringBefore(". Nothing")
                else -> result.removePrefix("ERROR:").trim()
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun rename(m: Macro) {
        val input = EditText(this).apply {
            setText(m.name)
            setSelection(text.length)
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isSingleLine = true
        }
        val form = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Rename macro")
            .setMessage("You'll run it by saying “Run <name>”.")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    MacroCenter.update(m.copy(name = name))
                    render()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Delete “${m.name}”?")
            .setMessage("Its ${m.steps.size} steps will be removed. This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                MacroCenter.delete(m.id)
                expanded -= m.id
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Helpers =====

    private fun tile(icon: Int, bg: Int, tint: Int) = ImageView(this).apply {
        setImageResource(icon)
        setBackgroundResource(bg)
        imageTintList = getColorStateList(tint)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(6)
        }
    }

    private fun ripple(): Int {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
