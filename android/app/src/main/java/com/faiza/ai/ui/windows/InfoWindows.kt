package com.faiza.ai.ui.windows

import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.faiza.ai.R
import com.faiza.ai.ai.skills.TaskPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small shared helpers for the window bodies. */
private fun FloatWindow.label(ctx: Context, size: Float, color: Int = R.color.text_primary, bold: Boolean = false) =
    TextView(ctx).apply {
        textSize = size
        setTextColor(ctx.getColor(color))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

private fun pill(ctx: Context, text: String, dp: Float, onClick: () -> Unit) = TextView(ctx).apply {
    this.text = text
    textSize = 14f
    gravity = Gravity.CENTER
    setTextColor(ctx.getColor(R.color.text_primary))
    setBackgroundResource(R.drawable.bg_pill)
    val p = (10 * dp).toInt()
    setPadding(p, (6 * dp).toInt(), p, (6 * dp).toInt())
    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        val m = (3 * dp).toInt(); setMargins(m, m, m, m)
    }
    setOnClickListener { onClick() }
}

private fun row(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

/** 📝 Quick notes: typed or said ("এটা নোট করো…"); kept on the phone. */
class NotesWindow(ctx: Context) : FloatWindow(ctx, WindowKind.NOTES) {

    override val wantsKeyboard = true
    private lateinit var box: EditText

    override fun buildContent(container: FrameLayout) {
        box = EditText(ctx).apply {
            gravity = Gravity.TOP or Gravity.START
            textSize = 15f
            hint = "লিখুন… / Write here…"
            setTextColor(ctx.getColor(R.color.text_primary)); setHintTextColor(ctx.getColor(R.color.text_muted))
            background = null
            setPadding(px(12), px(10), px(12), px(10))
            setText(Notes.text(ctx))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { Notes.save(ctx, s?.toString().orEmpty()) }
            })
        }
        container.addView(box)
    }

    fun refresh() { if (::box.isInitialized) box.setText(Notes.text(ctx)) }
}

object Notes {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("faiza_windows", Context.MODE_PRIVATE)
    fun text(ctx: Context): String = sp(ctx).getString("notes", "").orEmpty()
    fun save(ctx: Context, t: String) = sp(ctx).edit().putString("notes", t).apply()
    fun add(ctx: Context, line: String) {
        val now = text(ctx)
        save(ctx, (if (now.isBlank()) "" else now.trimEnd() + "\n") + "• " + line.trim())
    }
}

/** ⏱️ Countdown timer and stopwatch. When a countdown ends Faiza says so. */
class TimerWindow(ctx: Context) : FloatWindow(ctx, WindowKind.TIMER) {

    override val smallSize = 240 to 230
    override val largeSize = 300 to 300
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var caption: TextView
    private var endsAt = 0L          // countdown end (elapsedRealtime), 0 = none
    private var startedAt = 0L       // stopwatch start, 0 = none
    private var what = ""

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            when {
                endsAt > 0 -> {
                    val left = endsAt - now
                    if (left <= 0) {
                        endsAt = 0; clock.text = "00:00"; caption.text = "✓ শেষ / Done"
                        val say = if (what.isNotBlank()) "সময় শেষ — $what" else "টাইমার শেষ!"
                        com.faiza.ai.service.FloatingOrbService.instance?.deliverTimer(say)
                        return
                    }
                    clock.text = fmt(left)
                }
                startedAt > 0 -> clock.text = fmt(now - startedAt)
                else -> return
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(8), px(6), px(8), px(6)) }
        clock = label(ctx, 40f, bold = true).apply { gravity = Gravity.CENTER; text = "00:00" }
        caption = label(ctx, 12f, R.color.text_secondary).apply { gravity = Gravity.CENTER }
        col.addView(clock); col.addView(caption)
        val r1 = row(ctx)
        listOf(1, 5, 10, 30).forEach { m -> r1.addView(pill(ctx, "${m}m", dp) { countdown(m * 60, "") }) }
        val r2 = row(ctx)
        r2.addView(pill(ctx, "⏱ Stopwatch", dp) { stopwatch() })
        r2.addView(pill(ctx, "■ Stop", dp) { stop() })
        col.addView(r1); col.addView(r2)
        container.addView(col)
    }

    fun countdown(seconds: Int, note: String) {
        handler.removeCallbacks(tick)
        startedAt = 0; what = note
        endsAt = SystemClock.elapsedRealtime() + seconds * 1000L
        caption.text = if (note.isNotBlank()) note else "Countdown"
        handler.post(tick)
    }

    fun stopwatch() {
        handler.removeCallbacks(tick)
        endsAt = 0; startedAt = SystemClock.elapsedRealtime()
        caption.text = "Stopwatch"
        handler.post(tick)
    }

    /** Stops; returns what was showing. */
    fun stop(): String {
        handler.removeCallbacks(tick)
        val shown = clock.text.toString()
        endsAt = 0; startedAt = 0
        caption.text = "Stopped at $shown"
        return shown
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onClosed() { handler.removeCallbacks(tick) }
}

/** 🧾 Live progress of the user's task plan ("ধাপ ২/৫ …"). */
class TasksWindow(ctx: Context) : FloatWindow(ctx, WindowKind.TASKS) {

