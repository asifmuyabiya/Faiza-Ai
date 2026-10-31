package com.faiza.ai.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faiza.ai.R
import com.faiza.ai.util.ActivityLog
import com.faiza.ai.util.LogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The 🔔 inbox: reminders that ran, messages she answered, macros, updates… */
class ActivityLogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_log)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClear).setOnClickListener {
            ActivityLog.clear()
            render()
        }
        render()
        ActivityLog.markSeen()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.logList)
        list.removeAllViews()
        val entries = ActivityLog.all()
        findViewById<View>(R.id.tvLogEmpty).visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardLog).visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        entries.forEachIndexed { i, e ->
            if (i > 0) list.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.divider))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(70) }
            })
            list.addView(row(e))
        }
    }

    private fun style(kind: String): Triple<Int, Int, Int> = when (kind) {
        "reminder" -> Triple(R.drawable.ic_timer, R.drawable.bg_tile_cyan, R.color.tile_cyan)
        "watch" -> Triple(R.drawable.ic_chat, R.drawable.bg_tile_blue, R.color.tile_blue)
        "reply" -> Triple(R.drawable.ic_send, R.drawable.bg_tile_green, R.color.tile_green)
        "macro" -> Triple(R.drawable.ic_bolt, R.drawable.bg_tile_orange, R.color.tile_orange)
        "update" -> Triple(R.drawable.ic_restore, R.drawable.bg_tile_green, R.color.tile_green)
        "journal" -> Triple(R.drawable.ic_edit, R.drawable.bg_tile_violet, R.color.tile_violet)
        "sos" -> Triple(R.drawable.ic_shield, R.drawable.bg_tile_red, R.color.tile_red)
        else -> Triple(R.drawable.ic_bell, R.drawable.bg_tile_slate, R.color.tile_slate)
    }

    private fun whenText(at: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = at }
        val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(at))
        return when {
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "Today $time"
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday $time"
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH).format(Date(at))
        }
    }

    private fun row(e: LogEntry): View {
        val (icon, bg, tint) = style(e.kind)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(66)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            addView(ImageView(context).apply {
                setImageResource(icon)
                setBackgroundResource(bg)
                imageTintList = getColorStateList(tint)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
                addView(TextView(context).apply {
                    text = e.title
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(getColor(R.color.text_primary))
                })
                if (e.text.isNotBlank()) addView(TextView(context).apply {
                    text = e.text
                    textSize = 13f
                    maxLines = 3
                    setTextColor(getColor(R.color.text_secondary))
                    setPadding(0, dp(2), 0, 0)
                })
                addView(TextView(context).apply {
                    text = whenText(e.at)
                    textSize = 11f
                    setTextColor(getColor(R.color.text_muted))
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
