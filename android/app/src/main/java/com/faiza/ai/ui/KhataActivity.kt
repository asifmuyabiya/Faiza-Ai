package com.faiza.ai.ui

import android.content.ContentValues
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.ai.khata.Khata
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 📒 The money book: this month's spending, who owes whom, recent entries, add by hand, export CSV. */
class KhataActivity : AppCompatActivity() {

    private lateinit var col: LinearLayout
    private var which = "month"
    private val prefs get() = com.faiza.ai.FaizaApp.instance.prefs

    private val smsPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) enableSms() else { toast("SMS পড়ার অনুমতি দরকার"); render() }
    }

    /** Turn on and bring in the last 30 days. */
    private fun enableSms() {
        prefs.smsMoneyLog = true
        Thread {
            val n = runCatching { com.faiza.ai.ai.khata.MoneySms.importRecent(this, 30) }.getOrDefault(0)
            runOnUiThread { toast("গত ৩০ দিনের $n টা লেনদেন খাতায় যোগ হয়েছে"); render() }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)
    }

    override fun onResume() { super.onResume(); render() }

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
                text = "📒 হিসাবের খাতা"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "➕"; textSize = 22f; setPadding(dp(10), dp(6), dp(6), dp(6))
                setOnClickListener { addDialog() }
            })
        })
        col.addView(caption("মুখে বলুন: \"করিম ৫০০ টাকা বাকি নিল\", \"আজ বাজারে ৮৫০ খরচ\", \"রহিমকে ৫০০ শোধ দিলাম\", \"এই মাসে কত খরচ হলো?\""))

        // bKash / Nagad SMS → book.
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8)); setBackgroundResource(R.drawable.bg_input)
            addView(TextView(context).apply {
                text = "💸 bKash / Nagad / Rocket SMS থেকে অটো হিসাব\n(ফাইজা শুধু পড়ে, কখনো টাকা পাঠায় না)"
                textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(androidx.appcompat.widget.SwitchCompat(context).apply {
                isChecked = prefs.smsMoneyLog
                setOnCheckedChangeListener { _, on ->
                    if (!on) { prefs.smsMoneyLog = false; return@setOnCheckedChangeListener }
                    val need = arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS)
                    if (need.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) enableSms()
                    else smsPermission.launch(need)
                }
            })
        }, lp(top = 10))

        val entries = Khata.all(this)
        // Period chips.
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        listOf("today" to "আজ", "week" to "৭ দিন", "month" to "এই মাস", "last_month" to "গত মাস", "all" to "সব").forEach { (k, l) ->
            chips.addView(TextView(this).apply {
                text = l; textSize = 13f
                setTextColor(getColor(if (k == which) R.color.text_primary else R.color.text_secondary))
                setBackgroundResource(if (k == which) R.drawable.bg_input_focused else R.drawable.bg_input)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                setOnClickListener { which = k; render() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        col.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val p = Khata.period(which)
        val s = Khata.summary(entries, p)
        col.addView(TextView(this).apply {
            text = "${p.label}\nখরচ: ${Khata.taka(s.spent)}   ·   আয়: ${Khata.taka(s.earned)}" +
                    if (s.byNote.isNotEmpty()) "\n" + s.byNote.take(5).joinToString("\n") { "• ${it.first}: ${Khata.taka(it.second)}" } else ""
            textSize = 16f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.35f)
            setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.bg_input_focused)
        }, lp(top = 10))

        val bal = Khata.balances(entries)
        section("👥 বাকি ও ধার")
        if (bal.isEmpty()) col.addView(caption("কারো সাথে বাকি বা ধার নেই।"))
        bal.forEach { b ->
            col.addView(TextView(this).apply {
                text = if (b.net >= 0) "${b.person} দেবে ${Khata.taka(b.net)}" else "${b.person}-কে দিতে হবে ${Khata.taka(-b.net)}"
                textSize = 15f; setTextColor(getColor(if (b.net >= 0) R.color.success else R.color.warning))
                setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundResource(R.drawable.bg_input)
                setOnClickListener { settleDialog(b) }
            }, lp(top = 6))
        }

        section("🧾 সাম্প্রতিক")
        val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        entries.takeLast(40).reversed().forEach { e ->
            col.addView(TextView(this).apply {
                text = "${e.type.bn} ${Khata.taka(e.amount)}" + (if (e.person.isNotBlank()) " · ${e.person}" else "") +
                        (if (e.note.isNotBlank()) " · ${e.note}" else "") + (if (e.source == "sms") " · SMS" else "") + "\n${fmt.format(Date(e.at))}"
                textSize = 14f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.25f)
                setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundResource(R.drawable.bg_input)
                setOnLongClickListener {
                    AlertDialog.Builder(this@KhataActivity, R.style.Theme_FaizaAI_Dialog)
                        .setTitle("এই হিসাবটা মুছবেন?").setMessage(text)
                        .setPositiveButton("মুছুন") { _, _ -> Khata.delete(this@KhataActivity, e.id); render() }
                        .setNegativeButton("না", null).show()
                    true
                }
            }, lp(top = 6))
        }
        if (entries.isEmpty()) col.addView(caption("এখনো কিছু লেখা হয়নি।"))
        col.addView(TextView(this).apply {
            text = "📤 Excel/CSV ফাইলে সেভ করুন"; textSize = 15f; setTextColor(getColor(R.color.text_primary))
            setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundResource(R.drawable.bg_input)
            setOnClickListener { exportCsv() }
        }, lp(top = 16))
        col.addView(caption("লম্বা চাপ দিলে একটা হিসাব মুছে যাবে।"))
    }

    private fun settleDialog(b: Khata.Balance) {
        val amount = kotlin.math.abs(b.net)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(b.person)
            .setMessage(if (b.net >= 0) "${b.person} কি ${Khata.taka(amount)} ফেরত দিয়েছে?" else "আপনি কি ${b.person}-কে ${Khata.taka(amount)} দিয়েছেন?")
            .setPositiveButton("হ্যাঁ, সব শোধ") { _, _ ->
                Khata.add(this, Khata.Entry(type = if (b.net >= 0) Khata.Type.GOT_BACK else Khata.Type.PAID_BACK, amount = amount, person = b.person, note = "সব শোধ", source = "hand"))
                render()
            }
            .setNegativeButton("না", null).show()
    }

    private fun addDialog() {
        val types = Khata.Type.values()
        var picked = Khata.Type.EXPENSE
        val amount = box("টাকা", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val person = box("কে (বাকি/ধারের জন্য)", InputType.TYPE_CLASS_TEXT)
        val note = box("কিসের জন্য", InputType.TYPE_CLASS_TEXT)
        val typeLine = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.text_primary)); setPadding(dp(4), dp(8), dp(4), dp(8)) }
        fun showType() { typeLine.text = "ধরন: ${picked.bn}  (বদলাতে চাপুন)" }
        showType()
        typeLine.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog).setItems(types.map { it.bn }.toTypedArray()) { _, i -> picked = types[i]; showType() }.show()
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0)
            addView(typeLine); addView(amount, lp(top = 6)); addView(person, lp(top = 6)); addView(note, lp(top = 6))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("নতুন হিসাব").setView(box)
            .setPositiveButton("সেভ") { _, _ ->
                val a = amount.text.toString().toDoubleOrNull()
                if (a == null || a <= 0) { toast("টাকার পরিমাণ লিখুন"); return@setPositiveButton }
                Khata.add(this, Khata.Entry(type = picked, amount = a, person = person.text.toString().trim(), note = note.text.toString().trim(), source = "hand"))
                render()
            }
            .setNegativeButton("বাতিল", null).show()
    }

    private fun exportCsv() {
        val name = "faiza-khata-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv"
        runCatching {
            val bytes = ("﻿" + Khata.csv(this)).toByteArray(Charsets.UTF_8) // BOM so Excel shows Bangla
            if (Build.VERSION.SDK_INT >= 29) {
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                })!!
                contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            } else {
                @Suppress("DEPRECATION")
                java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name).writeBytes(bytes)
            }
        }.onSuccess { toast("Downloads-এ সেভ হয়েছে: $name") }.onFailure { toast("সেভ করা গেল না: ${it.message}") }
    }

    private fun box(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; textSize = 15f
        setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_muted))
        setBackgroundResource(R.drawable.bg_input); setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun section(t: String) = col.addView(TextView(this).apply {
        text = t; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(getColor(R.color.primary_light)); setPadding(dp(4), dp(22), 0, dp(2))
    })

    private fun caption(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.3f); setPadding(dp(4), dp(10), dp(4), 0)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
