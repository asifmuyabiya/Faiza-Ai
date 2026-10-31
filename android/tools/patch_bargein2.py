import os
p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ai\live\GeminiLiveSession.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


# 1) Loudspeaker the WhatsApp-video-call way: call mode + the speaker as the call device → the phone's own echo
#    canceller removes her voice from the mic, so the user's voice gets through while she talks.
rep('''            if (callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {
                if (loudspeaker) return // media stream → loudspeaker, mic stays open for "stop when I talk"
                liftCallVolume()
                callStreamOnly = true
                return
            }
            // Screen recording only captures the media stream, and earphones take the sound by themselves.
            if (callSpeaker && !forceSpeaker && !headsetConnected()) runCatching {''', '''            if (!loudspeaker && callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {
                liftCallVolume()
                callStreamOnly = true
                return
            }
            // Loudspeaker (owner: "WhatsApp/imo ভিডিও কলের মতো") or call speaker: call mode on the LOUD speaker. Screen
            // recording only captures the media stream, and earphones take the sound by themselves.
            if ((loudspeaker || callSpeaker) && !forceSpeaker && !headsetConnected()) runCatching {''')

# 2) + 3) Echo protection by meaning, not loudness.
rep('''    private fun handleServerContent(content: JsonObject) {
        content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { el ->
            val data = el.asJsonObject.getAsJsonObject("inlineData")?.get("data")?.asString ?: return@forEach
            enqueueAudio(Base64.decode(data, Base64.DEFAULT))
        }''', '''    /** What the mic transcribed since her last answer: if it's only her own words, she must not answer it. */
    private val heardSinceTurn = StringBuilder()
    /** This answer is a reply to her own echo: don't play it. */
    @Volatile private var muteThisTurn = false
    /** Audio of the current answer has started arriving. */
    @Volatile private var turnStarted = false
    @Volatile private var echoResumes = 0

    private fun handleServerContent(content: JsonObject) {
        content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { el ->
            val data = el.asJsonObject.getAsJsonObject("inlineData")?.get("data")?.asString ?: return@forEach
            if (!turnStarted) {
                turnStarted = true
                // A new answer is starting: was the "user" she's answering just her own voice from the speaker?
                val heard = synchronized(heardSinceTurn) { EchoText.words(heardSinceTurn.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                muteThisTurn = !muteMicWhileSpeaking && EchoText.isEchoTurn(heard, spoken)
            }
            if (muteThisTurn) return@forEach
            enqueueAudio(Base64.decode(data, Base64.DEFAULT))
        }
        if (content.get("turnComplete")?.asBoolean == true || content.get("generationComplete")?.asBoolean == true) {
            if (content.get("turnComplete")?.asBoolean == true) {
                turnStarted = false
                muteThisTurn = false
                synchronized(heardSinceTurn) { heardSinceTurn.setLength(0) }
            }
        }''')
rep('''            ?.takeIf { it.isNotBlank() }?.let { text ->
                checkOverTalk(text)
                main.post { listener.onUserSpeech(text) }
            }''', '''            ?.takeIf { it.isNotBlank() }?.let { text ->
                synchronized(heardSinceTurn) { heardSinceTurn.append(' ').append(text) }
                checkOverTalk(text)
                main.post { listener.onUserSpeech(text) }
            }''')
rep('''            bargeSuppressUntil = 0L
            flushPlayback()
            barge.resetTurn()
            synchronized(overTalkText) { overTalkText.setLength(0) }
        }
    }''', '''            bargeSuppressUntil = 0L
            flushPlayback()
            barge.resetTurn()
            turnStarted = false
            muteThisTurn = false
            // Stopped by her OWN voice from the speaker (not the user)? Wait for the transcript, then let her go on.
            if (!muteMicWhileSpeaking && echoResumes < 3) scope.launch {
                delay(900)
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                if (EchoText.isEchoTurn(heard, spoken) && !stopped) {
                    echoResumes++
                    synchronized(heardSinceTurn) { heardSinceTurn.setLength(0) }
                    sendText("[ECHO] That sound was only your own voice from the loudspeaker, not the user. Continue your " +
                            "answer exactly from where you stopped — don't repeat what you already said, don't mention this.")
                }
                synchronized(overTalkText) { overTalkText.setLength(0) }
            } else synchronized(overTalkText) { overTalkText.setLength(0) }
        }
        if (content.get("turnComplete")?.asBoolean == true) echoResumes = 0
    }''')
open(p, 'w', encoding='utf-8').write(s)

p2 = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ai\live\BargeInDetector.kt'
s = open(p2, encoding='utf-8').read()
a = '''    /** True when the mic transcript is the user talking over Faiza, not her echo. */'''
assert s.count(a) == 1
s = s.replace(a, '''    /** Everything the mic "heard" was Faiza's own recent words: an echo, not the user. */
    fun isEchoTurn(heard: List<String>, recentOutput: Set<String>): Boolean =
        heard.size >= 2 && echoShare(heard, recentOutput) >= 0.75

''' + a)
open(p2, 'w', encoding='utf-8').write(s)
print('ok')
