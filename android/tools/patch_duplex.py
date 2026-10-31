import os
p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ai\live\GeminiLiveSession.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:70], s.count(a))
    s = s.replace(a, b)


# 1) Loudspeaker = media stream on the loud speaker + EVERY bit of mic audio goes to the AI while she talks
#    (no loudness filter: on Honor the speaker drowned the user and the filter held the user back as "echo";
#    call mode made Honor squash the user's voice). The AI hears the user and stops; echo is handled by meaning.
rep('''        if (headset == null) {
            if (!loudspeaker && callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {''',
    '''        if (headset == null) {
            if (loudspeaker && !forceSpeaker && !headsetConnected()) {
                loudDuplex = bargeIn && !muteMicWhileSpeaking
                fullDuplex = loudDuplex
                return
            }
            if (!loudspeaker && callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {''')
rep('''            if ((loudspeaker || callSpeaker) && !forceSpeaker && !headsetConnected()) runCatching {''',
    '''            if (callSpeaker && !forceSpeaker && !headsetConnected()) runCatching {''')
rep('''    @Volatile private var useFastCompression = gamingFast''', '''    @Volatile private var useFastCompression = gamingFast

    /** Loudspeaker with the mic open the whole time she talks (WhatsApp-call style). */
    @Volatile private var loudDuplex = false
    /** When the mic was last transcribed (to tell a real interruption from her own echo). */
    @Volatile private var lastInputAt = 0L''')

# 2) Her own echo on the loudspeaker must not cut her off easily: the user's voice starting right at the phone is
#    still a clear "start of speech".
rep('''                    addProperty("startOfSpeechSensitivity", if (gamingFast) "START_SENSITIVITY_LOW" else "START_SENSITIVITY_HIGH")''',
    '''                    addProperty("startOfSpeechSensitivity", if (gamingFast || loudDuplex) "START_SENSITIVITY_LOW" else "START_SENSITIVITY_HIGH")''')

# 3) Mic transcript time.
rep('''                synchronized(heardSinceTurn) { heardSinceTurn.append(' ').append(text) }''',
    '''                synchronized(heardSinceTurn) { heardSinceTurn.append(' ').append(text) }
                lastInputAt = SystemClock.elapsedRealtime()''')

# 4) Stopped by her own echo (her words came back, or nothing the user said was heard): go on.
rep('''            if (!muteMicWhileSpeaking && echoResumes < 3) scope.launch {
                delay(900)
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                if (EchoText.isEchoTurn(heard, spoken) && !stopped) {''', '''            if (!muteMicWhileSpeaking && echoResumes < 3) scope.launch {
                val interruptedAt = SystemClock.elapsedRealtime()
                delay(if (loudDuplex) 1600 else 900)
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                val userSaidSomething = lastInputAt > interruptedAt - 1_500 && !EchoText.isEchoTurn(heard, spoken)
                val echo = EchoText.isEchoTurn(heard, spoken) || (loudDuplex && !userSaidSomething && heard.isEmpty())
                if (echo && !isSpeaking && !stopped) {''')
open(p, 'w', encoding='utf-8').write(s)
print('ok')
