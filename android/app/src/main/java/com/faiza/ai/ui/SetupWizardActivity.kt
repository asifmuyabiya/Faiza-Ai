package com.faiza.ai.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.google.android.material.button.MaterialButton

/**
 * First start (owner: "নতুন ক্রেতাকে একটা একটা স্ক্রিনে বাংলায় বুঝিয়ে সব চালু করানো"): one permission per screen,
 * what it's for and exactly what to tap. Done ones are skipped; coming back from Settings moves on by itself.
 */
class SetupWizardActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var progress: ProgressBar
    private lateinit var stepText: TextView
    private lateinit var emoji: TextView
    private lateinit var title: TextView
    private lateinit var why: TextView
    private lateinit var how: TextView
    private lateinit var doIt: MaterialButton
    private lateinit var skip: TextView
    private var index = -1 // -1 = welcome
    private val steps get() = SetupChecks.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(getColor(R.color.bg)); setPadding(dp(24), dp(28), dp(24), dp(24))
        }
        setContentView(col)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = steps.size }
        stepText = text(13f, R.color.text_secondary)
        emoji = text(64f, R.color.text_primary).apply { setPadding(0, dp(36), 0, dp(8)) }
        title = text(24f, R.color.text_primary).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        why = text(16f, R.color.text_secondary).apply { setPadding(0, dp(10), 0, 0) }
        how = text(15f, R.color.text_primary).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.bg_input)
        }
        doIt = MaterialButton(this).apply { textSize = 17f; setOnClickListener { act() } }
        skip = text(14f, R.color.text_muted).apply { setPadding(0, dp(16), 0, 0); setOnClickListener { next() } }
        col.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)))
        col.addView(stepText); col.addView(emoji); col.addView(title); col.addView(why)
        col.addView(how, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(22) })
        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        col.addView(doIt, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))
        col.addView(skip)
        render()
    }

    override fun onResume() {
        super.onResume()
        // Back from Settings: if that step is done now, go on.
        if (index in steps.indices && steps[index].ok(this)) next() else render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (index in steps.indices && steps[index].ok(this)) next() else render()
    }

    private fun act() {
        when {
            index < 0 -> next()
            index >= steps.size -> finishWizard()
            else -> steps[index].fix(this)
        }
    }

    /** Next step that isn't done yet. */
    private fun next() {
        var i = index + 1
        while (i < steps.size && steps[i].ok(this)) i++
        index = i
        render()
    }

    private fun render() {
        val done = steps.count { it.ok(this) }
        progress.progress = done
        when {
            index < 0 -> {
                stepText.text = "স্বাগতম"
                emoji.text = "👋"
                title.text = "আমি ফাইজা"
                why.text = "আপনার ফোনের AI সহকারী। মুখে বললেই কল, মেসেজ, হিসাব, নামাজের সময়, গেমে গাইড — অনেক কিছু করে দেব।"
                how.text = "শুরু করতে কয়েকটা অনুমতি লাগবে। আমি একটা একটা করে দেখাব কোথায় চাপতে হবে — ১–২ মিনিট লাগবে।\n" +
                        "এখন হয়েছে: $done/${steps.size}"
                doIt.text = "শুরু করি"
                skip.text = "পরে করব"
                skip.setOnClickListener { finishWizard() }
            }
            index >= steps.size -> {
                stepText.text = "সব শেষ"
                emoji.text = "🎉"
                title.text = "সব রেডি!"
                why.text = "এখন বলুন \"Hey Faiza\" — বা বাবলে চাপ দিন।"
                how.text = "কোনো অনুমতি পরে বদলাতে চাইলে: Settings → How to use। হয়েছে $done/${steps.size}।"
                doIt.text = "শুরু করি"
                skip.text = ""
            }
            else -> {
                val s = steps[index]
                stepText.text = "ধাপ ${index + 1}/${steps.size}" + if (s.essential) " · জরুরি" else " · ঐচ্ছিক"
                emoji.text = s.emoji
                title.text = s.title
                why.text = s.why
                how.text = "👉 " + s.how
                doIt.text = "চালু করুন"
                skip.text = if (s.essential) "এখন না, পরে" else "লাগবে না, এড়িয়ে যাই"
                skip.setOnClickListener { next() }
            }
        }
    }

    private fun finishWizard() {
        prefs.setupWizardDone = true
        finish()
    }

    private fun text(size: Float, color: Int) = TextView(this).apply {
        textSize = size; gravity = Gravity.CENTER; setTextColor(getColor(color)); setLineSpacing(0f, 1.3f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
