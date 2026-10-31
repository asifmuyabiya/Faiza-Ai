package com.faiza.ai.ui.windows

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.faiza.ai.ai.code.CodeAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Typing speed of the live code window (pure, unit-tested). */
object CodeTyping {
    const val TICK_MS = 16L
    /** A whole reply is typed out in about this long, however big it is. */
    private const val TARGET_MS = 10_000L

    /**
     * Characters to type in one tick: a steady "typing" look for short code, faster when a lot is waiting, so any
     * reply is typed out in about 10 seconds and never falls far behind the work.
     */
    fun charsPerTick(remaining: Int): Int {
        if (remaining <= 0) return 0
        return (remaining / (TARGET_MS / TICK_MS).toInt()).coerceIn(3, 400).coerceAtMost(remaining)
    }
}

/**
 * 💻 Code Studio, live (owner: "কোডিং করার সময় কোডগুলো স্ক্রিনের উপরে ভেসে উঠবে, VS Code / টার্মিনালের মতো উপরের দিকে
 * স্ক্রল হতে থাকবে"). Floats over every app: the team's progress lines, then each file typed out character by
 * character, always scrolled to the newest line.
 */
class CodeWindow(ctx: Context) : FloatWindow(ctx, WindowKind.CODE) {

    override val smallSize = 300 to 380
    override val largeSize = 360 to 560

    companion object {
        private const val TICK_MS = CodeTyping.TICK_MS
        /** Most text kept on screen; older lines are dropped from the top. */
        private const val MAX_CHARS = 60_000
        private const val KEEP_CHARS = 40_000

        private fun charsPerTick(remaining: Int) = CodeTyping.charsPerTick(remaining)

        private val BG = Color.parseColor("#0D1117")
        private val CODE = Color.parseColor("#E6EDF3")
        private val STATUS = Color.parseColor("#7EE787")
        private val FILE = Color.parseColor("#79C0FF")
        private val DIM = Color.parseColor("#8B949E")
    }

    private class Piece(val text: String, val color: Int)

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = ArrayDeque<Piece>()
    private var current: Piece? = null
    private var pos = 0
    private var cursorOn = true
    private var lastBlink = 0L

    private lateinit var header: TextView
    private lateinit var scroll: ScrollView
    private lateinit var body: TextView
    private val text = SpannableStringBuilder()

    override fun buildContent(container: FrameLayout) {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        header = TextView(ctx).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(STATUS)
            setPadding(px(10), px(6), px(10), px(6))
            background = GradientDrawable().apply { setColor(Color.parseColor("#161B22")) }
        }
        col.addView(header)
        body = TextView(ctx).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(CODE)
            setPadding(px(10), px(8), px(10), px(12))
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(false)
        }
        scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(body)
        }
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(col)

        // Progress lines: only the ones added after the window opened.
        var seen = CodeAgent.log.value.size
        if (seen > 0) CodeAgent.log.value.takeLast(6).forEach { queue.addLast(Piece("$ $it\n", STATUS)) }
        scope.launch {
            CodeAgent.log.collect { lines ->
                if (lines.size < seen) seen = 0 // a new run cleared the log
                lines.drop(seen).forEach { queue.addLast(Piece("\n$ $it\n", STATUS)) }
                seen = lines.size
            }
        }
        scope.launch {
            CodeAgent.stage.collect { s -> header.text = "${s.emoji} ${s.label}" + if (CodeAgent.running) " · working…" else "" }
        }
        scope.launch {
            CodeAgent.typing.collect { chunk ->
                if (chunk.startsWith(CodeAgent.FILE_MARK)) queue.addLast(Piece("\n── ${chunk.removePrefix(CodeAgent.FILE_MARK)} ──\n", FILE))
                else queue.addLast(Piece(chunk, CODE))
            }
        }
        handler.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            typeSome()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun waiting(): Int = (current?.let { it.text.length - pos } ?: 0) + queue.sumOf { it.text.length }

    private fun typeSome() {
        val now = android.os.SystemClock.elapsedRealtime()
        val blink = now - lastBlink > 500
        var budget = charsPerTick(waiting())
        // Nothing new and no blink due: leave the (possibly long) text alone.
        if (budget == 0 && !blink && (CodeAgent.running || !text.endsWith("▌"))) return
        // Drop the blinking cursor before adding text.
        if (text.endsWith("▌")) text.delete(text.length - 1, text.length)
        var typed = false
        while (budget > 0) {
            val p = current ?: queue.removeFirstOrNull()?.also { current = it; pos = 0 } ?: break
            val n = minOf(budget, p.text.length - pos)
            val start = text.length
            text.append(p.text, pos, pos + n)
            text.setSpan(ForegroundColorSpan(p.color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            pos += n
            budget -= n
            typed = true
            if (pos >= p.text.length) current = null
        }
        if (text.length > MAX_CHARS) text.delete(0, text.length - KEEP_CHARS)
        // While the team is thinking, a blinking cursor shows it's alive.
        if (blink) { cursorOn = !cursorOn; lastBlink = now }
        if (CodeAgent.running && (typed || cursorOn)) text.append("▌")
        body.text = text
        if (typed) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onClosed() {
        handler.removeCallbacks(tick)
        scope.cancel()
    }
}
