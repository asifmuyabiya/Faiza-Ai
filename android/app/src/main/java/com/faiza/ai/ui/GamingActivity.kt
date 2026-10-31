package com.faiza.ai.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.game.GameKnowledge
import com.faiza.ai.ai.game.GameMode
import com.faiza.ai.service.FaizaAccessibilityService
import com.faiza.ai.service.FloatingOrbService
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/** 🎮 Gaming mode: on/off, how much she talks, jokes, ask-when-a-game-opens. */
class GamingActivity : AppCompatActivity() {

    private val prefs get() = FaizaApp.instance.prefs
    private lateinit var mainBtn: MaterialButton
    private lateinit var statusLine: TextView
    private lateinit var talkRow: LinearLayout
    private lateinit var qualityRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(32)) }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back); background = null
                imageTintList = getColorStateList(R.color.text_primary)
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(TextView(context).apply {
                text = "🎮 Gaming mode"; textSize = 22f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(R.color.text_primary)); setPadding(dp(8), 0, 0, 0)
            })
        })
        col.addView(caption("While you play, Faiza watches your screen and keeps talking like a coach and commentator: where enemies are, where shots come from, cover, Gloo Walls, loot, zone, ammo — or turns, traffic and overtaking in driving games — plus funny Bangla commentary. Free Fire has its own expert pack; every other game she figures out herself."))

        statusLine = caption("")
        col.addView(statusLine)
        mainBtn = MaterialButton(this).apply { setOnClickListener { toggle() } }
        col.addView(mainBtn, lp(top = 10))

        section(col, "How much she talks")
        talkRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(talkRow)
        renderTalk()

        section(col, "Smoothness")
        col.addView(caption("If the game stutters, choose Smooth. Auto picks for your phone."))
        qualityRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(qualityRow)
        renderQuality()

        section(col, "Options")
        col.addView(switchRow("Funny commentary & teasing enemies", prefs.gamingJokes) { prefs.gamingJokes = it; restartIfOn() })
        col.addView(switchRow("Don't let game sound or her own voice stop her (recommended)", prefs.gamingEchoGuard) {
            prefs.gamingEchoGuard = it; restartIfOn()
        })
        col.addView(switchRow("Ask \"গেমিং মোড অন করব?\" when I open a game", prefs.gamingAsk) { prefs.gamingAsk = it })
        col.addView(switchRow("Start by itself in every game (no asking)", prefs.gamingAutoStart) { prefs.gamingAutoStart = it })
        col.addView(switchRow("Turn off when I leave the game", prefs.gamingAutoOff) { prefs.gamingAutoOff = it })
        col.addView(switchRow("Gaming mode feature enabled", prefs.gamingEnabled) { prefs.gamingEnabled = it; if (!it) FloatingOrbService.instance?.stopGaming(false) })

        section(col, "Say it")
        col.addView(caption("• \"ফাইজা, গেমিং মোড অন করো\" / \"অফ করো\"\n• \"চুপ করো\" — she keeps watching but stops talking · \"আবার বলো\"\n• \"কোন গান নেব?\" \"কোথায় যাব?\" — ask anything mid-game\n• Mode off: \"ফাইজা, একটু গাইড করো\" — one quick look and one tip"))

        section(col, "Good to know")
        col.addView(caption("🎧 Use headphones — otherwise the game sound and her voice mix in the mic.\n" +
                "👁 She sees only what's on your screen (about one picture a second, 1–2 s behind). No wall-hack: she can't see through walls or off-screen.\n" +
                "🔋 Live screen + voice uses more battery and data; the phone may get warm in long sessions.\n" +
                "⚠️ Faiza never touches the game — she only watches and talks. Still, game makers (e.g. Garena) may not like outside helper apps; there's a small risk in ranked matches. Use it at your own risk."))

        lifecycleScope.launch { GameMode.active.collect { render() } }
    }

    override fun onResume() { super.onResume(); render() }

    private fun toggle() {
        val svc = FloatingOrbService.instance
        if (svc == null) { toast("Start Faiza first (the floating orb)"); return }
        if (GameMode.active.value || GameMode.armed) {
            svc.stopGaming(sayBye = false); GameMode.armed = false
        } else {
            if (FaizaAccessibilityService.instance == null) {
                toast("Turn on Faiza's Accessibility Service — she needs it to see the game")
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                return
            }
            prefs.gamingEnabled = true
            val r = svc.requestGaming()
            toast(if (r.startsWith("ERROR")) r.removePrefix("ERROR: ") else "Ready — open your game, she'll start talking")
        }
        render()
    }

    private fun render() {
        statusLine.text = when {
            Build.VERSION.SDK_INT < 30 -> "❌ Needs Android 11 or newer"
            GameMode.active.value -> "🟢 ON — watching ${GameMode.gameName}" + if (GameMode.quiet) " (quiet)" else ""
            GameMode.armed -> "🟡 Ready — starts when you open a game"
            else -> "⚪ Off"
        }
        mainBtn.text = if (GameMode.active.value || GameMode.armed) "Turn gaming mode OFF" else "Turn gaming mode ON"
    }

    private fun renderTalk() {
        talkRow.removeAllViews()
        GameKnowledge.Talk.values().forEach { t ->
            val on = prefs.gamingTalk == t.name
            talkRow.addView(TextView(this).apply {
                text = (if (on) "● " else "○ ") + t.label; textSize = 15f
                setTextColor(getColor(if (on) R.color.text_primary else R.color.text_secondary))
                setBackgroundResource(if (on) R.drawable.bg_input_focused else R.drawable.bg_input)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { prefs.gamingTalk = t.name; renderTalk(); restartIfOn() }
            }, lp(top = 6))
        }
    }

    private fun renderQuality() {
        qualityRow.removeAllViews()
        val options = listOf("" to "Auto — ${GameMode.autoQualityLabel()}") +
                com.faiza.ai.ai.game.GameKnowledge.Quality.values().map { it.name to it.label }
        options.forEach { (key, label) ->
            val on = prefs.gamingQuality == key
            qualityRow.addView(TextView(this).apply {
                text = (if (on) "● " else "○ ") + label; textSize = 15f
                setTextColor(getColor(if (on) R.color.text_primary else R.color.text_secondary))
                setBackgroundResource(if (on) R.drawable.bg_input_focused else R.drawable.bg_input)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { prefs.gamingQuality = key; renderQuality() }
            }, lp(top = 6))
        }
    }

    private fun restartIfOn() { if (GameMode.active.value) FloatingOrbService.instance?.restartGamingSession() }

    private fun switchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(10), dp(8), dp(10)); setBackgroundResource(R.drawable.bg_input)
        layoutParams = lp(top = 6)
        addView(TextView(context).apply {
            text = label; textSize = 15f; setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(SwitchCompat(context).apply { isChecked = value; setOnCheckedChangeListener { _, b -> onChange(b) } })
    }

    private fun section(col: LinearLayout, t: String) = col.addView(TextView(this).apply {
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
