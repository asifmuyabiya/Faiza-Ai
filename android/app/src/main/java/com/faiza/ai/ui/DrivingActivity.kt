package com.faiza.ai.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.driving.Driving
import com.faiza.ai.assist.AssistLauncher
import com.faiza.ai.service.FloatingOrbService

/** 🚗 Big buttons for the road: talk, home, work, call, music, speed. The screen stays on. */
class DrivingActivity : AppCompatActivity(), LocationListener {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var speedView: TextView
    private lateinit var limitView: TextView
    private var lm: LocationManager? = null
    private val watch = Driving.SpeedWatch({ prefs.drivingSpeedLimit })
    private val bangla get() = com.faiza.ai.ai.AppLanguage.from(prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(20)) }
        scroll.addView(col)
        setContentView(scroll)

        speedView = TextView(this).apply {
            text = "— km/h"; textSize = 54f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
        }
        limitView = TextView(this).apply {
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(getColor(R.color.text_secondary))
            setOnClickListener { limitDialog() }
        }
        col.addView(speedView); col.addView(limitView)
        renderLimit()

        col.addView(big("🎙️  ফাইজাকে বলুন · Talk", R.drawable.bg_input_focused, 26f) { AssistLauncher.activate(this) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(110)).apply { topMargin = dp(14) })

        val grid = GridLayout(this).apply { columnCount = 2 }
        fun cell(label: String, action: () -> Unit, long: (() -> Unit)? = null) = grid.addView(big(label, R.drawable.bg_input, 20f, action).apply {
            if (long != null) setOnLongClickListener { long(); true }
        }, GridLayout.LayoutParams().apply {
            width = 0; height = dp(96); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(dp(5), dp(5), dp(5), dp(5))
        })
        cell("🏠 বাসা", { go(prefs.homeAddress, "home") }, { placeDialog("home") })
        cell("💼 অফিস", { go(prefs.workAddress, "work") }, { placeDialog("work") })
        cell("📞 কল", { callDialog() })
        cell("⏯ গান", { media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
        cell("⏭ পরের গান", { media(KeyEvent.KEYCODE_MEDIA_NEXT) })
        cell("🗺️ ম্যাপ", { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).setPackage("com.google.android.apps.maps")) } })
        col.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        col.addView(big("🛑 ড্রাইভিং শেষ", R.drawable.bg_input, 18f) {
            runCatching { com.faiza.ai.ai.modes.ModeCenter.setManual(this, null) }
            finish()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)).apply { topMargin = dp(12) })
        col.addView(TextView(this).apply {
            text = "মেসেজ এলে ফাইজা পড়ে শোনাবে আর উত্তর দিতে চাইলে বলবেন। কল এলে Driving মোড SMS দিয়ে জানিয়ে দেবে।\nবাসা/অফিসে লম্বা চাপ দিয়ে ঠিকানা বদলান · স্পিডে চাপ দিয়ে লিমিট বদলান"
            textSize = 13f; setTextColor(getColor(R.color.text_secondary)); setLineSpacing(0f, 1.3f); setPadding(dp(4), dp(12), dp(4), 0)
        })
        runCatching { com.faiza.ai.ai.modes.ModeCenter.setManual(this, "driving") }
    }

    override fun onResume() {
        super.onResume()
        Driving.screenOpen = true
        startSpeed()
    }

    override fun onPause() {
        super.onPause()
        lm?.removeUpdates(this)
    }

    override fun onDestroy() {
        Driving.screenOpen = false
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startSpeed() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            speedView.text = "📍 GPS"
            speedView.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 31) }
            return
        }
        lm = getSystemService(LocationManager::class.java)
        runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 31) startSpeed()
    }

    override fun onLocationChanged(loc: Location) {
        if (!loc.hasSpeed()) return
        val kmh = loc.speed * 3.6
        speedView.text = "${kmh.toInt()} km/h"
        val lim = prefs.drivingSpeedLimit
        speedView.setTextColor(getColor(if (lim > 0 && kmh > lim) R.color.warning else R.color.text_primary))
        if (watch.onSpeed(kmh, SystemClock.elapsedRealtime())) {
            FloatingOrbService.instance?.announceEvent(Driving.speedWarning(bangla, kmh.toInt(), lim))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { speedView.text = "GPS বন্ধ" }

    private fun renderLimit() {
        limitView.text = if (prefs.drivingSpeedLimit > 0) "⚠️ স্পিড লিমিট ${prefs.drivingSpeedLimit} km/h (বদলাতে চাপুন)" else "স্পিড সতর্কতা বন্ধ (চালু করতে চাপুন)"
    }

    private fun go(place: String, which: String) {
        if (place.isBlank()) { placeDialog(which); return }
        if (!Driving.navigate(this, place)) toast("Maps খোলা গেল না")
    }

    private fun placeDialog(which: String) {
        val box = EditText(this).apply {
            hint = if (which == "home") "বাসার ঠিকানা" else "অফিসের ঠিকানা"
            setText(if (which == "home") prefs.homeAddress else prefs.workAddress)
            inputType = InputType.TYPE_CLASS_TEXT; textSize = 16f
            setTextColor(getColor(R.color.text_primary)); setBackgroundResource(R.drawable.bg_input); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(if (which == "home") "🏠 বাসা" else "💼 অফিস")
            .setView(LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box) })
            .setPositiveButton("সেভ") { _, _ ->
                if (which == "home") prefs.homeAddress = box.text.toString().trim() else prefs.workAddress = box.text.toString().trim()
            }
            .setNegativeButton("বাতিল", null).show()
    }

    private fun limitDialog() {
        val options = listOf(0, 40, 60, 80, 100, 120)
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("স্পিড সতর্কতা")
            .setItems(options.map { if (it == 0) "বন্ধ" else "$it km/h" }.toTypedArray()) { _, i -> prefs.drivingSpeedLimit = options[i]; renderLimit() }
            .show()
    }

    private fun callDialog() {
        val people = runCatching { FavoriteContacts.load(prefs) }.getOrDefault(emptyList()).filter { it.number.isNotBlank() }
        if (people.isEmpty()) { AssistLauncher.activate(this); toast("কাকে কল দেবেন বলুন"); return }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("📞 কাকে কল দেবেন?")
            .setItems(people.map { p -> p.nicknames.firstOrNull()?.let { "$it (${p.name})" } ?: p.name }.toTypedArray()) { _, i ->
                val n = people[i].number
                val intent = if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$n")) else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))
                runCatching { startActivity(intent) }
            }
            .setNegativeButton("বাতিল", null).show()
    }

    private fun media(code: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun big(label: String, bg: Int, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = size; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(getColor(R.color.text_primary)); setBackgroundResource(bg)
        setOnClickListener { action() }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
