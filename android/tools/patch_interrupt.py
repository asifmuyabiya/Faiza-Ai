import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- Live session ----------
p = 'ai/live/GeminiLiveSession.kt'
edit(p, '''    /** Ask the model to say something (e.g. a notification or a typed question) in its own voice. */
    fun sendText(text: String) {''', '''    /**
     * The user cut in (stop word heard on the phone, orb tapped, volume-down pressed): silence her NOW, stop the
     * answer on the server too, and listen — same conversation, nothing forgotten.
     */
    fun userInterrupt() {
        if (!setupDone || stopped) return
        barge.resetTurn()
        spotter?.reset()
        interruptWith(
            "[THE USER INTERRUPTED YOU] Stop the previous answer right now. Say only 1–3 words like \\"হ্যাঁ, বলুন\\" / " +
                    "\\"জি?\\" in their language, then listen. When they finish, answer what they said. If they say " +
                    "\\"continue / আগেরটা বলো / তারপর\\", continue the earlier answer from where you stopped."
        )
    }

    /** Hears "stop / wait / Faiza…" on the phone while she talks (null if the offline model isn't there). */
    @Volatile private var spotter: StopWordSpotter? = null
    private var spotterUsed = false

    /** Ask the model to say something (e.g. a notification or a typed question) in its own voice. */
    fun sendText(text: String) {''')
edit(p, '''                val level = rms(buffer, 0, n)
                if (echoActive) {''', '''                val level = rms(buffer, 0, n)
                // Stop words while she talks: decided here on the phone, whatever the loudspeaker does to the mic.
                if (echoActive && bargeIn && !muteMicWhileSpeaking) {
                    val sp = spotter
                    if (sp != null) {
                        spotterUsed = true
                        val her = synchronized(recentOutputWords) {
                            val since = SystemClock.elapsedRealtime() - 6_000
                            recentOutputWords.filter { it.at >= since }.mapTo(HashSet()) { it.word }
                        }
                        if (sp.feed(buffer, n, her)) {
                            main.post { userInterrupt() }
                            held.clear()
                            continue
                        }
                    }
                } else if (spotterUsed) {
                    spotterUsed = false
                    spotter?.reset()
                }
                if (echoActive) {''')
edit(p, '''    fun stop() {
        if (stopped) return
        stopped = true''', '''    fun stop() {
        if (stopped) return
        stopped = true
        spotter?.close()
        spotter = null''')
edit(p, '''        startCallAudioRouting()
        startPlayback()
        startMicLoop()''', '''        startCallAudioRouting()
        startPlayback()
        // Not in games (game sound would trip it; gaming has its own echo guard).
        if (bargeIn && !gamingFast) scope.launch(Dispatchers.IO) { spotter = StopWordSpotter.create(appContext) }
        startMicLoop()''')

# ---------- Service: tap to interrupt, volume-down to interrupt ----------
p = 'service/FloatingOrbService.kt'
edit(p, '''        if (live != null) {
            sleepLive()
            return
        }''', '''        val session = live
        if (session != null) {
            // While she's talking, a tap means "stop and listen to me" — the conversation goes on.
            if (session.isSpeaking) { session.userInterrupt(); return }
            sleepLive()
            return
        }''')
edit(p, '''    /** The live voice chat needs the internet;''', '''    /** Volume-down while she talks (from the accessibility service): stop and listen. True if she was talking. */
    fun interruptIfSpeaking(): Boolean {
        val s = live ?: return false
        if (!s.isSpeaking) return false
        s.userInterrupt()
        return true
    }

    /** The live voice chat needs the internet;''')

p = 'service/FaizaAccessibilityService.kt'
edit(p, '''    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!com.faiza.ai.FaizaApp.instance.prefs.isHeadsetTrigger) return false''', '''    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        // Volume-down while Faiza is talking = "stop and listen" (the volume still changes as usual).
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.action == android.view.KeyEvent.ACTION_DOWN) {
            handler.post { com.faiza.ai.service.FloatingOrbService.instance?.interruptIfSpeaking() }
            return false
        }
        if (!com.faiza.ai.FaizaApp.instance.prefs.isHeadsetTrigger) return false''')

# ---------- Memory: the recent conversation goes into every new chat ----------
p = 'ai/memory/MemoryStore.kt'
edit(p, '''    fun logSize(): Int = synchronized(lock) { log.size }''', '''    fun logSize(): Int = synchronized(lock) { log.size }

    /** The last lines of talk from the past [withinMs] (so a new voice chat continues where the last one stopped). */
    fun recentConversation(maxLines: Int = 16, withinMs: Long = 3 * 3600_000L, now: Long = System.currentTimeMillis()): List<ConversationLine> =
        synchronized(lock) { log.filter { now - it.at <= withinMs }.takeLast(maxLines) }''')
p = 'ai/FaizaAgent.kt'
edit(p, '''            val known = MemoryStore.recentFacts(40)''', '''            val recent = MemoryStore.recentConversation()
            if (recent.isNotEmpty()) {
                appendLine("RECENT CONVERSATION (the last minutes/hours — continue naturally from here; keep the same topic, " +
                        "tone and details; don't repeat it back unless asked):")
                recent.forEach { l -> appendLine("${if (l.who == "user") "User" else "You"}: ${l.text.take(220)}") }
            }
            val known = MemoryStore.recentFacts(40)''')
print('ok')
