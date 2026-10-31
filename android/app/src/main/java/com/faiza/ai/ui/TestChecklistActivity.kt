package com.faiza.ai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R

/**
 * The owner's 15-minute phone test (test APK only): what to do, what should happen, ✅ / ❌, then "copy results"
 * to paste to Claude. Ticks are kept on the phone.
 */
class TestChecklistActivity : AppCompatActivity() {

    data class Item(val id: String, val doThis: String, val expect: String)

    companion object {
        val SECTIONS: List<Pair<String, List<Item>>> = listOf(
            "১. শুরু" to listOf(
                Item("setup", "নতুন করে ইনস্টল করে অ্যাপ খুলুন", "ধাপে ধাপে সেটআপ আসে, প্রতিটা অনুমতির পরে নিজে পরের ধাপে যায়"),
                Item("wake", "বাবল বন্ধ রেখে বলুন \"Wake up Faiza\"", "বাবল আসে, ফাইজা উত্তর দেয়, কথা শেষে বাবল লুকায়"),
                Item("tile", "উপর থেকে টেনে Quick Settings-এ \"Faiza\" টাইল যোগ করে চাপুন", "ফাইজা শুনতে শুরু করে"),
                Item("headset", "ইয়ারফোন লাগিয়ে বাটন দুইবার দ্রুত চাপুন", "ফাইজা শুনতে শুরু করে"),
                Item("speed", "সাধারণ প্রশ্ন করুন: \"আজকের আবহাওয়া কেমন?\"", "আগের চেয়ে তাড়াতাড়ি উত্তর আসে")
            ),
            "২. কল ও মেসেজ" to listOf(
                Item("callname", "অন্য ফোন থেকে কল দিন (নম্বরটা My People-এ ডাকনামসহ রাখুন)", "\"বস, <ডাকনাম> ফোন দিছে\" বলে"),
                Item("callreply", "কল বাজার সময় বলুন \"পরে কল দিচ্ছি বলে দাও\"", "কল কেটে যায়, ওই নম্বরে SMS যায়"),
                Item("voicenote", "কেউ WhatsApp ভয়েস মেসেজ পাঠালে বলুন \"ভয়েস মেসেজটা পড়ো\"", "কী বলেছে ছোট করে বলে"),
                Item("wa", "\"আম্মুকে WhatsApp-এ লেখো আমি আসছি\"", "মেসেজ ঠিক মানুষকে যায়")
            ),
            "৩. প্রতিদিনের কাজ" to listOf(
                Item("prayer", "\"আসর কখন?\" · Prayer পেজ খুলুন", "সময় মসজিদের সাথে ১–৩ মিনিটের মধ্যে মেলে; কম্পাস ঘোরালে কিবলায় সবুজ হয়"),
                Item("khata", "\"করিম ৫০০ টাকা বাকি নিল\" তারপর \"করিম কত দেবে?\"", "৫০০ বলে; খাতার পেজে দেখায়"),
                Item("bkash", "খাতার পেজে bKash SMS সুইচ চালু করুন", "গত ৩০ দিনের লেনদেন খাতায় আসে"),
                Item("homework", "\"এই অঙ্কটা বুঝিয়ে দাও\" — খাতায় একটা অঙ্ক ধরুন", "ছবি তুলে ধাপে ধাপে সমাধান কার্ডে দেখায়, উত্তর ঠিক"),
                Item("medicine", "প্রেসক্রিপশন দেখিয়ে \"ওষুধের রিমাইন্ডার দাও\"", "ওষুধের নাম পড়ে শোনায়, হ্যাঁ বললে Routines-এ আসে"),
                Item("offline", "ইন্টারনেট বন্ধ করে \"কয়টা বাজে?\" · \"টর্চ জ্বালাও\"", "নেট ছাড়াই কাজ করে")
            ),
            "৪. গেম ও গাড়ি" to listOf(
                Item("ff", "Free Fire খুলুন, \"হ্যাঁ\" বলে গেমিং মোড চালু করুন, একটা ম্যাচ খেলুন", "দূরের শত্রু আগে বলে, আটকে যায় না, গেম ল্যাগ করে না"),
                Item("ffreport", "ম্যাচ শেষে গেম থেকে বের হন", "আজকের রিপোর্ট বলে (ম্যাচ, কিল), শেয়ার করতে চায়"),
                Item("bus", "বাস গেম খুলে গেমিং মোড চালু করুন", "আগে থেকে মোড়/গাড়ির কথা বলে, হেলপারের মতো মজা করে"),
                Item("driving", "\"আমি গাড়ি চালাচ্ছি\" (হাঁটতে হাঁটতে বা রিকশায় চেষ্টা করুন)", "বড় বাটনের পেজ, গতি দেখায়, মেসেজ এলে পড়ে শোনায়")
            ),
            "৫. অন্যান্য" to listOf(
                Item("windows", "\"ক্যালকুলেটর খোলো\" · \"৫ মিনিটের টাইমার\" · \"আমাকে দেখো\"", "ভাসমান জানালা আসে, ছোট/বন্ধ করা যায়"),
                Item("drawer", "ড্যাশবোর্ডের মেনু খুলে \"নামাজ\" লিখে খুঁজুন", "Prayer times পাওয়া যায়"),
                Item("backup", "Settings → Backup করে আবার Restore করুন", "খাতা, ওষুধ, সেটিং ফিরে আসে"),
                Item("battery", "রাতে চার্জ ৩০%-এর নিচে, স্ক্রিন বন্ধ অবস্থায় রেখে দিন", "সকালে চার্জ আগের চেয়ে কম খরচ হয়েছে")
            )
        )
    }

