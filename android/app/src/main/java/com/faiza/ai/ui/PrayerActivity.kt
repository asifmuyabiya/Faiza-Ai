package com.faiza.ai.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.prayer.PrayerCalc
import com.faiza.ai.ai.prayer.PrayerCenter
import java.util.Calendar

/** 🕌 Today's prayer times, next prayer, Ramadan Sehri/Iftar, the Qibla compass and reminder settings. */
class PrayerActivity : AppCompatActivity(), SensorEventListener {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var col: LinearLayout
    private lateinit var compass: QiblaCompass
    private lateinit var qiblaText: TextView
    private var qibla = 0.0
    private var declination = 0f
    private var sensors: SensorManager? = null

    private val pickAzan = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.azanSoundUri = uri.toString()
        Toast.makeText(this, "Azan sound saved", Toast.LENGTH_SHORT).show()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)
        sensors = getSystemService(SensorManager::class.java)
        render()
    }

    override fun onResume() {
        super.onResume()
        sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensors?.unregisterListener(this)
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
                text = "🕌 নামাজ · Prayer"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
            })
        })

        val place = PrayerCenter.place(this)
        val h = PrayerCenter.hijri()
        val t = PrayerCenter.timesFor(this)
        val (nName, nMin, tomorrow) = PrayerCenter.next(this)
        col.addView(caption("${h.day} ${h.monthNameBn} ${h.year} হিজরি · 📍 ${place.label}"))

        // Next prayer card.
        val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val left = if (tomorrow) nMin + 24 * 60 - now else nMin - now
        col.addView(TextView(this).apply {
            text = "পরের নামাজ: ${PrayerCalc.NAMES_BN[nName]} · ${PrayerCalc.clock(nMin)}\nআর ${left / 60} ঘণ্টা ${left % 60} মিনিট"
            textSize = 18f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.3f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundResource(R.drawable.bg_input_focused)
        }, lp(top = 12))

        // Times.
        t.list().forEach { (name, min) ->
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(12))
                setBackgroundResource(if (name == nName) R.drawable.bg_input_focused else R.drawable.bg_input)
                addView(TextView(context).apply {
                    text = "${PrayerCalc.NAMES_BN[name]}  ·  $name"; textSize = 15f; setTextColor(getColor(R.color.text_primary))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = PrayerCalc.clock(min); textSize = 15f; setTextColor(getColor(R.color.text_primary))
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                })
            }, lp(top = 6))
        }

        if (PrayerCenter.isRamadanSoon()) {
            section("🌙 রমজান")
            col.addView(TextView(this).apply {
                text = "সেহরির শেষ সময়: ${PrayerCalc.clock(t.fajr)} (কয়েক মিনিট আগে শেষ করুন)\nইফতার: ${PrayerCalc.clock(t.maghrib)}"
                textSize = 16f; setTextColor(getColor(R.color.text_primary)); setLineSpacing(0f, 1.35f)
                setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundResource(R.drawable.bg_input)
            }, lp(top = 6))
        }

        // Qibla.
        section("🧭 কিবলা · Qibla")
        qibla = PrayerCalc.qibla(place.lat, place.lng)
        declination = GeomagneticField(place.lat.toFloat(), place.lng.toFloat(), 0f, System.currentTimeMillis()).declination
        compass = QiblaCompass(this)
        col.addView(compass, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)).apply { topMargin = dp(6) })
        qiblaText = caption("কিবলা: উত্তর থেকে ${qibla.toInt()}° · ফোন সমান করে ধরুন, তীর 🕋-এর দিকে ঘোরান").apply { gravity = Gravity.CENTER }
        col.addView(qiblaText)
        if (sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) col.addView(caption("এই ফোনে কম্পাস সেন্সর নেই — উত্তর থেকে ${qibla.toInt()}° দিকে মুখ করুন।"))

        // Settings.
        section("⚙️ রিমাইন্ডার")
        col.addView(switchRow("আজানের রিমাইন্ডার (৫ ওয়াক্ত)", prefs.azanReminders) { prefs.azanReminders = it; PrayerCenter.schedule(this) })
        col.addView(switchRow("রমজানে সেহরি ও ইফতারের রিমাইন্ডার", prefs.ramadanReminders) { prefs.ramadanReminders = it; PrayerCenter.schedule(this) })
        col.addView(switchRow("হানাফি আসর (বাংলাদেশ)", prefs.prayerHanafi) { prefs.prayerHanafi = it; PrayerCenter.schedule(this); render() })
        col.addView(choiceRow("কতক্ষণ আগে মনে করাবে", listOf(0 to "ঠিক সময়ে", 5 to "৫ মিনিট আগে", 10 to "১০ মিনিট আগে", 15 to "১৫ মিনিট আগে"), prefs.azanBeforeMin) {
            prefs.azanBeforeMin = it; PrayerCenter.schedule(this); render()
        })
        col.addView(choiceRow("হিজরি তারিখ ঠিক করুন (চাঁদ দেখা অনুযায়ী)", listOf(-2 to "−২", -1 to "−১", 0 to "০", 1 to "+১", 2 to "+২"), prefs.hijriAdjust) {
            prefs.hijriAdjust = it; PrayerCenter.schedule(this); render()
        })
        col.addView(choiceRow("হিসাবের পদ্ধতি", PrayerCalc.Method.values().mapIndexed { i, m -> i to m.label }, PrayerCalc.Method.from(prefs.prayerMethod).ordinal) {
            prefs.prayerMethod = PrayerCalc.Method.values()[it].name; PrayerCenter.schedule(this); render()
        })
        col.addView(TextView(this).apply {
            text = if (prefs.azanSoundUri.isNullOrBlank()) "🔊 আজানের অডিও বেছে নিন (না দিলে ফাইজা মুখে বলবে)" else "🔊 আজানের অডিও: বাছাই করা আছে · বদলাতে চাপুন"
            textSize = 15f; setTextColor(getColor(R.color.text_primary))
            setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundResource(R.drawable.bg_input)
            setOnClickListener { pickAzan.launch(arrayOf("audio/*")) }
        }, lp(top = 6))
        col.addView(caption("সময় ফোনেই হিসাব করা, ইন্টারনেট লাগে না। মসজিদের সময়ের সাথে ১–৩ মিনিট পার্থক্য হতে পারে; হিজরি তারিখ চাঁদ দেখার উপর ১ দিন এদিক-ওদিক হতে পারে।"))
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!::compass.isInitialized) return
        val r = FloatArray(9); val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(r, e.values)
        SensorManager.getOrientation(r, o)
        val heading = (Math.toDegrees(o[0].toDouble()) + declination + 360) % 360
        val turn = ((qibla - heading + 540) % 360) - 180 // −180..180
        compass.angle = (qibla - heading).toFloat()
        compass.aligned = kotlin.math.abs(turn) < 5
        qiblaText.text = if (compass.aligned) "✅ আপনি কিবলার দিকে আছেন" else
            "কিবলা: ${qibla.toInt()}° · ${if (turn > 0) "ডানে" else "বামে"} ${kotlin.math.abs(turn).toInt()}° ঘুরুন"
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun switchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(8), dp(8), dp(8)); setBackgroundResource(R.drawable.bg_input)
        layoutParams = lp(top = 6)
        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(SwitchCompat(context).apply { isChecked = value; setOnCheckedChangeListener { _, b -> onChange(b) } })
    }

    private fun choiceRow(label: String, options: List<Pair<Int, String>>, current: Int, onPick: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundResource(R.drawable.bg_input)
        layoutParams = lp(top = 6)
        addView(TextView(context).apply { text = label; textSize = 14f; setTextColor(getColor(R.color.text_secondary)) })
        val wrap = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { (v, name) ->
            wrap.addView(TextView(context).apply {
                text = (if (v == current) "● " else "○ ") + name; textSize = 15f
                setTextColor(getColor(if (v == current) R.color.text_primary else R.color.text_secondary))
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { onPick(v) }
            })
        }
        addView(wrap)
    }

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

/** A dial with an arrow pointing to the Kaaba; turns green when the phone faces the Qibla. */
class QiblaCompass(ctx: Context) : View(ctx) {
    var angle = 0f
        set(v) { field = v; invalidate() }
    var aligned = false
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = ctx.getColor(R.color.primary_dark) }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 64f }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(width, height) / 2f - 16f
        c.drawCircle(cx, cy, r, ring)
        arrow.color = context.getColor(if (aligned) R.color.success else R.color.primary)
        c.save()
        c.rotate(angle, cx, cy)
        val p = Path().apply {
            moveTo(cx, cy - r + 24f); lineTo(cx - 26f, cy + 10f); lineTo(cx, cy - 6f); lineTo(cx + 26f, cy + 10f); close()
        }
        c.drawPath(p, arrow)
        c.drawText("🕋", cx, cy - r + 10f, label)
        c.restore()
    }
}
