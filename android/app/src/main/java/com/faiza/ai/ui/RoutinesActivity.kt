package com.faiza.ai.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
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
import androidx.lifecycle.lifecycleScope
import com.faiza.ai.R
import com.faiza.ai.ai.routines.Repeat
import com.faiza.ai.ai.routines.RoutineCenter
import com.faiza.ai.ai.routines.Routines
import com.faiza.ai.ai.routines.ScheduledTask
import com.faiza.ai.ai.routines.Watch
import com.faiza.ai.ai.routines.WatchAction
import com.faiza.ai.ai.routines.WatchKind
import com.faiza.ai.service.FaizaNotificationListener
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Scheduled tasks and watches (video 40:31). Most are made by voice; here you can see, pause and delete them. */
class RoutinesActivity : AppCompatActivity() {

    private val zone get() = ZoneId.systemDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routines)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnNewTask).setOnClickListener { newTaskDialog() }
        findViewById<View>(R.id.cardNotifAccess).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        // Refresh when a task runs or Faiza adds something by voice while this screen is open.
        lifecycleScope.launch { RoutineCenter.version.collect { render() } }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val tasks = RoutineCenter.tasks().sortedWith(compareBy({ !it.enabled }, { it.hour * 60 + it.minute }))
        val watches = RoutineCenter.watches().sortedBy { !it.enabled }

        val taskList = findViewById<LinearLayout>(R.id.taskList)
        taskList.removeAllViews()
        tasks.forEachIndexed { i, t ->
            if (i > 0) taskList.addView(divider())
            taskList.addView(taskRow(t))
        }
        findViewById<View>(R.id.cardTasks).visibility = if (tasks.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvTasksEmpty).visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE

        val watchList = findViewById<LinearLayout>(R.id.watchList)
        watchList.removeAllViews()
        watches.forEachIndexed { i, w ->
            if (i > 0) watchList.addView(divider())
            watchList.addView(watchRow(w))
        }
        findViewById<View>(R.id.cardWatches).visibility = if (watches.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tvWatchesEmpty).visibility = if (watches.isEmpty()) View.VISIBLE else View.GONE

        val needsNotif = watches.any { it.enabled && it.kind == WatchKind.MESSAGE } && FaizaNotificationListener.instance == null
        findViewById<View>(R.id.cardNotifAccess).visibility = if (needsNotif) View.VISIBLE else View.GONE
    }

    // ===== Rows =====

    private fun taskSubtitle(t: ScheduledTask): String {
        val now = ZonedDateTime.now(zone)
        val next = Routines.nextRun(t, now)
        return when {
            next != null -> "${Routines.describe(t, zone)} · next ${Routines.whenText(next, now)}"
            t.lastRunAt > 0 -> "Done · ran ${Routines.whenText(t.lastRunAt, now)}"
            else -> "${Routines.describe(t, zone)} · paused"
        }
    }

    private fun taskRow(t: ScheduledTask): View = row(
        icon = R.drawable.ic_timer, tile = R.drawable.bg_tile_cyan, tint = R.color.tile_cyan,
        title = t.label, subtitle = taskSubtitle(t), checked = t.enabled,
        canToggle = !(t.repeat == Repeat.ONCE && t.lastRunAt > 0 && !t.enabled),
        onToggle = { on ->
            RoutineCenter.setTaskEnabled(this, t.id, on)
            if (on && Routines.nextRun(t.copy(enabled = true), ZonedDateTime.now(zone)) == null) {
                Toast.makeText(this, "That time has passed — create a new one", Toast.LENGTH_SHORT).show()
            }
        },
        onClick = {
            details(t.label, "${Routines.describe(t, zone)}\n\nFaiza will: ${t.instruction}") {
                RoutineCenter.deleteTask(this, t.id)
            }
        }
    )

    private fun watchSubtitle(w: Watch): String {
        if (w.kind == WatchKind.SCREEN) return "Screen · waiting for “${w.keywords}”"
        val parts = mutableListOf<String>()
        parts += w.app.ifBlank { "Any app" }
        if (w.sender.isNotBlank()) parts += "from ${w.sender.substringBefore(',').trim()}"
        if (w.keywords.isNotBlank()) parts += "with “${w.keywords.substringBefore(',').trim()}”"
        parts += when (w.action) {
            WatchAction.TELL -> "tell you"
            WatchAction.REPLY -> "auto-reply"
            WatchAction.TASK -> "do a task"
        }
        if (w.expiresAt > 0) {
            parts += if (w.expiresAt < System.currentTimeMillis()) "ended"
            else "until " + Instant.ofEpochMilli(w.expiresAt).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        }
        return parts.joinToString(" · ")
    }

    private fun watchRow(w: Watch): View = row(
        icon = if (w.kind == WatchKind.SCREEN) R.drawable.ic_smartphone else R.drawable.ic_chat,
        tile = if (w.kind == WatchKind.SCREEN) R.drawable.bg_tile_slate else R.drawable.bg_tile_blue,
        tint = if (w.kind == WatchKind.SCREEN) R.color.tile_slate else R.color.tile_blue,
        title = w.label, subtitle = watchSubtitle(w), checked = w.enabled, canToggle = true,
        onToggle = { on -> RoutineCenter.setWatchEnabled(w.id, on) },
        onClick = {
            val what = when (w.action) {
                WatchAction.TELL -> "Faiza tells you."
                WatchAction.REPLY -> "Faiza replies: “${w.replyText}”"
                WatchAction.TASK -> "Faiza will: ${w.instruction}"
            }
            val count = if (w.triggerCount > 0) "\n\nHappened ${w.triggerCount} time(s)." else ""
            details(w.label, "${watchSubtitle(w)}\n\n$what$count") { RoutineCenter.deleteWatch(w.id) }
        }
    )

    private fun row(
        icon: Int, tile: Int, tint: Int, title: String, subtitle: String,
        checked: Boolean, canToggle: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(66)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(icon)
            setBackgroundResource(tile)
            imageTintList = getColorStateList(tint)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            alpha = if (checked) 1f else 0.5f
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14); marginEnd = dp(8)
            }
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(getColor(if (checked) R.color.text_primary else R.color.text_secondary))
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, dp(3), 0, 0)
            })
        })
        if (canToggle) {
            row.addView(SwitchMaterial(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, on -> onToggle(on) }
            })
        }
        return row
    }

    private fun details(title: String, message: String, onDelete: () -> Unit) {
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNegativeButton("Delete") { _, _ -> onDelete() }
            .show()
    }

    // ===== New task by hand =====

    private fun newTaskDialog() {
        var hour = 8
        var minute = 0
        var repeat = Repeat.DAILY
        val repeats = listOf(Repeat.ONCE, Repeat.DAILY, Repeat.WEEKDAYS, Repeat.WEEKENDS)
        val repeatLabels = arrayOf("Once", "Every day", "Weekdays", "Weekends")

        val input = EditText(this).apply {
            hint = "e.g. Remind me to drink water"
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        fun chip() = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_pill)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val timeChip = chip()
        val repeatChip = chip()
        fun refresh() {
            timeChip.text = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            repeatChip.text = repeatLabels[repeats.indexOf(repeat)]
        }
        refresh()
        timeChip.setOnClickListener {
            TimePickerDialog(this, R.style.Theme_FaizaAI_Dialog, { _, h, m -> hour = h; minute = m; refresh() }, hour, minute, false).show()
        }
        repeatChip.setOnClickListener {
            AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
                .setTitle("Repeat")
                .setSingleChoiceItems(repeatLabels, repeats.indexOf(repeat)) { d, which ->
                    repeat = repeats[which]
                    refresh()
                    d.dismiss()
                }
                .show()
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            addView(timeChip)
            addView(repeatChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8) })
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(chips)
        }
        AlertDialog.Builder(this, R.style.Theme_FaizaAI_Dialog)
            .setTitle("New scheduled task")
            .setMessage("Faiza will do this at the time you choose and say it out loud.")
            .setView(form)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "Write what Faiza should do", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val task = ScheduledTask(
                    label = text.take(40),
                    instruction = "The user wrote this task: \"$text\". Do it now (if it's a reminder, remind them).",
                    hour = hour, minute = minute, repeat = repeat
                )
                RoutineCenter.upsertTask(this, task)
                val next = Routines.nextRun(task, ZonedDateTime.now(zone))
                next?.let { Toast.makeText(this, "Next: ${Routines.whenText(it, ZonedDateTime.now(zone))}", Toast.LENGTH_SHORT).show() }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Helpers =====

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.divider))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(70) }
    }

    private fun ripple(): Int {
        val out = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
