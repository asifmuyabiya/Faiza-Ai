import os
base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = os.path.join(base, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- Live session ----------
p = 'ai/live/GeminiLiveSession.kt'
# Mic taken by the game's voice chat: retry rarely (re-opening the mic is heavy and glitches game audio).
edit(p, '''                    delay(MIC_REOPEN_MS)''', '''                    delay(micReopenMs)''')
edit(p, '''                    if (now - lastReopen >= MIC_REOPEN_MS) {''', '''                    if (now - lastReopen >= micReopenMs) {''')
edit(p, '''    @Volatile private var useFastCompression = gamingFast
''', '''    @Volatile private var useFastCompression = gamingFast

    /** In a game the mic is often taken by the game's voice chat: check for it back only now and then. */
    private val micReopenMs get() = if (gamingFast) 6_000L else MIC_REOPEN_MS

    /** Current output usage of [track] (media, or voice-call while another app's call/voice chat is on). */
    @Volatile private var trackUsage = AudioAttributes.USAGE_MEDIA

    /**
     * Gaming: when the game turns its voice chat on, the phone switches to "call" audio and media sound can go
     * quiet or vanish on some phones. Then she follows the call route; back to media when the voice chat is off.
     */
    fun followOtherAppCall(otherCallActive: Boolean) {
        if (stopped || routedBluetooth || routedCallSpeaker || callStreamOnly) return
        val want = if (otherCallActive) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        if (want == trackUsage) return
        val old = track
        track = buildTrack(want)
        trackUsage = want
        old?.runCatching { pause(); flush(); release() }
    }
''')
edit(p, '''        val usage = if (routedBluetooth || routedCallSpeaker || callStreamOnly) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA
        val t = AudioTrack.Builder()''', '''        val usage = if (routedBluetooth || routedCallSpeaker || callStreamOnly) AudioAttributes.USAGE_VOICE_COMMUNICATION
        else AudioAttributes.USAGE_MEDIA
        trackUsage = usage
        track = buildTrack(usage)
        scope.launch(Dispatchers.IO) {
            for (chunk in playback) {
                if (stopped) break
                // Read [track] each time: gaming may swap it when the game's voice chat starts/stops.
                track?.runCatching { write(chunk, 0, chunk.size) }
            }
        }
    }

    private fun buildTrack(usage: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()''')
edit(p, '''        t.play()
        track = t
        if (louderVoice && !routedBluetooth) {''', '''        t.play()
        loudness?.runCatching { release() }
        if (louderVoice && !routedBluetooth) {''')
edit(p, '''            }.getOrNull()
        }
        scope.launch(Dispatchers.IO) {
            for (chunk in playback) {
                if (stopped) break
                t.write(chunk, 0, chunk.size)
            }
        }
    }''', '''            }.getOrNull()
        }
        return t
    }''')

# ---------- Service ----------
p = 'service/FloatingOrbService.kt'
edit(p, '''            muteMicWhileSpeaking = !prefs.isBargeIn || prefs.isScreenRecordingMode,''',
     '''            // Gaming echo guard: game sound and her own voice must not cut her off mid-call.
            muteMicWhileSpeaking = !prefs.isBargeIn || prefs.isScreenRecordingMode || (gaming && prefs.gamingEchoGuard),''')
edit(p, '''        override fun onMicBlocked(blocked: Boolean) {
            if (blocked && !micBusyToastShown) {''', '''        override fun onMicBlocked(blocked: Boolean) {
            // In a game the game's voice chat often holds the mic: she keeps talking, no warning needed.
            if (blocked && com.faiza.ai.ai.game.GameMode.active.value) { com.faiza.ai.ai.game.GameMode.micTaken = true; return }
            if (!blocked) com.faiza.ai.ai.game.GameMode.micTaken = false
            if (blocked && !micBusyToastShown) {''')
edit(p, '''            val now = SystemClock.elapsedRealtime()
            val talk = gm.talk
            val q = gm.quality''', '''            val now = SystemClock.elapsedRealtime()
            if (now - lastRouteCheck >= 2_000) {
                lastRouteCheck = now
                val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                session.followOtherAppCall(am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION)
            }
            val talk = gm.talk
            val q = gm.quality''')
edit(p, '''        var lastTick = SystemClock.elapsedRealtime()
        var missing = 0''', '''        var lastTick = SystemClock.elapsedRealtime()
        var lastRouteCheck = 0L
        var missing = 0''')

# ---------- Prefs ----------
edit('util/PrefsManager.kt', '''    /** "" = automatic, SHARP or SMOOTH. */''', '''    /** Gaming: don't listen while she talks, so game sound / her own voice can't cut her off. */
    var gamingEchoGuard: Boolean
        get() = prefs.getBoolean("gaming_echo_guard", true)
        set(value) = prefs.edit().putBoolean("gaming_echo_guard", value).apply()
    /** "" = automatic, SHARP or SMOOTH. */''')

# ---------- GameMode skill ----------
p = 'ai/game/GameMode.kt'
edit(p, '''    /** "চুপ করো" during a game: keep watching, stop the running commentary. */''', '''    /** The game's voice chat has the microphone: she can't hear the user right now, but keeps talking. */
    @Volatile var micTaken = false

    /** "চুপ করো" during a game: keep watching, stop the running commentary. */''')
edit(p, '''                ToolParam("action", ParamType.STRING, "on | off | guide | quiet | resume | talk | jokes | status | settings"),''',
     '''                ToolParam("action", ParamType.STRING, "on | off | guide | quiet | resume | talk | jokes | fix_echo | allow_interrupt | status | settings"),''')
edit(p, '''                "status" -> when {''', '''                "fix_echo", "echo", "stuck" -> {
                    prefs.gamingEchoGuard = true
                    if (active.value) svc?.restartGamingSession()
                    "Fixed: while you talk, the mic is now closed, so game sound and your own voice can't stop you. " +
                            "Tell the user in one short line (they can still talk to you between your lines)."
                }
                "allow_interrupt" -> {
                    prefs.gamingEchoGuard = false
                    if (active.value) svc?.restartGamingSession()
                    "The user can interrupt you mid-sentence now (best with headphones). Say it in one short line."
                }
                "status" -> when {''')
edit(p, '''                    active.value -> "ON for $gameName · ${talk.label} · jokes ${if (prefs.gamingJokes) "on" else "off"}" + if (quiet) " · quiet" else ""''',
     '''                    active.value -> "ON for $gameName · ${talk.label} · jokes ${if (prefs.gamingJokes) "on" else "off"}" +
                            (if (quiet) " · quiet" else "") + (if (micTaken) " · the game's voice chat has the mic (you can't hear the user now)" else "")''')

# ---------- Prompt: tell her what to do when stuck ----------
p = 'ai/game/GameKnowledge.kt'
edit(p, '''"\\"আবার বলো\\" → action=resume. Turning off → action=off."''',
     '''"\\"আবার বলো\\" → action=resume. Turning off → action=off. If the user says you keep getting stuck / stopping / " +
                    "hearing yourself (\\"তুমি নিজের কথা শুনে আটকে যাচ্ছ\\", \\"কথা বলতে বলতে থেমে যাচ্ছ\\") → action=fix_echo. " +
                    "If they want to cut you off mid-sentence (with headphones) → action=allow_interrupt."''')
print('ok')