    private lateinit var col: LinearLayout
    private val store by lazy { getSharedPreferences("test_checklist", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)
        render()
    }

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
                text = "🧪 ফোন টেস্ট"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
            })
        })
        val all = SECTIONS.flatMap { it.second }
        val pass = all.count { store.getString(it.id, "") == "ok" }
        val fail = all.count { store.getString(it.id, "") == "bad" }
        col.addView(caption("প্রতিটা করুন → যা হওয়ার কথা হলে ✅, না হলে ❌। শেষে \"ফলাফল কপি\" চেপে Claude-কে পাঠান।\n✅ $pass · ❌ $fail · বাকি ${all.size - pass - fail}"))
        SECTIONS.forEach { (title, items) ->
            col.addView(TextView(this).apply {
                text = title; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.primary_light)); setPadding(dp(4), dp(20), 0, dp(2))
            })
            items.forEach { item ->
                val state = store.getString(item.id, "")
                col.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
                    setBackgroundResource(if (state == "") R.drawable.bg_input else R.drawable.bg_input_focused)
                    layoutParams = lp(top = 6)
                    addView(TextView(context).apply {
                        text = "👉 ${item.doThis}"; textSize = 15f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                    })
                    addView(TextView(context).apply {
                        text = "হওয়ার কথা: ${item.expect}"; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
                        setLineSpacing(0f, 1.25f); setPadding(0, dp(4), 0, dp(6))
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        fun btn(label: String, value: String) = TextView(context).apply {
                            text = label; textSize = 15f; gravity = Gravity.CENTER
                            setTextColor(getColor(if (state == value) R.color.text_primary else R.color.text_muted))
                            setBackgroundResource(if (state == value) R.drawable.bg_input_focused else R.drawable.bg_input)
                            setPadding(dp(16), dp(8), dp(16), dp(8))
                            setOnClickListener { store.edit().putString(item.id, if (state == value) "" else value).apply(); render() }
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
                        }
                        addView(btn("✅ হয়েছে", "ok")); addView(btn("❌ হয়নি", "bad"))
                    })
                })
            }
        }
        col.addView(TextView(this).apply {
            text = "📋 ফলাফল কপি করুন"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_primary)); setBackgroundResource(R.drawable.bg_input_focused)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { copyResults() }
        }, lp(top = 20))
        col.addView(TextView(this).apply {
            text = "সব মুছে নতুন করে শুরু"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_muted))
            setPadding(0, dp(14), 0, 0)
            setOnClickListener { store.edit().clear().apply(); render() }
        })
    }

    private fun copyResults() {
        val text = buildString {
            appendLine("Faiza phone test (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}):")
            SECTIONS.forEach { (title, items) ->
                appendLine(title)
                items.forEach { i ->
                    val s = when (store.getString(i.id, "")) { "ok" -> "✅"; "bad" -> "❌"; else -> "⬜" }
                    appendLine("$s ${i.id}: ${i.doThis}")
                }
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Faiza test", text))
        Toast.makeText(this, "কপি হয়েছে — Claude-এর চ্যাটে পেস্ট করুন। ❌ গুলোর পাশে কী হলো একটু লিখে দিন।", Toast.LENGTH_LONG).show()
    }

    private fun caption(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.3f); setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
