base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\ai\live'

# ---- BargeInDetector: call-mode floors (the phone's call processing makes everything quieter) ----
p = base + r'\BargeInDetector.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep("""class BargeInDetector(hardwareEchoCanceller: Boolean) {""",
    """class BargeInDetector(hardwareEchoCanceller: Boolean, callMode: Boolean = false) {""")
rep("""    private val defaultRatio = if (hardwareEchoCanceller) 0.12 else 0.35""",
    """    private val defaultRatio = if (hardwareEchoCanceller) 0.12 else 0.35

    /**
     * Call loudspeaker mode: the phone's call processing (echo canceller + noise suppression) makes the user's voice
     * much quieter while Faiza talks, so the absolute floors must be lower or she never hears them.
     */
    private val minSoft = if (callMode) 70.0 else MIN_SOFT_RMS
    private val minLoud = if (callMode) 220.0 else MIN_LOUD_RMS""")
rep("""        max(MIN_SOFT_RMS, max(noiseFloor * 2.5 + 40.0, echoRatio() * playing * SOFT_ECHO_MARGIN + 60.0))""",
    """        max(minSoft, max(noiseFloor * 2.5 + 40.0, echoRatio() * playing * SOFT_ECHO_MARGIN + 60.0))""")
rep("""        max(MIN_LOUD_RMS, max(echoRatio() * playing * LOUD_ECHO_MARGIN + 150.0, softThreshold(playing) * 1.6))""",
    """        max(minLoud, max(echoRatio() * playing * LOUD_ECHO_MARGIN + 150.0, softThreshold(playing) * 1.6))""")
open(p, 'w', encoding='utf-8').write(s)

# ---- GeminiLiveSession: full duplex on the call speaker / headset ----
p = base + r'\GeminiLiveSession.kt'
s = open(p, encoding='utf-8').read()

rep("""    @Volatile private var hardwareEchoActive = false
""", """    @Volatile private var hardwareEchoActive = false

    /**
     * Call speaker or headset: the phone's echo canceller removes her voice, so the mic streams the whole time like a
     * WhatsApp call and Gemini hears the user even mid-sentence. Switched off for the session if her own echo keeps
     * interrupting her.
     */
    @Volatile private var fullDuplex = false
    private var selfInterrupts = 0
""")

# Decide full duplex when routing starts.
rep("""                routedCallSpeaker = true
            }
            return
        }""", """                routedCallSpeaker = true
                fullDuplex = !muteMicWhileSpeaking
                barge = BargeInDetector(hardwareEchoCanceller = true, callMode = true)
            }
            return
        }""")
rep("""            routedBluetooth = true
        }""", """            routedBluetooth = true
            fullDuplex = !muteMicWhileSpeaking
        }""")
# Never lose the call-mode detector when the recorder is re-created without a hardware AEC.
rep("""        if (!hardwareEchoActive && bargeAssumesAec) {
            bargeAssumesAec = false
            barge = BargeInDetector(hardwareEchoCanceller = false)
        }""", """        if (!hardwareEchoActive && bargeAssumesAec) {
            bargeAssumesAec = false
            barge = BargeInDetector(hardwareEchoCanceller = false, callMode = routedCallSpeaker)
            // No echo canceller at all: streaming everything would make her interrupt herself.
            if (routedCallSpeaker) fullDuplex = false
        }""")

# Mic loop: in full duplex, always send (local detector only adds an instant stop for loud speech).
rep("""                    val t = SystemClock.elapsedRealtime()
                    when (barge.onEchoChunk(level, outputLevel(t), t)) {""", """                    val t = SystemClock.elapsedRealtime()
                    if (fullDuplex) {
                        if (barge.onEchoChunk(level, outputLevel(t), t) == BargeInDetector.Action.INTERRUPT) silenceForBargeIn()
                        while (held.isNotEmpty()) { val c = held.removeFirst(); sendAudio(c, c.size) }
                        sendAudio(buffer, n)
                        continue
                    }
                    when (barge.onEchoChunk(level, outputLevel(t), t)) {""")

# Gemini stopped her: if what it "heard" was only her own words, that was her echo (self-interruption).
rep("""        if (content.get("interrupted")?.asBoolean == true) {
            bargeSuppressUntil = 0L""", """        if (content.get("interrupted")?.asBoolean == true) {
            if (fullDuplex) {
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }
                val spoken = synchronized(recentOutputWords) { recentOutputWords.mapTo(HashSet()) { it.word } }
                if (heard.size >= 2 && EchoText.echoShare(heard, spoken) >= 0.8 && ++selfInterrupts >= 2) {
                    // Her echo keeps getting through on this phone: go back to filtering it out.
                    fullDuplex = false
                }
            }
            bargeSuppressUntil = 0L""")
open(p, 'w', encoding='utf-8').write(s)
print("ok")
