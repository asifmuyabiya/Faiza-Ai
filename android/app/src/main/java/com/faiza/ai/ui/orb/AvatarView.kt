package com.faiza.ai.ui.orb

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.faiza.ai.FaizaApp
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Where the face is in the avatar picture (0..1 of the picture's width / height). */
data class AvatarFace(
    val leftEyeX: Float, val leftEyeY: Float,
    val rightEyeX: Float, val rightEyeY: Float,
    val mouthX: Float, val mouthY: Float
)

/** The user's own avatar picture and where its eyes and mouth are. */
object AvatarStore {

    fun file(ctx: Context) = File(ctx.filesDir, "avatar.jpg")

    fun face(): AvatarFace? {
        val p = FaizaApp.instance.prefs
        val v = p.avatarFace ?: return null
        val n = v.split(',').mapNotNull { it.toFloatOrNull() }
        return if (n.size == 6) AvatarFace(n[0], n[1], n[2], n[3], n[4], n[5]) else null
    }

    fun save(face: AvatarFace) {
        FaizaApp.instance.prefs.avatarFace = with(face) { listOf(leftEyeX, leftEyeY, rightEyeX, rightEyeY, mouthX, mouthY) }
            .joinToString(",")
    }

    /** Ready to show: a picture and its face points exist. */
    fun isReady(ctx: Context) = file(ctx).exists() && face() != null

    /** Use the avatar instead of the orb (the switch is on and it's set up). */
    fun inUse(ctx: Context) = FaizaApp.instance.prefs.isAvatarMode && isReady(ctx)

    fun loadBitmap(ctx: Context, maxSide: Int = 1024): Bitmap? = runCatching {
        val f = file(ctx)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/**
 * Faiza as a living picture (owner's idea): the chosen portrait breathes, sways a little, blinks, and her mouth moves
 * with her voice while she talks. Drawn as a circle (floating bubble / home hero). One picture can't move hands or
 * turn in 3D, so the motion stays gentle and believable.
 */
class AvatarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var mode: OrbMode = OrbMode.IDLE
        set(value) { field = value; invalidate() }

    /** 0..1, how loud her voice is right now. */
    var voiceLevel: () -> Float = { 0f }

    var theme: OrbTheme = OrbTheme.VIOLET
        set(value) { field = value; invalidate() }

    private var bitmap: Bitmap? = null
    private var face: AvatarFace? = null
    private var skin = Color.rgb(240, 200, 180)
    private var lash = Color.rgb(40, 25, 30)

    private val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val m = Matrix()
    private val tmpRect = RectF()

    private val startNs = System.nanoTime()
    private var nextBlinkAt = 1.5f
    private var blinkStart = -10f
    private var mouthOpen = 0f

    /** For previews and tests: hold the mouth open this much / the eyes this closed (0..1). */
    internal var forceMouth: Float? = null
    internal var forceBlink: Float? = null

    /** Loads (or reloads) the user's picture. */
    fun reload() = show(AvatarStore.loadBitmap(context), AvatarStore.face())

    /** Shows [b] with its face points [f]. */
    fun show(b: Bitmap?, f: AvatarFace?) {
        bitmap = b
        face = f
        val f = face
        if (b != null && f != null) {
            // Eyelid colour = the skin just between the eyes; lashes = the darkest pixel along the eye line.
            val cx = ((f.leftEyeX + f.rightEyeX) / 2f * b.width).toInt().coerceIn(0, b.width - 1)
            val cy = ((f.leftEyeY + f.rightEyeY) / 2f * b.height).toInt().coerceIn(0, b.height - 1)
            skin = b.getPixel(cx, (cy + (eyeDistPx(b, f) * 0.15f).toInt()).coerceIn(0, b.height - 1))
            var dark = Color.rgb(40, 25, 30)
            var darkLum = 999
            val ex = (f.leftEyeX * b.width).toInt()
            val ey = (f.leftEyeY * b.height).toInt()
            val r = (eyeDistPx(b, f) * 0.2f).toInt().coerceAtLeast(2)
            for (dx in -r..r step 2) for (dy in -r..0 step 2) {
                val px = b.getPixel((ex + dx).coerceIn(0, b.width - 1), (ey + dy).coerceIn(0, b.height - 1))
                val lum = Color.red(px) + Color.green(px) + Color.blue(px)
                if (lum < darkLum) { darkLum = lum; dark = px }
            }
            lash = dark
            imgPaint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        syncTicking()
        invalidate()
    }

    private fun eyeDistPx(b: Bitmap, f: AvatarFace) =
        hypot((f.rightEyeX - f.leftEyeX) * b.width, (f.rightEyeY - f.leftEyeY) * b.height)

    // Frames are scheduled by hand (not every screen refresh): smooth enough, much lighter on the battery.
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            invalidate()
            val busy = mode == OrbMode.SPEAKING || mode == OrbMode.LISTENING
            val ms = when {
                PowerSave.on(context) -> if (busy) 66L else 150L
                busy -> 33L   // ~30 fps for lips and sway
                else -> 80L   // ~12 fps is plenty for breathing and blinks
            }
            postDelayed(this, ms)
        }
    }

    private fun syncTicking() {
        val want = isAttachedToWindow && visibility == VISIBLE && windowVisibility == VISIBLE && bitmap != null
        if (want && !ticking) { ticking = true; post(tick) }
        else if (!want && ticking) { ticking = false; removeCallbacks(tick) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bitmap == null && visibility == VISIBLE && !isInEditMode) runCatching { reload() }
        syncTicking()
    }

    override fun onDetachedFromWindow() {
        ticking = false
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncTicking()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncTicking()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val b = bitmap
        val f = face
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - startNs) / 1_000_000_000f
        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val radius = size / 2f - size * 0.04f

        // Soft glow ring in the theme colour; brighter while she listens / speaks.
        val glow = when (mode) {
            OrbMode.IDLE -> 0.25f
            OrbMode.SPEAKING -> 0.75f + 0.2f * sin(t * 2f * PI.toFloat() / 0.9f)
            else -> 0.55f
        }
        glowPaint.shader = RadialGradient(
            cx, cy, size / 2f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(theme.main, (glow * 200).toInt())),
            floatArrayOf(0f, 0.86f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, size / 2f, glowPaint)

        if (b == null || f == null) {
            ringPaint.color = theme.main
            ringPaint.strokeWidth = size * 0.04f
            canvas.drawCircle(cx, cy, radius, ringPaint)
            return
        }

        // Fit: centre the face (between eyes and mouth) in the circle, eyes about a third of the circle apart.
        val eyeDist = eyeDistPx(b, f)
        val faceCx = (f.leftEyeX + f.rightEyeX + f.mouthX) / 3f * b.width
        val faceCy = ((f.leftEyeY + f.rightEyeY) / 2f * b.height + f.mouthY * b.height) / 2f
        val scale = (radius * 2f * 0.34f) / eyeDist.coerceAtLeast(1f)

        // Life: breathing, idle sway, livelier head motion while talking.
        val level = if (mode == OrbMode.SPEAKING) voiceLevel().coerceIn(0f, 1f) else 0f
        mouthOpen += (level - mouthOpen) * (if (level > mouthOpen) 0.5f else 0.25f)
        forceMouth?.let { mouthOpen = it }
        val breathe = 1f + 0.012f * sin(t * 2f * PI.toFloat() / 4.2f)
        val talkSway = if (mode == OrbMode.SPEAKING) 2.2f * sin(t * 2f * PI.toFloat() / 1.4f) * (0.4f + mouthOpen) else 0f
        val angle = 1.2f * sin(t * 2f * PI.toFloat() / 5.5f) + talkSway
        val nod = if (mode == OrbMode.SPEAKING) 2.5f * sin(t * 2f * PI.toFloat() / 0.8f) * mouthOpen else 0f
        val lean = if (mode == OrbMode.LISTENING) 1.03f else 1f

        m.reset()
        m.postTranslate(-faceCx, -faceCy)
        m.postScale(scale * breathe * lean, scale * breathe * lean)
        // Turn around the chin, so it looks like the head tilting.
        val chinY = (f.mouthY * b.height - faceCy + eyeDist * 0.6f) * scale
        m.postRotate(angle, 0f, chinY)
        m.postTranslate(cx, cy + nod * size / 100f)

        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawColor(skin)
        imgPaint.shader?.setLocalMatrix(m)
        canvas.drawCircle(cx, cy, radius, imgPaint)

        // Features drawn in picture coordinates through the same matrix.
        canvas.save()
        canvas.concat(m)
        drawMouth(canvas, b, f, eyeDist)
        drawBlink(canvas, b, f, eyeDist, t)
        canvas.restore()
        canvas.restore()

        ringPaint.color = withAlpha(theme.main, 230)
        ringPaint.strokeWidth = size * 0.025f
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }

    private fun drawMouth(canvas: Canvas, b: Bitmap, f: AvatarFace, eyeDist: Float) {
        if (mouthOpen < 0.04f) return
        val mx = f.mouthX * b.width
        val my = f.mouthY * b.height
        val mw = eyeDist * (0.30f + 0.06f * mouthOpen)
        val mh = eyeDist * 0.19f * mouthOpen
        tmpRect.set(mx - mw / 2f, my - mh * 0.35f, mx + mw / 2f, my + mh * 0.65f)
        mouthPaint.color = Color.rgb(92, 30, 42)
        canvas.drawOval(tmpRect, mouthPaint)
        // A hint of tongue at the bottom when it's wide open.
        if (mouthOpen > 0.35f) {
            mouthPaint.color = Color.rgb(196, 92, 104)
            tmpRect.set(mx - mw * 0.28f, my + mh * 0.15f, mx + mw * 0.28f, my + mh * 0.6f)
            canvas.drawOval(tmpRect, mouthPaint)
        }
    }

    private fun drawBlink(canvas: Canvas, b: Bitmap, f: AvatarFace, eyeDist: Float, t: Float) {
        if (t >= nextBlinkAt && t - blinkStart > 0.3f) {
            blinkStart = t
            // Natural rhythm: every 2.5–6 s, now and then a quick double blink.
            nextBlinkAt = t + if (Random.nextFloat() < 0.15f) 0.35f else 2.5f + Random.nextFloat() * 3.5f
        }
        val p = t - blinkStart
        val closed = forceBlink ?: when {
            p < 0f || p > 0.24f -> return
            p < 0.08f -> p / 0.08f          // closing
            p < 0.12f -> 1f                  // shut
            else -> 1f - (p - 0.12f) / 0.12f // opening
        }
        val ew = eyeDist * 0.44f
        val eh = eyeDist * 0.30f
        lidPaint.color = skin
        lashPaint.color = lash
        lashPaint.strokeWidth = eyeDist * 0.035f
        for ((ex, ey) in listOf(f.leftEyeX * b.width to f.leftEyeY * b.height, f.rightEyeX * b.width to f.rightEyeY * b.height)) {
            val top = ey - eh / 2f
            val lidBottom = top + eh * closed
            tmpRect.set(ex - ew / 2f, top - eh * 0.15f, ex + ew / 2f, lidBottom)
            canvas.drawRoundRect(tmpRect, ew / 2f, eh / 2f, lidPaint)
            // The lash line sits on the lid's lower edge, curving down a little like a closed eye.
            val path = Path().apply {
                moveTo(ex - ew / 2f, lidBottom - eh * 0.08f)
                quadTo(ex, lidBottom + eh * 0.12f * closed, ex + ew / 2f, lidBottom - eh * 0.08f)
            }
            lashPaint.style = Paint.Style.STROKE
            canvas.drawPath(path, lashPaint)
        }
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < 0.001f
}
