package com.faiza.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import kotlin.random.Random
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Faiza's hands and eyes: reads what is on screen as a numbered list of elements,
 * taps/types/scrolls like a finger would, and can take a screenshot for visual questions.
 */
class FaizaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FaizaAccessibilityService? = null
            private set

        /** Called (main thread) whenever a different app/screen comes to the front. */
        @Volatile
        var foregroundListener: ((String) -> Unit)? = null

        /** Until this time (elapsedRealtime) Faiza herself is acting on the screen (a tool is running). */
        @Volatile var automationUntil = 0L

        private const val MAX_ELEMENTS = 150
        private const val MAX_LABEL = 80

        /** Lowercase, accent-free, keeps letters/digits and Indic vowel signs so Bengali matches Bengali. */
        fun normalize(s: String): String {
            val decomposed = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
            return buildString {
                for (ch in decomposed) {
                    val type = Character.getType(ch)
                    if (ch.isLetterOrDigit() ||
                        type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.COMBINING_SPACING_MARK.toInt()
                    ) append(ch)
                }
            }
        }
    }

    data class ScreenElement(
        val number: Int,
        val label: String,
        val role: String,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val bounds: Rect
    )

    @Volatile
    var foregroundPackage: String = ""
        private set

    /** The app on screen right now, including Faiza's own screens. */
    @Volatile
    var topPackage: String = ""
        private set

    @Volatile
    private var lastSnapshot: List<ScreenElement> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isNotEmpty() && !pkg.contains("inputmethod") && pkg != "com.android.systemui") {
                topPackage = pkg
                if (pkg != packageName) foregroundPackage = pkg
                foregroundListener?.invoke(pkg)
            }
            // A dialog just opened: is it "Delete…?" (Delete guard)? Give it a moment to fill in.
            if (pkg != packageName && pkg != "com.android.systemui") {
                deleteCheck?.let { handler.removeCallbacks(it) }
                deleteCheck = Runnable { runCatching { com.faiza.ai.ai.guardian.DeleteGuard.check(this) } }
                    .also { handler.postDelayed(it, 300) }
            }
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var deleteCheck: Runnable? = null

    override fun onInterrupt() {}

    private val headset = com.faiza.ai.assist.HeadsetDoublePress()

    /** Earphone button pressed twice quickly → Faiza listens (the first press still plays/pauses music). */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        // Volume-down while Faiza is talking = "stop and listen" (the volume still changes as usual).
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.action == android.view.KeyEvent.ACTION_DOWN) {
            handler.post { com.faiza.ai.service.FloatingOrbService.instance?.interruptIfSpeaking() }
            return false
        }
        if (!com.faiza.ai.FaizaApp.instance.prefs.isHeadsetTrigger) return false
        return headset.onKey(event.keyCode, event.action, event.eventTime) {
            handler.post { com.faiza.ai.assist.AssistLauncher.activate(this) }
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ========== Reading the screen ==========

    /**
     * The app window the user is looking at — INCLUDING Faiza's own screens (Code Studio, Chat, Settings…). Our orb and
     * floating mini windows are overlays (system windows), so they are left out by window TYPE, not by package: the
     * old "skip our package" rule made her blind on her own screens ("এটা টেক্সট বক্স না, আমি লিখতে পারি না").
     */
    private fun appRoot(): AccessibilityNodeInfo? {
        val apps = runCatching { windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } }.getOrDefault(emptyList())
        apps.firstOrNull { it.isActive || it.isFocused }?.root?.let { return it }
        rootInActiveWindow?.let { r ->
            // Another app's window, or one of our own app windows — but never one of our overlays.
            if (r.packageName?.toString() != packageName || apps.any { it.id == r.windowId }) return r
        }
        return apps.mapNotNull { it.root }.firstOrNull()
    }

    /** Can text be written into this node? (Also custom / WebView fields that don't say "editable".) */
    private fun isTextField(node: AccessibilityNodeInfo): Boolean = TextFieldRules.isTextField(
        node.className?.toString(),
        node.isEditable,
        runCatching { node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } }.getOrDefault(false)
    )

    /** Numbered list of visible elements; numbers stay valid until the next call. */
    fun snapshot(): List<ScreenElement> {
        val root = appRoot() ?: return emptyList<ScreenElement>().also { lastSnapshot = it }
        val raw = mutableListOf<ScreenElement>()
        collect(root, raw, depth = 0)
        val sorted = raw
            .distinctBy { "${it.bounds.flattenToString()}|${it.label}" }
            .sortedWith(compareBy({ it.bounds.top / 24 }, { it.bounds.left }))
            .take(MAX_ELEMENTS)
            .mapIndexed { i, e -> e.copy(number = i + 1) }
        lastSnapshot = sorted
        return sorted
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenElement>, depth: Int) {
        if (depth > 40 || out.size > MAX_ELEMENTS * 3) return
        if (!node.isVisibleToUser) return

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val hint = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString()?.trim().orEmpty() else ""
        val clickable = node.isClickable || node.isLongClickable
        val editable = isTextField(node)
        val scrollable = node.isScrollable

        var label = if (editable) {
            // Say clearly what's in the box: its text, or that it's empty (and what the grey hint says).
            val typed = if (text.isNotEmpty() && text != hint) text else ""
            val name = desc.ifEmpty { node.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' ').orEmpty() }
            buildString {
                if (typed.isNotEmpty()) append("contains: \"").append(typed.take(80)).append('"')
                else append("empty")
                if (hint.isNotEmpty()) append(" — hint: \"").append(hint.take(80)).append('"')
                else if (typed.isEmpty() && text.isNotEmpty()) append(" — hint: \"").append(text.take(80)).append('"')
                if (name.isNotEmpty()) append(" — ").append(name)
            }
        } else when {
            text.isNotEmpty() && desc.isNotEmpty() && desc != text -> "$text ($desc)"
            text.isNotEmpty() -> text
            desc.isNotEmpty() -> desc
            else -> ""
        }
        // A clickable container with no label of its own (e.g. a chat row): borrow its children's text.
        if (label.isEmpty() && clickable) label = childText(node, 0)
        if (label.isEmpty() && (clickable || editable)) {
            label = node.viewIdResourceName?.substringAfterLast('/')?.replace('_', ' ').orEmpty()
        }

        val worthListing = bounds.width() > 4 && bounds.height() > 4 &&
                (label.isNotEmpty() || editable || scrollable)
        if (worthListing) {
            out += ScreenElement(
                number = 0,
                label = label.replace('\n', ' ').take(MAX_LABEL),
                role = roleOf(node, editable, scrollable),
                clickable = clickable,
                editable = editable,
                scrollable = scrollable,
                bounds = bounds
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    private fun childText(node: AccessibilityNodeInfo, depth: Int): String {
        if (depth > 4) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val t = child.text?.toString()?.trim().orEmpty()
                .ifEmpty { child.contentDescription?.toString()?.trim().orEmpty() }
            if (t.isNotEmpty()) parts += t else childText(child, depth + 1).takeIf { it.isNotEmpty() }?.let { parts += it }
            if (parts.joinToString(" ").length > MAX_LABEL) break
        }
        return parts.joinToString(" · ")
    }

    private fun roleOf(node: AccessibilityNodeInfo, editable: Boolean, scrollable: Boolean): String {
        val cls = node.className?.toString()?.substringAfterLast('.').orEmpty()
        return when {
            editable -> "✏️ TEXT BOX (you can type here: type_text number=N)"
            cls.contains("Button") -> "button"
            cls.contains("Image") && node.isClickable -> "icon button"
            cls.contains("CheckBox") || cls.contains("Switch") -> if (node.isChecked) "switch ON" else "switch OFF"
            scrollable -> "scrollable list"
            node.isClickable -> "clickable"
            else -> "text"
        }
    }

    /** Human/AI-readable description of the current screen. */
    fun describeScreen(): String {
        val elements = snapshot()
        val metrics = resources.displayMetrics
        val app = appRoot()?.packageName?.toString() ?: foregroundPackage.ifEmpty { "unknown" }
        val header = "App: $app · screen ${metrics.widthPixels}x${metrics.heightPixels}px"
        if (elements.isEmpty()) return "$header\nNothing readable on screen (it may be a video, game, or a protected screen). Try look_at_screen."
        return buildString {
            appendLine(header)
            appendLine("Elements (use the number with tap_element):")
            for (e in elements) {
                append('[').append(e.number).append("] ")
                append(e.label.ifEmpty { "(no label)" })
                append(" — ").append(e.role)
                append(" @").append(e.bounds.centerX()).append(',').append(e.bounds.centerY())
                appendLine()
            }
            if (elements.any { it.editable }) appendLine(
                "Every ✏️ TEXT BOX can be typed into — in any app, including Faiza's own screens (Code Studio prompt, " +
                        "chat box, settings). Use type_text with its number."
            )
        }.take(7000)
    }

    // ========== Finding & tapping ==========

    fun elementByNumber(number: Int): ScreenElement? = lastSnapshot.firstOrNull { it.number == number }

    /** Best on-screen match for [query]; exact label beats prefix beats substring, clickable preferred. */
    fun findBest(query: String, accept: (ScreenElement) -> Boolean = { true }): ScreenElement? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val elements = snapshot().filter(accept)
        return elements.mapNotNull { e ->
            val l = normalize(e.label)
            if (l.isEmpty()) return@mapNotNull null
            val base = when {
                l == q -> 100
                l.startsWith(q) -> 85
                l.split(' ').any { it.startsWith(q) } -> 80
                l.contains(q) -> 70
                q.length >= 3 && q.contains(l) && l.length >= 3 -> 50
                else -> return@mapNotNull null
            }
            val bonus = (if (e.clickable) 10 else 0) + (if (e.editable) 3 else 0)
            e to base + bonus
        }.maxByOrNull { it.second }?.first
    }

    /** Taps an element: real click on the node (or its clickable parent), else a finger tap at its centre. */
    suspend fun tapElement(e: ScreenElement): Boolean {
        val node = nodeAt(e)
        if (node != null) {
            var target: AccessibilityNodeInfo? = node
            var hops = 0
            while (target != null && !target.isClickable && hops < 6) {
                target = target.parent
                hops++
            }
            if (target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return tap(e.bounds.exactCenterX(), e.bounds.exactCenterY())
    }

    enum class TapResult { CHANGED, NO_CHANGE, FAILED }

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

    suspend fun longPressElement(e: ScreenElement): Boolean {
        val node = nodeAt(e)
        if (node != null && node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
        return gesture(path(e.bounds.exactCenterX(), e.bounds.exactCenterY()), 0, 700)
    }

    /** Re-finds the live node for a snapshot element (snapshots don't keep stale node handles). */
    private fun nodeAt(e: ScreenElement): AccessibilityNodeInfo? {
        val root = appRoot() ?: return null
        var best: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || best != null) return
            val r = Rect().also { n.getBoundsInScreen(it) }
            if (r == e.bounds) {
                best = n
                return
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return best
    }

    // ========== Gestures ==========

    private fun path(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    private suspend fun gesture(path: Path, startMs: Long, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, startMs, durationMs))
                .build()
            val sent = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!sent && cont.isActive) cont.resume(false)
        }

    suspend fun tap(x: Float, y: Float): Boolean = gesture(path(x, y), 0, 60)

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 320): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 0, durationMs)

    /**
     * Scrolls like a finger. "down" shows content further down (finger moves up) —
     * this is also "next video" in YouTube Shorts / Reels / TikTok.
     */
    suspend fun scroll(direction: String): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        return when (direction) {
            "down" -> swipe(w / 2, h * 0.72f, w / 2, h * 0.28f)
            "up" -> swipe(w / 2, h * 0.28f, w / 2, h * 0.72f)
            "left" -> swipe(w * 0.2f, h / 2, w * 0.8f, h / 2)   // show content to the left
            "right" -> swipe(w * 0.8f, h / 2, w * 0.2f, h / 2)  // show content to the right
            else -> false
        }
    }

    // ========== Typing ==========

    /**
     * The text box to type into: [target] if given (tapped first so it gets the cursor), else the focused one, else the
     * first on screen.
     */
    private suspend fun typingField(target: ScreenElement? = null): AccessibilityNodeInfo? {
        if (target == null) {
            appRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { isTextField(it) }?.let { return it }
        }
        val box = target ?: snapshot().firstOrNull { it.editable } ?: return null
        tapElement(box)
        delay(400)
        val focused = appRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { isTextField(it) }
        // With a chosen box, trust the focus only if it really is that box.
        if (focused != null && (target == null || Rect().also { focused.getBoundsInScreen(it) }.intersect(target.bounds))) return focused
        return nodeAt(box) ?: focused
    }

    /** Text in the message / text box on screen now (hint ignored), or null when there is no box. */
    fun messageBoxText(): String? {
        val root = appRoot() ?: return null
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 40 || found != null) return
            if (isTextField(n) && n.isVisibleToUser) { found = n; return }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        return found?.let { currentText(it) }
    }

    private fun currentText(field: AccessibilityNodeInfo): String {
        val existing = field.text?.toString().orEmpty()
        val hint = if (Build.VERSION.SDK_INT >= 26) field.hintText?.toString().orEmpty() else ""
        return if (existing == hint) "" else existing
    }

    private fun setFieldText(field: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        return pasteInto(field, value)
    }

    /** Fallback for boxes that refuse SET_TEXT (some web pages, custom apps): select all, then paste. */
    private fun pasteInto(field: AccessibilityNodeInfo, value: String): Boolean = runCatching {
        val cm = getSystemService(android.content.ClipboardManager::class.java) ?: return false
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Faiza", value))
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val len = field.text?.length ?: 0
        if (len > 0) field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        })
        field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }.getOrDefault(false)

    /** Realistic typing is on, and this app is one of the chosen ones (or none are chosen). */
    fun typeLikePerson(): Boolean {
        val prefs = com.faiza.ai.FaizaApp.instance.prefs
        if (!prefs.isRealisticTyping) return false
        val apps = prefs.typingApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return apps.isEmpty() || foregroundPackage in apps
    }

    /** Types all of [text] at once. */
    suspend fun typeText(text: String, append: Boolean = false, target: ScreenElement? = null): Boolean {
        val field = typingField(target) ?: return false
        val current = currentText(field)
        return setFieldText(field, if (append && current.isNotEmpty()) current + text else text)
    }

    /** Types like a person, one character at a time (video 29:11). Never takes longer than about 12 seconds. */
    suspend fun typeTextHuman(text: String, append: Boolean = false, target: ScreenElement? = null): Boolean {
        var field = typingField(target) ?: return false
        val points = text.codePoints().toArray()
        val sb = StringBuilder(if (append) currentText(field) else "")
        if (points.isEmpty()) return setFieldText(field, sb.toString())
        // Slow / Normal / Fast (MAYA). Long texts speed up so typing never takes forever.
        val (base, maxTotal) = when (com.faiza.ai.FaizaApp.instance.prefs.typingSpeed) {
            "slow" -> 140L to 25_000L
            "fast" -> 30L to 8_000L
            else -> 75L to 14_000L
        }
        val perChar = minOf(base, maxTotal / points.size).coerceAtLeast(10L)
        for (cp in points) {
            sb.appendCodePoint(cp)
            field.refresh()
            if (!setFieldText(field, sb.toString())) {
                // The screen may have changed under us: find the box once more.
                field = typingField(target) ?: return false
                if (!setFieldText(field, sb.toString())) return false
            }
            delay(perChar + Random.nextLong(0, perChar / 2 + 1))
        }
        return true
    }

    /** Presses the keyboard's Enter / Search / Send key on the focused box (Android 11+). */
    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val field = appRoot()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** Clicks the first clickable element whose content description exactly matches (e.g. a "Send" icon). */
    fun clickByDescription(description: String): Boolean {
        val root = appRoot() ?: return false
        fun find(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (depth > 40) return null
            if (n.isClickable && n.contentDescription?.toString().equals(description, ignoreCase = true)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c, depth + 1)?.let { return it } }
            return null
        }
        return find(root, 0)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    // ========== Screenshot ==========

    /** JPEG (base64) of the current screen, max 768 px wide. Android 11+ only. */
    private val shotExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    /** [fast] (gaming): work on a background thread and shrink before copying, so the game doesn't stutter. */
    suspend fun screenshotJpeg(maxSide: Int? = null, quality: Int = 70, fast: Boolean = false): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, if (fast) shotExecutor else mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val encoded = runCatching {
                        val hw = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        val soft = if (fast && wrapped != null && maxSide != null) {
                            // Scale the GPU bitmap first, then read back only the small one.
                            val s = minOf(1f, maxSide.toFloat() / maxOf(wrapped.width, wrapped.height))
                            runCatching {
                                Bitmap.createScaledBitmap(wrapped, (wrapped.width * s).toInt(), (wrapped.height * s).toInt(), true)
                                    .copy(Bitmap.Config.ARGB_8888, false)
                            }.getOrNull() ?: wrapped.copy(Bitmap.Config.ARGB_8888, false)
                        } else wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()
                        soft?.let { bmp ->
                            val scale = if (maxSide != null) minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
                            else minOf(1f, 768f / bmp.width)
                            val scaled = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                            } else bmp
                            val out = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        }
                    }.getOrNull()
                    if (cont.isActive) cont.resume(encoded)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    /**
     * Gaming: one capture → the whole screen, plus (if [zoom]) a 2× close-up of the middle where far-away enemies and
     * the road ahead are (framed in yellow so the AI knows it's the zoom). Both shrunk on the GPU, off the main thread.
     */
    suspend fun gameShots(maxSide: Int, quality: Int, zoom: Boolean): List<ByteArray> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(Display.DEFAULT_DISPLAY, shotExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val shots = mutableListOf<ByteArray>()
                    runCatching {
                        val hw = result.hardwareBuffer
                        val full = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        if (full != null) {
                            fun jpeg(src: Bitmap, frame: Boolean): ByteArray {
                                val s = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
                                val small = runCatching {
                                    Bitmap.createScaledBitmap(src, maxOf(1, (src.width * s).toInt()), maxOf(1, (src.height * s).toInt()), true)
                                        .copy(Bitmap.Config.ARGB_8888, frame)
                                }.getOrNull() ?: src.copy(Bitmap.Config.ARGB_8888, frame)
                                if (frame) {
                                    val c = android.graphics.Canvas(small)
                                    val p = android.graphics.Paint().apply {
                                        style = android.graphics.Paint.Style.STROKE; strokeWidth = 6f; color = android.graphics.Color.YELLOW
                                    }
                                    c.drawRect(3f, 3f, small.width - 3f, small.height - 3f, p)
                                }
                                return ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                            }
                            shots += jpeg(full, false)
                            if (zoom) runCatching {
                                // Middle half of the screen, a little above centre (horizon / road ahead / rooftops).
                                val w = full.width / 2; val h = full.height / 2
                                val x = full.width / 4; val y = (full.height * 0.18f).toInt()
                                shots += jpeg(Bitmap.createBitmap(full, x, y, w, minOf(h, full.height - y)), true)
                            }
                        }
                        hw.close()
                    }
                    if (cont.isActive) cont.resume(shots)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            })
        }
    }

    // ========== System buttons ==========

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
}