    override val smallSize = 260 to 260
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var body: TextView
    private val refresh = object : Runnable {
        override fun run() {
            val p = TaskPlan.get()
            body.text = when {
                p == null -> "এখন কোনো কাজের লিস্ট নেই।\nNo task list right now."
                p.finished -> "✓ সব কাজ শেষ\n\n" + p.summary()
                else -> "ধাপ ${p.current + 1}/${p.steps.size}\n\n" + p.summary()
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun buildContent(container: FrameLayout) {
        body = label(ctx, 14f).apply { setPadding(px(12), px(10), px(12), px(10)); setLineSpacing(0f, 1.2f) }
        container.addView(ScrollView(ctx).apply { addView(body) })
        handler.post(refresh)
    }

    override fun onClosed() { handler.removeCallbacks(refresh) }
}

/** 🌍 Type or say something, see it in another language (Bangla ⇄ English ⇄ Arabic ⇄ Hindi). */
class TranslateWindow(ctx: Context) : FloatWindow(ctx, WindowKind.TRANSLATE) {

    override val wantsKeyboard = true
    override val smallSize = 280 to 330
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var input: EditText
    private lateinit var output: TextView
    private var target = "en"
    private val langs = listOf("bn" to "বাংলা", "en" to "English", "ar" to "العربية", "hi" to "हिन्दी")

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(8), px(6), px(8), px(6)) }
        input = EditText(ctx).apply {
            textSize = 14f; minLines = 2; maxLines = 4; gravity = Gravity.TOP
            hint = "লিখুন… / Type…"
            setTextColor(ctx.getColor(R.color.text_primary)); setHintTextColor(ctx.getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input); setPadding(px(10), px(8), px(10), px(8))
        }
        col.addView(input)
        val r = row(ctx)
        langs.forEach { (code, name) -> r.addView(pill(ctx, name, dp) { target = code; run() }) }
        col.addView(r)
        output = label(ctx, 16f, bold = true).apply { setPadding(px(6), px(8), px(6), px(6)); setTextIsSelectable(true) }
        col.addView(ScrollView(ctx).apply { addView(output) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(col)
    }

    /** Faiza already translated: just show both sides. */
    fun show(source: String, result: String) {
        input.setText(source); output.text = result
    }

    fun translate(text: String, to: String) {
        input.setText(text); target = to; run()
    }

    private fun run() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        output.text = "…"
        scope.launch {
            output.text = withContext(Dispatchers.IO) { fetch(text, target) } ?: "অনুবাদ করা গেল না (ইন্টারনেট?)"
        }
    }

    override fun onClosed() { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() }

    companion object {
        /** Google's public translate endpoint; null when offline. */
        fun fetch(text: String, to: String): String? = runCatching {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&dt=t&tl=$to&q=" +
                    URLEncoder.encode(text, "UTF-8")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val parts = JSONArray(body).getJSONArray(0)
            (0 until parts.length()).joinToString("") { parts.getJSONArray(it).optString(0) }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

/** 🌤️ A card Faiza fills: weather, news headlines, prayer times, a list — anything worth seeing at a glance. */
class CardWindow(ctx: Context) : FloatWindow(ctx, WindowKind.CARD) {

    override val smallSize = 280 to 300
    override val largeSize = 340 to 480
    private lateinit var head: TextView
    private lateinit var body: TextView

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(14), px(10), px(14), px(10)) }
        head = label(ctx, 18f, R.color.primary_light, bold = true)
        body = label(ctx, 14f).apply { setLineSpacing(0f, 1.25f); setTextIsSelectable(true) }
        col.addView(head); col.addView(body)
        container.addView(ScrollView(ctx).apply { addView(col) })
    }

    fun fill(title: String, text: String) { head.text = title; body.text = text }
}

/** 🎵 Play / pause / next / previous / volume for whatever music app is playing. */
class MusicWindow(ctx: Context) : FloatWindow(ctx, WindowKind.MUSIC) {

    override val smallSize = 260 to 170
    override val largeSize = 300 to 200
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(6), px(8), px(6), px(6)) }
        val r1 = row(ctx)
        r1.addView(pill(ctx, "⏮", dp) { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
        r1.addView(pill(ctx, "⏯", dp) { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
        r1.addView(pill(ctx, "⏭", dp) { key(KeyEvent.KEYCODE_MEDIA_NEXT) })
        val r2 = row(ctx)
        r2.addView(pill(ctx, "🔉", dp) { volume(-1) })
        r2.addView(pill(ctx, "🔊", dp) { volume(+1) })
        col.addView(r1); col.addView(r2)
        container.addView(col)
    }

    fun control(action: String): String = when (action.lowercase()) {
        "play", "pause", "toggle" -> { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); "toggled play/pause" }
        "next" -> { key(KeyEvent.KEYCODE_MEDIA_NEXT); "next track" }
        "previous", "prev", "back" -> { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS); "previous track" }
        "louder", "up" -> { volume(+1); "volume up" }
        "quieter", "down" -> { volume(-1); "volume down" }
        else -> "unknown action"
    }

    private fun key(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun volume(dir: Int) = am.adjustStreamVolume(
        AudioManager.STREAM_MUSIC, if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
    )
}
