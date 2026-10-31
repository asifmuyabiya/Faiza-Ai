import os
base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = os.path.join(base, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- Live session: gaming fast mode ----------
p = 'ai/live/GeminiLiveSession.kt'
edit(p, '''    private val bargeIn: Boolean = true
) {''', '''    private val bargeIn: Boolean = true,
    /**
     * Gaming mode: keep the conversation short (a picture every second fills it fast and slows every answer) and
     * don't let game sounds in the mic count as the user talking.
     */
    private val gamingFast: Boolean = false
) {
    @Volatile private var useFastCompression = gamingFast
''')
edit(p, '''                    addProperty("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")''',
     '''                    addProperty("startOfSpeechSensitivity", if (gamingFast) "START_SENSITIVITY_LOW" else "START_SENSITIVITY_HIGH")''')
edit(p, '''            add("contextWindowCompression", JsonObject().apply { add("slidingWindow", JsonObject()) })''',
     '''            add("contextWindowCompression", JsonObject().apply {
                if (useFastCompression) {
                    // ~25 pictures of game + chat, then the oldest are dropped: answers stay fast all match long.
                    addProperty("triggerTokens", "12000")
                    add("slidingWindow", JsonObject().apply { addProperty("targetTokens", "5000") })
                } else add("slidingWindow", JsonObject())
            })''')
edit(p, '''        if (useVadConfig && (code == 1007 || code == 1008 || lower.contains("invalid")) &&''',
     '''        if (useFastCompression && (code == 1007 || code == 1008 || lower.contains("invalid")) &&
            listOf("compression", "trigger", "target").any { lower.contains(it) }
        ) {
            useFastCompression = false
            scope.launch { delay(300); if (!stopped) connect() }
            return
        }
        if (useVadConfig && (code == 1007 || code == 1008 || lower.contains("invalid")) &&''')

# ---------- Service: compact gaming session, fresh frame per call, smoother capture ----------
p = 'service/FloatingOrbService.kt'
edit(p, '''        val skills = agent?.currentSkills().orEmpty()
        val tools = skills.map { it.spec } + GO_TO_SLEEP
        val prompt = (agent?.liveSystemPrompt() ?: "You are ${prefs.assistantName(persona)}, a friendly phone assistant.") +''',
     '''        val gaming = com.faiza.ai.ai.game.GameMode.active.value
        // Gaming: a short prompt and only the tools a player needs — every extra token is delay on every call.
        val skills = agent?.currentSkills().orEmpty()
            .filter { !gaming || it.id in com.faiza.ai.ai.game.GameMode.GAMING_TOOLS }
        val tools = skills.map { it.spec } + GO_TO_SLEEP
        val basePrompt = if (gaming) com.faiza.ai.ai.game.GameMode.compactPersona(prefs.assistantName(persona), language.promptName)
        else agent?.liveSystemPrompt()
        val prompt = (basePrompt ?: "You are ${prefs.assistantName(persona)}, a friendly phone assistant.") +''')
edit(p, '''            bargeIn = prefs.isBargeIn
        ).also { it.start(liveGreeting) }''', '''            bargeIn = prefs.isBargeIn,
            gamingFast = gaming
        ).also { it.start(liveGreeting) }''')

# The loop: capture → send → call, so every call is about the newest picture.
edit(p, '''            val now = SystemClock.elapsedRealtime()
            if (now - lastFrame >= 1000) {
                lastFrame = now
                FaizaAccessibilityService.instance?.screenshotJpeg(maxSide = 960, quality = 55)?.let {
                    session.sendImage(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
                }
            }
            val talk = gm.talk
            if (!gm.quiet && !session.isSpeaking &&
                now - lastTick >= talk.tickMs &&
                now - lastModelSpeechAt >= talk.tickMs * 2 / 3 &&
                now - lastUserSpeechAt >= 4_000
            ) {
                lastTick = now
                session.sendText(gm.TICK)
            }
            delay(250)''', '''            val now = SystemClock.elapsedRealtime()
            val talk = gm.talk
            val q = gm.quality
            val callDue = !gm.quiet && !session.isSpeaking &&
                    now - lastTick >= talk.tickMs &&
                    now - lastModelSpeechAt >= talk.afterSpeechMs &&
                    now - lastUserSpeechAt >= 2_500
            if (callDue || now - lastFrame >= q.frameMs) {
                lastFrame = now
                FaizaAccessibilityService.instance?.screenshotJpeg(maxSide = q.maxSide, quality = q.jpeg, fast = true)?.let {
                    session.sendImage(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
                }
            }
            if (callDue) {
                lastTick = SystemClock.elapsedRealtime()
                session.sendText(gm.TICK)
            }
            delay(120)''')
edit(p, '''        val b64 = a.screenshotJpeg(maxSide = 960, quality = 60) ?: return "ERROR: couldn't capture the screen."''',
     '''        val b64 = a.screenshotJpeg(maxSide = 768, quality = 55, fast = true) ?: return "ERROR: couldn't capture the screen."''')

# ---------- Accessibility: capture off the main thread, shrink on the GPU before copying ----------
p = 'service/FaizaAccessibilityService.kt'
edit(p, '''    suspend fun screenshotJpeg(maxSide: Int? = null, quality: Int = 70): String? {''',
     '''    private val shotExecutor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

    /** [fast] (gaming): work on a background thread and shrink before copying, so the game doesn't stutter. */
    suspend fun screenshotJpeg(maxSide: Int? = null, quality: Int = 70, fast: Boolean = false): String? {''')
edit(p, '''            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {''',
     '''            takeScreenshot(Display.DEFAULT_DISPLAY, if (fast) shotExecutor else mainExecutor, object : TakeScreenshotCallback {''')
edit(p, '''                        val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        val soft = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()''', '''                        val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                        val soft = if (fast && wrapped != null && maxSide != null) {
                            // Scale the GPU bitmap first, then read back only the small one.
                            val s = minOf(1f, maxSide.toFloat() / maxOf(wrapped.width, wrapped.height))
                            runCatching {
                                Bitmap.createScaledBitmap(wrapped, (wrapped.width * s).toInt(), (wrapped.height * s).toInt(), true)
                                    .copy(Bitmap.Config.ARGB_8888, false)
                            }.getOrNull() ?: wrapped.copy(Bitmap.Config.ARGB_8888, false)
                        } else wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        hw.close()''')
print('ok')
