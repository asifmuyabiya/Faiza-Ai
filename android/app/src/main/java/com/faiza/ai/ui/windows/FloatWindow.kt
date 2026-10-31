package com.faiza.ai.ui.windows

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.faiza.ai.R
import kotlin.math.abs

/**
 * A small window that floats over every app (owner: "Faiza's own mini apps instead of opening phone apps").
 * Title bar with drag, size (small/large), minimise-to-bubble and close. Subclasses fill [content].
 */
abstract class FloatWindow(protected val ctx: Context, val kind: WindowKind) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    protected val dp = ctx.resources.displayMetrics.density
    protected fun px(v: Int) = (v * dp).toInt()

    private lateinit var frame: LinearLayout
    private lateinit var bubble: TextView
    protected lateinit var content: FrameLayout
    private lateinit var lp: WindowManager.LayoutParams
    private var large = false
    var minimized = false
        private set
    var shown = false
        private set

    /** Size in dp: (width, height) for small and large. */
    open val smallSize = 260 to 300
    open val largeSize = 340 to 460

    /** Needs the keyboard (notes, browser address): the window can take focus while touched. */
    open val wantsKeyboard = false

    protected abstract fun buildContent(container: FrameLayout)
    protected open fun onClosed() {}

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (wantsKeyboard) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = px(24) + kind.ordinal * px(14)
        y = px(140) + kind.ordinal * px(22)
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    fun show() {
        if (shown) { restore(); return }
        frame = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(ctx.getColor(R.color.surface))
                setStroke(px(1), ctx.getColor(R.color.primary_dark))
            }
            elevation = px(10).toFloat()
            clipToOutline = true
        }
        frame.addView(titleBar())
        content = FrameLayout(ctx)
        frame.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        buildContent(content)
        val (w, h) = smallSize
        lp = params(px(w), px(h))
        runCatching { wm.addView(frame, lp) }.onFailure { return }
        shown = true
        minimized = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun titleBar(): View {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(6), px(6), px(6))
            setBackgroundColor(ctx.getColor(R.color.surface_2))
        }
        bar.addView(TextView(ctx).apply {
            text = "${kind.emoji}  ${kind.title}"
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ctx.getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fun btn(label: String, action: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ctx.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(px(34), px(30))
            setOnClickListener { action() }
        }
        bar.addView(btn("—") { minimize() })
        bar.addView(btn("⤢") { toggleSize() })
        bar.addView(btn("✕") { close() })
        dragWith(bar) { lp }
        return bar
    }

    /** Dragging [handle] moves the window (or the bubble). */
    @SuppressLint("ClickableViewAccessibility")
    private fun dragWith(handle: View, onTap: (() -> Unit)? = null, params: () -> WindowManager.LayoutParams) {
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        handle.setOnTouchListener { v, e ->
            val p = params()
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    p.x = sx + dx.toInt(); p.y = sy + dy.toInt()
                    runCatching { wm.updateViewLayout(if (minimized) bubble else frame, p) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) onTap?.invoke() ?: v.performClick(); true }
                else -> false
            }
        }
    }

    fun toggleSize() {
        if (!shown || minimized) return
        large = !large
        val (w, h) = if (large) largeSize else smallSize
        lp.width = px(w); lp.height = px(h)
        runCatching { wm.updateViewLayout(frame, lp) }
    }

    /** Becomes a small round bubble with the window's emoji; tap it to open again. */
    fun minimize() {
        if (!shown || minimized) return
        runCatching { wm.removeView(frame) }
        bubble = TextView(ctx).apply {
            text = kind.emoji
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ctx.getColor(R.color.surface_2))
                setStroke(px(2), ctx.getColor(R.color.primary))
            }
            elevation = px(8).toFloat()
        }
        val bp = params(px(52), px(52)).apply { x = lp.x; y = lp.y; flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE }
        dragWith(bubble, onTap = { restore() }) { bp }
        runCatching { wm.addView(bubble, bp) }
        minimized = true
    }

    fun restore() {
        if (!shown || !minimized) return
        runCatching { wm.removeView(bubble) }
        runCatching { wm.addView(frame, lp) }
        minimized = false
    }

    fun close() {
        if (!shown) return
        runCatching { wm.removeView(if (minimized) bubble else frame) }
        shown = false
        minimized = false
        onClosed()
        WindowCenter.forget(kind)
    }
}

/** The mini apps. */
enum class WindowKind(val emoji: String, val title: String) {
    CAMERA("📷", "Camera"), CALCULATOR("🧮", "Calculator"), BROWSER("🌐", "Research"), NOTES("📝", "Notes"),
    TIMER("⏱️", "Timer"), TASKS("🧾", "Tasks"), TRANSLATE("🌍", "Translate"), CARD("🌤️", "Info"), MUSIC("🎵", "Music"),
    CODE("💻", "Code");

    companion object {
        fun from(s: String?): WindowKind? = values().firstOrNull {
            it.name.equals(s?.trim(), true) || it.title.equals(s?.trim(), true)
        } ?: when (s?.trim()?.lowercase()) {
            "research", "web", "google" -> BROWSER
            "calc" -> CALCULATOR
            "note" -> NOTES
            "plan", "progress" -> TASKS
            "weather", "news", "info" -> CARD
            "player", "song" -> MUSIC
            "coding", "terminal", "code studio" -> CODE
            else -> null
        }
    }
}
