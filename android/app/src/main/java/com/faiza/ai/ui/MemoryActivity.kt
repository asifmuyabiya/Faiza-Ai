package com.faiza.ai.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.memory.MemoryFact
import com.faiza.ai.ai.memory.MemoryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows, searches and deletes what Faiza remembers (video 30:51 "Memory section"). */
class MemoryActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    private var filter = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.cardTrainedTasks).setOnClickListener {
            startActivity(android.content.Intent(this, MacrosActivity::class.java))
        }
        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString().orEmpty().trim()
                render()
            }
        })

        findViewById<View>(R.id.btnClearLog).setOnClickListener {
            confirm("Clear conversation history?", "Faiza keeps the facts she learned, but forgets past conversations.") {
                MemoryStore.clearLog()
                render()
            }
        }
        findViewById<View>(R.id.btnClearAll).setOnClickListener {
            confirm("Forget everything?", "All remembered facts and conversation history will be deleted. This can't be undone (unless you have a backup).") {
                MemoryStore.clearFacts()
                MemoryStore.clearLog()
                render()
                Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val macros = com.faiza.ai.ai.macros.MacroCenter.all()
        findViewById<TextView>(R.id.tvTrainedTasks).text =
            if (macros.isEmpty()) "Record by voice, replay with one sentence"
            else "${macros.size} macros · " + macros.take(3).joinToString { it.name } + if (macros.size > 3) "…" else ""
        render()
    }

    private fun render() {
        val status = findViewById<TextView>(R.id.tvMemoryStatus)
        status.text = when {
            !prefs.isMemoryEnabled -> "Memory is OFF — Faiza won't learn anything new. Turn it on in Settings → Assistant."
            prefs.isIncognito -> "Only on this phone. Incognito is ON: passwords, OTPs, PINs and card numbers are never saved."
            else -> "Only on this phone. Incognito is OFF."
        }

        val all = MemoryStore.facts().asReversed()
        val shown = if (filter.isEmpty()) all else all.filter { it.text.contains(filter, ignoreCase = true) }
        findViewById<TextView>(R.id.tvFactsHeader).text =
            if (filter.isEmpty()) "Things I remember (${all.size})" else "Matches (${shown.size})"

        val list = findViewById<LinearLayout>(R.id.factsList)
        list.removeAllViews()
        if (shown.isEmpty()) {
            list.addView(TextView(this).apply {
                text = if (all.isEmpty()) "Nothing yet. Tell Faiza about yourself — e.g. \"My sister's name is Rima\"."
                else "No memories match \"$filter\"."
                textSize = 13f
                setTextColor(getColor(R.color.text_muted))
                setPadding(dp(16), dp(18), dp(16), dp(18))
            })
        } else {
            shown.forEachIndexed { i, fact ->
                if (i > 0) list.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.stroke))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                        .apply { marginStart = dp(16) }
                })
                list.addView(factRow(fact))
            }
        }

        findViewById<TextView>(R.id.tvLogCount).text = "${MemoryStore.logSize()} lines saved (newest 800 kept)"
    }

    private fun factRow(fact: MemoryFact) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(8), dp(12))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = fact.text
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
            })
            addView(TextView(context).apply {
                text = dateFormat.format(Date(fact.createdAt))
                textSize = 12f
                setTextColor(getColor(R.color.text_muted))
                setPadding(0, dp(2), 0, 0)
            })
        })

        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_delete)
            imageTintList = getColorStateList(R.color.text_muted)
            contentDescription = "Delete"
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener {
                MemoryStore.delete(fact.id)
                render()
            }
        })
    }

    private fun confirm(title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ -> onYes() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
