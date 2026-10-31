package com.faiza.ai.ui.orb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * The glow around the screen edges while Faiza is awake (video 27:13). RGB mode (owner's wish): all the colours
 * flow smoothly round the edge — no blinking — and while she speaks the glow gently swells with her voice.
 * It follows the phone's own rounded corners, so it fits any screen edge to edge.
 */
class EdgeGlowView(context: Context) : View(context) {

    companion object {
        private val RGB = intArrayOf(
            Color.rgb(255, 45, 85), Color.rgb(255, 149, 0), Color.rgb(255, 214, 10), Color.rgb(52, 199, 89),
            Color.rgb(0, 199, 190), Color.rgb(10, 132, 255), Color.rgb(94, 92, 230), Color.rgb(191, 90, 242),
            Color.rgb(255, 45, 85)
        )
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private var shader: SweepGradient? = null

    private var rgb = true
    private var themeColor = OrbTheme.VIOLET.main
    private var cornerRadius = 0f

    private var mode = OrbMode.IDLE
    private var lastFrameNs = 0L
    private var animator: ValueAnimator? = null
    private var startedAt = 0L

    /** 0..1, how loud her voice is right now (set by the service while she speaks). */
    var voiceLevel: () -> Float = { 0f }
    private var smoothLevel = 0f

    // Animated "brightness" so mode changes fade instead of jumping.
    private var intensity = 0f
    private var targetIntensity = 0f

    fun setTheme(theme: OrbTheme) {
        themeColor = theme.main
        shader = null
        invalidate()
    }

    fun setRgb(on: Boolean) {
        if (rgb == on) return
        rgb = on
        shader = null
        invalidate()
    }

    fun setMode(m: OrbMode) {
        mode = m
        targetIntensity = when (m) {
            OrbMode.IDLE -> 0f
            OrbMode.MIC_BUSY -> 0.45f
            OrbMode.LISTENING -> 0.85f
            OrbMode.SPEAKING -> 0.95f
            OrbMode.WORKING, OrbMode.CONNECTING -> 0.75f
        }
        if (animator == null) start()
    }

    private fun start() {
        startedAt = System.nanoTime()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                // ~30 fps (≈15 in Battery Saver): the colours move slowly, so this looks the same and costs half.
                val now = System.nanoTime()
                val gap = if (PowerSave.on(context)) 66_000_000L else 33_000_000L
                if (now - lastFrameNs >= gap) { lastFrameNs = now; invalidate() }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // Match the phone's real screen corners.
        if (Build.VERSION.SDK_INT >= 31) {
            val r = listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT)
                .mapNotNull { insets.getRoundedCorner(it)?.radius }.maxOrNull()
            if (r != null && r > 0) cornerRadius = r.toFloat()
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = null
        if (cornerRadius == 0f) cornerRadius = 28f * density // most phones; replaced by the real value on Android 12+
    }

    private fun shaderFor(cx: Float, cy: Float): SweepGradient =
        shader ?: (if (rgb) SweepGradient(cx, cy, RGB, null)
        else {
            val c = themeColor
            val soft = Color.argb(255, (Color.red(c) + 255) / 2, (Color.green(c) + 255) / 2, (Color.blue(c) + 255) / 2)
            SweepGradient(cx, cy, intArrayOf(c, soft, c, soft, c), null)
        }).also { shader = it }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - startedAt) / 1_000_000_000f

        // Ease brightness towards the mode's level (~0.4 s) — smooth, never blinking.
        intensity += (targetIntensity - intensity) * 0.08f
        // Her voice: quick to rise, slow to fall, and only a gentle swell.
        val lv = if (mode == OrbMode.SPEAKING) voiceLevel().coerceIn(0f, 1f) else 0f
        smoothLevel += (lv - smoothLevel) * (if (lv > smoothLevel) 0.35f else 0.08f)
        val breathe = when (mode) {
            OrbMode.SPEAKING -> 0.06f * sin(t * 2f * PI.toFloat() / 1.6f) + 0.22f * smoothLevel
            OrbMode.LISTENING -> 0.05f * sin(t * 2f * PI.toFloat() / 3.2f)
            else -> 0f
        }
        if (intensity < 0.01f) return

        // Colours flow round the edge: one full turn every ~6 s (faster while she works).
        val turnSeconds = if (mode == OrbMode.WORKING || mode == OrbMode.CONNECTING) 3.2f else 6f
        val angle = (t / turnSeconds * 360f) % 360f
        val cx = w / 2f
        val cy = h / 2f
        val sh = shaderFor(cx, cy)
        shaderMatrix.setRotate(angle, cx, cy)
        sh.setLocalMatrix(shaderMatrix)
        paint.shader = sh

        val base = 5f * density * (1f + breathe)
        // Soft outer glow: wide faint strokes, then the bright core line hugging the screen edge.
        val passes = floatArrayOf(8f, 5f, 3f, 1.7f, 1f)
        val alphas = floatArrayOf(0.06f, 0.10f, 0.18f, 0.42f, 1f)
        for (i in passes.indices) {
            val sw = base * passes[i]
            paint.strokeWidth = sw
            paint.alpha = (255 * intensity * alphas[i]).toInt().coerceIn(0, 255)
            // The stroke is centred on the path: keep its outer edge on the screen edge.
            val inset = base * 0.5f
            rect.set(inset, inset, w - inset, h - inset)
            val r = (cornerRadius - inset).coerceAtLeast(0f)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        paint.shader = null
    }
}
