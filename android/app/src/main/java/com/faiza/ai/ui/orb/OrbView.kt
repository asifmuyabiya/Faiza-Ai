package com.faiza.ai.ui.orb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Faiza's orb, drawn in code so every design works in every colour and size.
 * It only animates while she is awake — a sleeping orb costs no battery.
 */
class OrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var design: OrbDesign = OrbDesign.SPHERE
        set(value) { field = value; invalidate() }

    var theme: OrbTheme = OrbTheme.VIOLET
        set(value) { field = value; invalidate() }

    var mode: OrbMode = OrbMode.IDLE
        set(value) {
            if (field == value) return
            field = value
            syncAnimator()
            invalidate()
        }

    private var icon: Drawable? = null
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3_200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private class Spark(val angle: Float, val radius: Float, val size: Float, val speed: Float, val alpha: Float, val light: Boolean)

    private val sparks: List<Spark> = Random(7).let { r ->
        List(44) {
            Spark(
                angle = r.nextFloat() * 6.2832f,
                radius = 0.45f + r.nextFloat() * 0.95f,
                size = 0.010f + r.nextFloat() * 0.022f,
                speed = (0.35f + r.nextFloat() * 1.1f) * (if (r.nextBoolean()) 1f else -1f),
                alpha = 0.35f + r.nextFloat() * 0.6f,
                light = r.nextBoolean()
            )
        }
    }

    fun setIconRes(@DrawableRes res: Int) {
        icon = ContextCompat.getDrawable(context, res)?.mutate()?.also { it.setTint(Color.WHITE) }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val def = (76 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(def, widthMeasureSpec), resolveSize(def, heightMeasureSpec))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncAnimator()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncAnimator()
    }

    private fun syncAnimator() {
        val shouldRun = isAttachedToWindow && isShown && mode != OrbMode.IDLE && mode != OrbMode.MIC_BUSY
        if (shouldRun && !animator.isStarted) animator.start()
        if (!shouldRun && animator.isStarted) {
            animator.cancel()
            phase = 0f
        }
    }

    // ===== Drawing =====

    private fun activity(): Float = when (mode) {
        OrbMode.IDLE -> 0.12f
        OrbMode.MIC_BUSY -> 0.08f
        OrbMode.CONNECTING -> 0.4f
        OrbMode.LISTENING -> 0.5f
        OrbMode.WORKING -> 0.7f
        OrbMode.SPEAKING -> 1f
    }

    private fun a(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val t = phase * 2f * PI.toFloat()
        val amp = activity()
        val breath = when (mode) {
            OrbMode.SPEAKING -> 1f + 0.09f * sin(t * 4f)
            OrbMode.LISTENING -> 1f + 0.05f * sin(t * 2f)
            OrbMode.WORKING -> 1f + 0.03f * sin(t * 3f)
            OrbMode.CONNECTING -> 1f + 0.03f * sin(t * 2f)
            else -> 1f
        }
        val base = s * 0.29f * breath
        val dim = if (mode == OrbMode.MIC_BUSY) 0.55f else 1f

        drawGlow(canvas, cx, cy, s * 0.5f, amp * dim)
        when (design) {
            OrbDesign.SPHERE -> drawSphere(canvas, cx, cy, base, s)
            OrbDesign.PULSE -> drawPulse(canvas, cx, cy, base, s, amp)
            OrbDesign.LIQUID -> drawLiquid(canvas, cx, cy, base, t, amp)
            OrbDesign.RINGS -> drawRings(canvas, cx, cy, base, s, amp)
            OrbDesign.PARTICLES -> drawParticles(canvas, cx, cy, base, s, t, amp)
            OrbDesign.WAVEFORM -> drawWaveform(canvas, cx, cy, base, t, amp)
            OrbDesign.HEXAGON -> drawHexagon(canvas, cx, cy, base, s)
            OrbDesign.MINIMAL -> drawMinimal(canvas, cx, cy, base, s)
        }
        drawModeRing(canvas, cx, cy, base, s)
        if (design != OrbDesign.WAVEFORM && design != OrbDesign.PARTICLES) drawIcon(canvas, cx, cy, base)
    }

    private fun drawGlow(c: Canvas, cx: Float, cy: Float, radius: Float, amp: Float) {
        paint.reset(); paint.isAntiAlias = true
        val strength = 0.30f + 0.55f * amp
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(a(theme.main, strength), a(theme.main, strength * 0.3f), a(theme.main, 0f)),
            floatArrayOf(0.30f, 0.68f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }

    private fun sphereShader(cx: Float, cy: Float, r: Float): RadialGradient =
        RadialGradient(
            cx - r * 0.30f, cy - r * 0.38f, r * 1.55f,
            intArrayOf(theme.light, theme.main, theme.dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )

    private fun ring(c: Canvas, cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        c.drawCircle(cx, cy, r, paint)
    }

    private fun drawSphere(c: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.shader = sphereShader(cx, cy, r)
        c.drawCircle(cx, cy, r, paint)
        paint.shader = null
        ring(c, cx, cy, r, s * 0.012f, a(theme.light, 0.45f))
    }

    private fun drawPulse(c: Canvas, cx: Float, cy: Float, base: Float, s: Float, amp: Float) {
        val core = base * 0.78f
        if (mode == OrbMode.IDLE || mode == OrbMode.MIC_BUSY) {
            ring(c, cx, cy, base * 1.08f, s * 0.012f, a(theme.light, 0.35f))
        } else {
            for (i in 0 until 3) {
                val p = (phase * 1.5f + i / 3f) % 1f
                val r = core + p * s * 0.2f * (0.5f + amp)
                ring(c, cx, cy, r, s * 0.014f * (1f - p * 0.5f), a(theme.light, (1f - p) * 0.6f))
            }
        }
        paint.reset(); paint.isAntiAlias = true
        paint.shader = RadialGradient(cx, cy, core, intArrayOf(theme.light, theme.main, theme.dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, core, paint)
        paint.shader = null
    }

    private fun drawLiquid(c: Canvas, cx: Float, cy: Float, base: Float, t: Float, amp: Float) {
        val n = 8
        val k = 0.3f + amp
        val px = FloatArray(n)
        val py = FloatArray(n)
        for (i in 0 until n) {
            val ang = i * 2f * PI.toFloat() / n
            val r = base * (1f + 0.10f * k * sin(t * 2f + i * 1.7f) + 0.06f * k * sin(t * 3f - i * 2.3f))
            px[i] = cx + cos(ang) * r
            py[i] = cy + sin(ang) * r
        }
        path.reset()
        path.moveTo((px[n - 1] + px[0]) / 2f, (py[n - 1] + py[0]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            path.quadTo(px[i], py[i], (px[i] + px[j]) / 2f, (py[i] + py[j]) / 2f)
        }
        path.close()
        paint.reset(); paint.isAntiAlias = true
        paint.shader = sphereShader(cx, cy, base * 1.05f)
        c.drawPath(path, paint)
        paint.shader = null
        // soft highlight
        paint.color = a(Color.WHITE, 0.30f)
        rect.set(cx - base * 0.55f, cy - base * 0.62f, cx - base * 0.05f, cy - base * 0.28f)
        c.drawOval(rect, paint)
    }

    private fun drawRings(c: Canvas, cx: Float, cy: Float, base: Float, s: Float, amp: Float) {
        val coreR = base * 0.55f
        paint.reset(); paint.isAntiAlias = true
        paint.shader = sphereShader(cx, cy, coreR)
        c.drawCircle(cx, cy, coreR, paint)
        paint.shader = null

        val speeds = floatArrayOf(1f, -1.6f, 2.2f)
        val sweeps = floatArrayOf(230f, 170f, 120f)
        val radii = floatArrayOf(0.98f, 1.18f, 1.38f)
        val move = 0.25f + amp
        for (i in 0 until 3) {
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = s * (0.026f - i * 0.004f)
            paint.color = a(if (i == 1) theme.main else theme.light, 0.9f - i * 0.15f)
            val r = base * radii[i]
            rect.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(rect, 360f * phase * speeds[i] * move + i * 70f, sweeps[i], false, paint)
        }
    }

    private fun drawParticles(c: Canvas, cx: Float, cy: Float, base: Float, s: Float, t: Float, amp: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.shader = RadialGradient(
            cx, cy, base * 0.6f,
            intArrayOf(a(theme.light, 0.95f), a(theme.main, 0.6f), a(theme.main, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, base * 0.6f, paint)
        paint.shader = null
        val move = 0.2f + amp
        for (p in sparks) {
            val ang = p.angle + t * p.speed * move * 0.5f
            val r = base * (0.5f + (p.radius - 0.45f) * 1.05f) * (1f + 0.08f * sin(t * 2f + p.angle))
            paint.color = a(if (p.light) theme.light else theme.main, p.alpha)
            c.drawCircle(cx + cos(ang) * r, cy + sin(ang) * r, s * p.size, paint)
        }
    }

    private fun drawWaveform(c: Canvas, cx: Float, cy: Float, base: Float, t: Float, amp: Float) {
        paint.reset(); paint.isAntiAlias = true
        paint.shader = sphereShader(cx, cy, base * 1.05f)
        c.drawCircle(cx, cy, base * 1.05f, paint)
        paint.shader = null
        val bars = 7
        val w = base * 0.15f
        val gap = base * 0.10f
        val total = bars * w + (bars - 1) * gap
        paint.color = a(Color.WHITE, 0.95f)
        for (i in 0 until bars) {
            val centre = 1f - abs(i - 3) * 0.12f
            val live = if (mode == OrbMode.IDLE || mode == OrbMode.MIC_BUSY) 0.12f else abs(sin(t * 2f + i * 0.85f))
            val h = base * (0.22f + 0.95f * amp * live) * centre
            val x = cx - total / 2f + i * (w + gap)
            rect.set(x, cy - h / 2f, x + w, cy + h / 2f)
            c.drawRoundRect(rect, w / 2f, w / 2f, paint)
        }
    }

    private fun hexagon(cx: Float, cy: Float, r: Float, rotation: Float): Path {
        val p = Path()
        for (i in 0 until 6) {
            val ang = rotation + i * PI.toFloat() / 3f
            val x = cx + cos(ang) * r
            val y = cy + sin(ang) * r
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun drawHexagon(c: Canvas, cx: Float, cy: Float, base: Float, s: Float) {
        val spin = phase * 2f * PI.toFloat() * (if (mode == OrbMode.SPEAKING) 1.5f else 0.6f)
        paint.reset(); paint.isAntiAlias = true
        paint.shader = RadialGradient(cx, cy, base * 1.1f, intArrayOf(a(theme.main, 0.55f), a(theme.dark, 0.95f)), null, Shader.TileMode.CLAMP)
        c.drawPath(hexagon(cx, cy, base * 1.05f, -PI.toFloat() / 2f), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.018f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = a(theme.light, 0.9f)
        c.drawPath(hexagon(cx, cy, base * 1.05f, -PI.toFloat() / 2f), paint)
        paint.strokeWidth = s * 0.012f
        paint.color = a(theme.light, 0.6f)
        c.drawPath(hexagon(cx, cy, base * 0.68f, spin), paint)
        paint.style = Paint.Style.FILL
        paint.color = a(theme.main, 0.45f)
        c.drawPath(hexagon(cx, cy, base * 0.48f, -spin), paint)
    }

    private fun drawMinimal(c: Canvas, cx: Float, cy: Float, base: Float, s: Float) {
        val r = base * 0.62f
        paint.reset(); paint.isAntiAlias = true
        paint.shader = sphereShader(cx, cy, r)
        c.drawCircle(cx, cy, r, paint)
        paint.shader = null
        if (mode != OrbMode.IDLE && mode != OrbMode.MIC_BUSY) ring(c, cx, cy, r * 1.35f, s * 0.01f, a(theme.light, 0.55f))
    }

    /** Spinner for connecting/working; a red ring when another app has the microphone. */
    private fun drawModeRing(c: Canvas, cx: Float, cy: Float, base: Float, s: Float) {
        when (mode) {
            OrbMode.CONNECTING, OrbMode.WORKING -> {
                paint.reset(); paint.isAntiAlias = true
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = s * 0.024f
                paint.color = a(theme.light, 0.95f)
                val r = base * 1.28f
                rect.set(cx - r, cy - r, cx + r, cy + r)
                c.drawArc(rect, phase * 360f * 2f, 72f, false, paint)
            }
            OrbMode.MIC_BUSY -> ring(c, cx, cy, base * 1.22f, s * 0.02f, a(Color.parseColor("#F87171"), 0.95f))
            else -> Unit
        }
    }

    private fun drawIcon(c: Canvas, cx: Float, cy: Float, base: Float) {
        val d = icon ?: return
        val half = base * (if (design == OrbDesign.MINIMAL) 0.26f else 0.36f)
        d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        d.draw(c)
    }
}
