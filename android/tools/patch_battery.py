root = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
base = root + r'\java\com\faiza\ai'
s = ''


def load(p):
    global s
    s = open(p, encoding='utf-8').read()


def save(p):
    open(p, 'w', encoding='utf-8').write(s)


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# ---- 1. Accessibility: only the events Faiza actually uses ----
import glob
for p in glob.glob(root + r'\res\xml\*.xml'):
    load(p)
    if 'accessibilityEventTypes' in s:
        rep('android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged|typeViewFocused"',
            'android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged"')
        rep('''<!-- No flagRequestTouchExplorationMode: it turns on "explore by touch" and breaks normal tapping. -->''',
            '''<!-- No flagRequestTouchExplorationMode: it turns on "explore by touch" and breaks normal tapping.
     Only window changes are requested: every-text-change events kept the phone busy and drained the battery,
     and the screen is read on demand anyway. -->''')
        save(p)

# ---- 2. Avatar: animate only while visible, ~30 fps talking / ~12 fps resting, slower in Battery Saver ----
p = base + r'\ui\orb\AvatarView.kt'
load(p)
rep('''    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bitmap == null && visibility == VISIBLE && !isInEditMode) runCatching { reload() }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }''', '''    // Frames are scheduled by hand (not every screen refresh): smooth enough, much lighter on the battery.
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
    }''')
rep('''            imgPaint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        invalidate()''', '''            imgPaint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        syncTicking()
        invalidate()''')
rep('''    private var animator: ValueAnimator? = null
''', '')
save(p)

# ---- 3. Edge glow: ~30 fps instead of every refresh, fewer layers; calmer in Battery Saver ----
p = base + r'\ui\orb\EdgeGlowView.kt'
load(p)
rep('''            addUpdateListener { invalidate() }''', '''            addUpdateListener {
                // ~30 fps (≈15 in Battery Saver): the colours move slowly, so this looks the same and costs half.
                val now = System.nanoTime()
                val gap = if (PowerSave.on(context)) 66_000_000L else 33_000_000L
                if (now - lastFrameNs >= gap) { lastFrameNs = now; invalidate() }
            }''')
rep('''    private var mode = OrbMode.IDLE''', '''    private var mode = OrbMode.IDLE
    private var lastFrameNs = 0L''')
rep('''        val passes = floatArrayOf(9f, 7f, 5.2f, 3.8f, 2.6f, 1.7f, 1f)
        val alphas = floatArrayOf(0.05f, 0.07f, 0.10f, 0.14f, 0.22f, 0.45f, 1f)''', '''        val passes = floatArrayOf(8f, 5f, 3f, 1.7f, 1f)
        val alphas = floatArrayOf(0.06f, 0.10f, 0.18f, 0.42f, 1f)''')
save(p)

# ---- 4. Wake-word fallback: back off instead of restarting every half second ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''            override fun onResults(results: Bundle?) {
                if (Phrases.isWakeWord(firstResult(results), customWakeNames())) onWakeWordDetected()
                else restartWakeWordSoon(0)
            }''', '''            override fun onResults(results: Bundle?) {
                fallbackErrors = 0
                if (Phrases.isWakeWord(firstResult(results), customWakeNames())) onWakeWordDetected()
                else restartWakeWordSoon(300)
            }''')
rep('''            override fun onError(error: Int) = restartWakeWordSoon(500)
        })''', '''            // Errors (no speech, busy, no network) come back-to-back: wait longer each time, up to 20 s.
            override fun onError(error: Int) {
                fallbackErrors = (fallbackErrors + 1).coerceAtMost(6)
                restartWakeWordSoon(minOf(20_000L, 500L shl fallbackErrors))
            }
        })''')
rep('''    private fun restartWakeWordSoon(delayMs: Long) {''', '''    /** Back-to-back errors of the fallback recogniser (reset when it hears something). */
    private var fallbackErrors = 0

    private fun restartWakeWordSoon(delayMs: Long) {''')
save(p)

# ---- PowerSave helper ----
open(base + r'\ui\orb\PowerSave.kt', 'w', encoding='utf-8').write('''package com.faiza.ai.ui.orb

import android.content.Context
import android.os.PowerManager

/** Is the phone in Battery Saver? Checked at most once a second (animations ask every frame). */
object PowerSave {
    private var checkedAt = 0L
    private var cached = false

    fun on(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - checkedAt > 1_000) {
            checkedAt = now
            cached = runCatching { ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }.getOrDefault(false)
        }
        return cached
    }
}
''')
print("ok")
