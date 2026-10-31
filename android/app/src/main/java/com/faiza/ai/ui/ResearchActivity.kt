package com.faiza.ai.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.provider.GeminiProvider
import com.faiza.ai.ai.provider.ProviderType
import com.faiza.ai.ai.web.ResearchAgent
import com.faiza.ai.ai.web.ResearchParsers
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** The research agent's own screen: ask, watch it work round by round, read the report with its sources. */
class ResearchActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var input: EditText
    private lateinit var startBtn: MaterialButton
    private lateinit var logView: TextView
    private lateinit var reportView: TextView
    private lateinit var modelLine: TextView
    private lateinit var depthRow: LinearLayout
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        scroll.addView(col)
        setContentView(scroll)

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
                text = "🔬 Deep research"
                textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary))
                setPadding(dp(8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "⚙️"
                textSize = 20f
                setPadding(dp(10), dp(6), dp(6), dp(6))
                setOnClickListener { settingsDialog() }
            })
        })
        col.addView(caption("Ask anything. She searches round after round, reads many pages, checks what's still missing and writes a full report with sources. You can leave this screen — she'll tell you when it's done."))

        input = EditText(this).apply {
            hint = "e.g. Today's full Bangladesh news summary · Best phone under 30,000 taka · What's happening in Saudi Arabia now"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        col.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) })

        depthRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        col.addView(depthRow)
        renderDepth()

        startBtn = MaterialButton(this).apply {
            setOnClickListener {
                if (ResearchAgent.running) { ResearchAgent.cancel(); return@setOnClickListener }
                val q = input.text.toString().trim()
                if (q.length < 3) { Toast.makeText(this@ResearchActivity, "Type what to research", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (!ResearchAgent.start(this@ResearchActivity, q, ResearchAgent.Depth.from(prefs.researchDepth), tellWhenDone = false)) {
                    Toast.makeText(this@ResearchActivity, "A research is already running", Toast.LENGTH_SHORT).show()
                }
            }
        }
        col.addView(startBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(10) })

        modelLine = caption("")
        col.addView(modelLine)

        logView = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.text_secondary))
            setLineSpacing(0f, 1.3f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_empty_card)
            visibility = View.GONE
        }
        col.addView(logView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) })

        reportView = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setLineSpacing(0f, 1.35f)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_input)
            visibility = View.GONE
        }
        col.addView(reportView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) })

        lifecycleScope.launch { ResearchAgent.log.collect { renderLog(it) } }
        lifecycleScope.launch { ResearchAgent.result.collect { renderResult(it) } }
        refreshModelLine()
    }

    private fun renderDepth() {
        depthRow.removeAllViews()
        ResearchAgent.Depth.values().forEach { d ->
            val on = prefs.researchDepth == d.name
            depthRow.addView(TextView(this).apply {
                text = d.label
                textSize = 13f
                setTextColor(getColor(if (on) R.color.text_primary else R.color.text_secondary))
                setBackgroundResource(if (on) R.drawable.bg_input_focused else R.drawable.bg_input)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { prefs.researchDepth = d.name; renderDepth() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
    }

    private fun renderLog(lines: List<String>) {
        logView.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        logView.text = lines.takeLast(12).joinToString("\n")
        startBtn.text = if (ResearchAgent.running) "Stop" else "Start research"
    }

    private fun renderResult(r: ResearchAgent.Result?) {
        startBtn.text = if (ResearchAgent.running) "Stop" else "Start research"
        if (r == null) { reportView.visibility = View.GONE; return }
        if (input.text.isBlank()) input.setText(r.question)
        reportView.visibility = View.VISIBLE
        // Light Markdown clean-up for reading on a phone; links stay tappable.
        reportView.text = r.report.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        Linkify.addLinks(reportView, Linkify.WEB_URLS)
        reportView.movementMethod = LinkMovementMethod.getInstance()
        modelLine.text = "Model ${r.model} · ${r.rounds} rounds · ${r.searches} searches · ${r.pagesRead} sources" +
                if (r.savedPath.isNotBlank()) " · saved to Downloads" else ""
    }

    private fun refreshModelLine() {
        val key = if (prefs.researchApiKey.isBlank()) "main Gemini key" else "its own key"
        val model = prefs.researchModel.ifBlank { prefs.researchModelAuto.substringAfter('|', "").ifBlank { "newest (auto)" } + " · auto" }
        modelLine.text = "Model: $model · $key"
    }

    /** Its own key and model. */
    private fun settingsDialog() {
        val keyBox = EditText(this).apply {
            setText(prefs.researchApiKey)
            hint = "Gemini API key (empty = use the main key)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(keyBox)
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("Research agent")
            .setMessage("Model now: ${prefs.researchModel.ifBlank { "automatic — the newest Gemini model on this key" }}")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                prefs.researchApiKey = keyBox.text.toString()
                prefs.researchModelAuto = "" // re-check which models this key can use
                refreshModelLine()
            }
            .setNeutralButton("Choose model") { _, _ ->
                prefs.researchApiKey = keyBox.text.toString()
                chooseModel()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseModel() {
        val key = prefs.researchApiKey.ifBlank { prefs.apiKeyFor(ProviderType.GEMINI).orEmpty() }
        if (key.isBlank()) { Toast.makeText(this, "Add a Gemini API key first", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Checking which models this key can use…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val models = runCatching { GeminiProvider(key, "").listModels() }.getOrElse {
                Toast.makeText(this@ResearchActivity, "Couldn't list models: ${it.message}", Toast.LENGTH_LONG).show()
                return@launch
            }.sortedDescending()
            val best = ResearchParsers.bestGeminiModel(models)
            val labels = (listOf("Automatic — newest (${best ?: "?"})") + models).toTypedArray()
            AlertDialog.Builder(this@ResearchActivity, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Research model")
                .setItems(labels) { _, which ->
                    prefs.researchModel = if (which == 0) "" else models[which - 1]
                    prefs.researchModelAuto = ""
                    refreshModelLine()
                }
                .show()
        }
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
