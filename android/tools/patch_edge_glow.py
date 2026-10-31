base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
svc = base + r'\java\com\faiza\ai\service\FloatingOrbService.kt'
s = open(svc, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep("""        if (edgeGlow != null) return
        val glow = EdgeGlowView(this).apply { setTheme(OrbTheme.from(prefs.orbTheme)) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.alpha = passThroughAlpha()
        if (runCatching { windowManager.addView(glow, lp) }.isSuccess) edgeGlow = glow
    }""", """        glowRemoveJob?.cancel()
        edgeGlow?.let {
            it.setRgb(prefs.isEdgeGlowRgb)
            return
        }
        val glow = EdgeGlowView(this).apply {
            setTheme(OrbTheme.from(prefs.orbTheme))
            setRgb(prefs.isEdgeGlowRgb)
            voiceLevel = { live?.voiceLevel() ?: 0f }
        }
        if (runCatching { windowManager.addView(glow, edgeGlowParams()) }.isSuccess) edgeGlow = glow
    }

    /**
     * The whole physical screen, edge to edge on every phone: behind the status and navigation bars and into the
     * camera cut-out (by default Android shrinks overlays to the space between the bars, leaving gaps top and bottom).
     */
    @Suppress("DEPRECATION")
    private fun edgeGlowParams(): WindowManager.LayoutParams {
        val (w, h) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        return WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = passThroughAlpha()
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private var glowRemoveJob: kotlinx.coroutines.Job? = null

    /** Screen rotated: the glow follows the new screen size. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeGlow?.let { g -> runCatching { windowManager.updateViewLayout(g, edgeGlowParams()) } }
    }""")

rep("""        if (mode == OrbMode.IDLE) removeEdgeGlow() else ensureEdgeGlow()
        edgeGlow?.setMode(mode)""", """        if (mode == OrbMode.IDLE) {
            // Fade out, then take the window away completely: nothing covers the screen while she's idle.
            edgeGlow?.setMode(mode)
            glowRemoveJob?.cancel()
            glowRemoveJob = scope.launch {
                delay(700)
                removeEdgeGlow()
            }
        } else {
            ensureEdgeGlow()
            edgeGlow?.setMode(mode)
        }""")
open(svc, 'w', encoding='utf-8').write(s)

# Live session: how loud her voice is right now (0..1) for the glow.
p = base + r'\java\com\faiza\ai\ai\live\GeminiLiveSession.kt'
s = open(p, encoding='utf-8').read()
rep("""    /** Loudest thing Faiza played in the window that could be echoing into the mic right now. */""",
    """    /** How loud her voice is at this moment, 0..1 (drives the edge glow while she speaks). */
    fun voiceLevel(): Float {
        val now = SystemClock.elapsedRealtime()
        val rms = synchronized(outSegments) { outSegments.firstOrNull { now in it.start..it.end }?.rms ?: 0.0 }
        return (rms / 5_000.0).toFloat().coerceIn(0f, 1f)
    }

    /** Loudest thing Faiza played in the window that could be echoing into the mic right now. */""")
open(p, 'w', encoding='utf-8').write(s)

# Prefs: RGB on by default.
p = base + r'\java\com\faiza\ai\util\PrefsManager.kt'
s = open(p, encoding='utf-8').read()
rep("""    // ====== Audio ======""", """    /** Edge glow in all colours (RGB) instead of the theme colour. */
    var isEdgeGlowRgb: Boolean
        get() = prefs.getBoolean("edge_glow_rgb", true)
        set(value) = prefs.edit().putBoolean("edge_glow_rgb", value).apply()

    // ====== Audio ======""")
open(p, 'w', encoding='utf-8').write(s)

# Orb settings: "RGB colours" switch under Edge glow.
p = base + r'\res\layout\activity_orb_settings.xml'
s = open(p, encoding='utf-8').read()
rep("""                            android:text="Soft glow while she's awake" />""",
    """                            android:text="Glow around the screen while she's awake" />""")
anchor = """                    <com.google.android.material.switchmaterial.SwitchMaterial
                        android:id="@+id/switchEdgeGlow"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
"""
rep(anchor, anchor + """
                <View style="@style/Widget.Faiza.Divider" />

                <LinearLayout style="@style/Widget.Faiza.Row">

                    <ImageView
                        style="@style/Widget.Faiza.IconTile"
                        android:background="@drawable/bg_tile_pink"
                        android:src="@drawable/ic_palette"
                        app:tint="@color/tile_pink" />

                    <LinearLayout style="@style/Widget.Faiza.RowText">

                        <TextView
                            style="@style/Widget.Faiza.RowTitle"
                            android:text="RGB colours" />

                        <TextView
                            style="@style/Widget.Faiza.RowSubtitle"
                            android:text="All colours flow round the edge · off = theme colour" />
                    </LinearLayout>

                    <com.google.android.material.switchmaterial.SwitchMaterial
                        android:id="@+id/switchEdgeRgb"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content" />
                </LinearLayout>
""")
open(p, 'w', encoding='utf-8').write(s)

p = base + r'\java\com\faiza\ai\ui\OrbSettingsActivity.kt'
s = open(p, encoding='utf-8').read()
rep("""        bind(R.id.switchEdgeGlow, { prefs.isOrbEdgeGlow }) { prefs.isOrbEdgeGlow = it }""",
    """        bind(R.id.switchEdgeGlow, { prefs.isOrbEdgeGlow }) { prefs.isOrbEdgeGlow = it }
        bind(R.id.switchEdgeRgb, { prefs.isEdgeGlowRgb }) { prefs.isEdgeGlowRgb = it }""")
open(p, 'w', encoding='utf-8').write(s)
print("ok")
