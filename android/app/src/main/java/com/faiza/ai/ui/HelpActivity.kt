package com.faiza.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FaizaNotificationListener

/** ❓ The buyer's guide in Bangla: a setup checklist (what's on / off, tap to fix) and what to say for each feature. */
class HelpActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() { super.onResume(); render() }

    private val guide = listOf(
        "🗣️ ফাইজাকে ডাকা" to "\"Hey Faiza\" · \"Wake up Faiza\" · \"Online Faiza\" — বা বাবলে চাপ দিন, ইয়ারফোনের বাটন দুইবার চাপুন, Quick Settings-এ \"Faiza\" টাইল, অ্যাপ আইকনে লম্বা চাপ।",
        "📞 কল" to "কল এলে ফাইজা নাম বলবে। বলুন \"ধরো\", \"কেটে দাও\", বা \"পরে কল দিচ্ছি বলে দাও\"।",
        "💬 মেসেজ" to "\"আম্মুকে WhatsApp-এ লেখো আমি আসছি\" · \"আম্মুর ভয়েস মেসেজটা পড়ো\"",
        "🎮 গেমিং" to "\"গেমিং মোড অন করো\" — খেলার সময় শত্রু, দিক, লুট, রাস্তা বলবে। \"চুপ করো\" / \"আবার বলো\"। হেডফোন ব্যবহার করুন।",
        "🕌 নামাজ" to "\"আসর কখন?\" · \"ইফতার কয়টায়?\" · \"কিবলা কোন দিকে?\" — আজানের রিমাইন্ডার Prayer পেজে চালু করুন।",
        "📒 হিসাব" to "\"করিম ৫০০ টাকা বাকি নিল\" · \"আজ বাজারে ৮৫০ খরচ\" · \"এই মাসে কত খরচ হলো?\" — bKash/Nagad SMS থেকে অটো হিসাব খাতার পেজে চালু করুন।",
        "📚 পড়া" to "\"এই অঙ্কটা বুঝিয়ে দাও\" (ক্যামেরা খুলবে) · \"আমার উত্তর ঠিক আছে কিনা দেখো\"",
        "💊 ওষুধ" to "\"প্রেসক্রিপশন দেখে ওষুধের রিমাইন্ডার দাও\" — ফাইজা তালিকা পড়ে শোনাবে, আপনি হ্যাঁ বললে তবেই সেট করবে।",
        "🚗 গাড়ি" to "\"আমি গাড়ি চালাচ্ছি\" — বড় বাটন, গতির সতর্কতা, মেসেজ পড়ে শোনানো। \"বাসায় নিয়ে চলো\"।",
        "🧮 হিসাব-নিকাশ ও জানালা" to "\"৩০০ যোগ ৪৫০ কত?\" · \"ক্যালকুলেটর খোলো\" · \"৫ মিনিটের টাইমার\" · \"আমাকে দেখো\" (ক্যামেরা)",
        "💻 Code Studio" to "\"আমার দোকানের জন্য একটা ওয়েবসাইট বানাও\" — ফাইজার দল বানাবে, টেস্ট করবে, ঠিক করবে।",
        "🔬 রিসার্চ" to "\"আজকের বাংলাদেশের খবর\" · \"৩০ হাজারের মধ্যে ভালো ফোন কোনটা?\"",
        "🆘 বিপদে" to "SOS পেজে কাছের মানুষ যোগ করুন — বিপদে ফাইজা লোকেশনসহ মেসেজ আর কল দেবে।"
    )

    private fun render() {
        col.removeAllViews()
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back); background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "❓ কীভাবে ব্যবহার করবেন"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
            })
        })
        val list = SetupChecks.all
        val done = list.count { it.ok(this) }
        section("✅ সেটআপ — $done/${list.size} হয়েছে")
        col.addView(caption("❌ থাকা লাইনে চাপ দিন, তারপর অনুমতি দিয়ে ফিরে আসুন।"))
        list.forEach { c ->
            val ok = c.ok(this)
            col.addView(TextView(this).apply {
                text = (if (ok) "✅  " else "❌  ") + c.title + "\n" + c.why + if (!ok) "\n👉 " + c.how else ""
                textSize = 15f; setTextColor(getColor(if (ok) R.color.text_primary else R.color.warning)); setLineSpacing(0f, 1.25f)
                setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundResource(R.drawable.bg_input)
                if (!ok) setOnClickListener { c.fix(this@HelpActivity) }
            }, lp(top = 6))
        }
        section("🗣️ কী বলবেন")
        guide.forEach { (t, d) ->
            col.addView(TextView(this).apply {
                text = "$t\n$d"; textSize = 15f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.3f)
                setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundResource(R.drawable.bg_input)
            }, lp(top = 6))
        }
        col.addView(caption("ফাইজা আপনার কথায় কাজ করে। টাকা পাঠানো, পাসওয়ার্ড দেওয়া বা কিছু মুছে ফেলার আগে সবসময় আপনাকে জিজ্ঞেস করবে।"))
    }

    private fun open(i: Intent) { runCatching { startActivity(i) } }

    private fun section(t: String) = col.addView(TextView(this).apply {
        text = t; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(getColor(R.color.primary_light)); setPadding(dp(4), dp(22), 0, dp(2))
    })

    private fun caption(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.3f); setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
