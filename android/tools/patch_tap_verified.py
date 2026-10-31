base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'

p = base + r'\service\FaizaAccessibilityService.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep('''    suspend fun longPressElement(e: ScreenElement): Boolean {''', '''    enum class TapResult { CHANGED, NO_CHANGE, FAILED }

    /**
     * Taps [e] and checks that the screen really changed. Prefers the element itself (or a small clickable parent);
     * on websites (Chrome WebView) and big containers it taps with a "finger" at the element's centre, because a
     * click on the whole page reports success while doing nothing. If nothing changed it tries the other way once.
     */
    suspend fun tapVerified(e: ScreenElement): TapResult {
        val before = screenSignature()
        val target = nodeAt(e)?.let { clickTarget(it, e) }
        val usedClick = target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!usedClick && !tap(e.bounds.exactCenterX(), e.bounds.exactCenterY())) return TapResult.FAILED
        if (waitForChange(before)) return TapResult.CHANGED
        // Nothing happened: try the other way once.
        if (usedClick) tap(e.bounds.exactCenterX(), e.bounds.exactCenterY())
        else nodeAt(e)?.let { n -> climbToClickable(n)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        return if (waitForChange(before)) TapResult.CHANGED else TapResult.NO_CHANGE
    }

    /** Finger tap at a point, then checks the screen changed. */
    suspend fun tapPointVerified(x: Float, y: Float): TapResult {
        val before = screenSignature()
        if (!tap(x, y)) return TapResult.FAILED
        return if (waitForChange(before)) TapResult.CHANGED else TapResult.NO_CHANGE
    }

    private suspend fun waitForChange(before: Int, maxMs: Long = 2_000): Boolean {
        var waited = 0L
        while (waited < maxMs) {
            delay(250)
            waited += 250
            if (screenSignature() != before) return true
        }
        return false
    }

    /** The node to click for [e]: itself, or a clickable parent that isn't much bigger and isn't a web page. */
    private fun clickTarget(node: AccessibilityNodeInfo, e: ScreenElement): AccessibilityNodeInfo? {
        if (inWebView(node)) return null
        if (node.isClickable) return node
        val parent = climbToClickable(node) ?: return null
        val pr = Rect().also { parent.getBoundsInScreen(it) }
        val area = maxOf(1, e.bounds.width() * e.bounds.height())
        val screen = resources.displayMetrics.let { it.widthPixels * it.heightPixels }
        val parentArea = pr.width() * pr.height()
        return if (parentArea <= area * 4 && parentArea < screen * 0.35) parent else null
    }

    private fun climbToClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var t: AccessibilityNodeInfo? = node
        var hops = 0
        while (t != null && !t.isClickable && hops < 6) {
            t = t.parent
            hops++
        }
        return t?.takeIf { it.isClickable }
    }

    private fun inWebView(node: AccessibilityNodeInfo): Boolean {
        var t: AccessibilityNodeInfo? = node
        var hops = 0
        while (t != null && hops < 25) {
            if (t.className?.toString()?.contains("WebView") == true) return true
            t = t.parent
            hops++
        }
        return false
    }

    /** A fingerprint of what's on screen right now, to tell whether an action did anything. */
    fun screenSignature(): Int {
        val root = appRoot() ?: return 0
        val sb = StringBuilder(root.packageName?.toString().orEmpty())
        var count = 0
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 30 || count > 300 || !n.isVisibleToUser) return
            count++
            n.text?.let { sb.append(it.take(40)).append('|') }
            n.contentDescription?.let { sb.append(it.take(20)).append('~') }
            if (n.isChecked) sb.append('+')
            if (n.isSelected) sb.append('*')
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        sb.append("#").append(runCatching { windows.size }.getOrDefault(0))
        return sb.toString().hashCode()
    }

    suspend fun longPressElement(e: ScreenElement): Boolean {''')
open(p, 'w', encoding='utf-8').write(s)

p = base + r'\ai\skills\BuiltInSkills.kt'
s = open(p, encoding='utf-8').read()
rep('''            MacroCenter.noteTap(e, a)
            if (a.tapElement(e)) { delay(700); "OK: tapped [$n] ${e.label}" } else "ERROR: tap failed"''',
    '''            MacroCenter.noteTap(e, a)
            tapReport(a.tapVerified(e), "[$n] ${e.label}")''')
rep('''            MacroCenter.noteTap(e, a)
            if (a.tapElement(e)) { delay(700); "OK: tapped \\"${e.label}\\"" } else "ERROR: tap failed"''',
    '''            MacroCenter.noteTap(e, a)
            tapReport(a.tapVerified(e), "\\"${e.label}\\"")''')
rep('''            if (accessibility().tap(x.toFloat(), y.toFloat())) { delay(700); "OK: tapped at $x,$y" } else "ERROR: tap failed"''',
    '''            tapReport(accessibility().tapPointVerified(x.toFloat(), y.toFloat()), "the point $x,$y")''')
rep('''    private fun whatsappLaunchIntent(ctx: Context): Intent? =''', '''    /** What a tap really did — never "OK" when the screen didn't change. */
    private fun tapReport(r: FaizaAccessibilityService.TapResult, what: String): String = when (r) {
        FaizaAccessibilityService.TapResult.CHANGED -> "OK: tapped $what — the screen changed"
        FaizaAccessibilityService.TapResult.NO_CHANGE ->
            "NOT CONFIRMED: tapped $what but NOTHING on the screen changed, so it probably did not work. Do NOT tell " +
                    "the user it worked. Call read_screen and tap a different element (e.g. the link text itself), or " +
                    "look_at_screen and tap_position."
        FaizaAccessibilityService.TapResult.FAILED -> "ERROR: the tap could not be performed"
    }

    private fun whatsappLaunchIntent(ctx: Context): Intent? =''')
open(p, 'w', encoding='utf-8').write(s)
print("ok")
