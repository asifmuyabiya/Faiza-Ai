p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\service\FloatingOrbService.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# 1. Every "pass-through" overlay must be at most the system's max obscuring opacity, or Android 12+
#    silently drops ALL touches under it (the screen "freezes" while Faiza runs).
rep("""        if (runCatching { windowManager.addView(glow, lp) }.isSuccess) edgeGlow = glow""",
    """        lp.alpha = passThroughAlpha()
        if (runCatching { windowManager.addView(glow, lp) }.isSuccess) edgeGlow = glow""")
rep("""            if (runCatching { windowManager.addView(p, lp) }.isFailure) return
            recPill = p""", """            lp.alpha = passThroughAlpha()
            if (runCatching { windowManager.addView(p, lp) }.isFailure) return
            recPill = p""")
rep("""            if (runCatching { windowManager.addView(v, lp) }.isFailure) return
            macroPill = v""", """            lp.alpha = passThroughAlpha()
            if (runCatching { windowManager.addView(v, lp) }.isFailure) return
            macroPill = v""")

# 2. No glow window at all while idle: nothing covers the screen when she isn't listening/speaking.
rep("""        ensureEdgeGlow()
        edgeGlow?.setMode(mode)""", """        if (mode == OrbMode.IDLE) removeEdgeGlow() else ensureEdgeGlow()
        edgeGlow?.setMode(mode)""")

rep("""    private fun ensureEdgeGlow() {""", """    /**
     * Android 12+ blocks every touch that passes through another app's window unless that window is see-through
     * enough ("untrusted touch occlusion"). Our pass-through overlays stay just under that limit.
     */
    private fun passThroughAlpha(): Float {
        val max = if (android.os.Build.VERSION.SDK_INT >= 31)
            runCatching { getSystemService(android.hardware.input.InputManager::class.java).maximumObscuringOpacityForTouch }
                .getOrDefault(0.8f)
        else 1f
        return (max - 0.01f).coerceIn(0.1f, 1f)
    }

    private fun ensureEdgeGlow() {""")
open(p, 'w', encoding='utf-8').write(s)
print("ok")
