import sys
sys.path.insert(0, r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools')
from settings_rows import row, apply

base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'
s = ''


def load(p):
    global s
    s = open(p, encoding='utf-8').read()


def save(p):
    open(p, 'w', encoding='utf-8').write(s)


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# ---- Live session: a hard "always let the user interrupt" switch ----
p = base + r'\ai\live\GeminiLiveSession.kt'
load(p)
rep('''    /** Extra microphone gain for soft speech (1 = off). */
    private val micGain: Float = 1f
) {''', '''    /** Extra microphone gain for soft speech (1 = off). */
    private val micGain: Float = 1f,
    /**
     * "Stop when I talk" (owner's switch): on the call speaker / a headset the mic stays open the whole time she
     * speaks, and nothing in here is allowed to switch that off by itself.
     */
    private val bargeIn: Boolean = true
) {''')
# Self-interrupt fallback only when the user didn't lock barge-in on.
rep('''            if (fullDuplex) {
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }''',
    '''            if (fullDuplex && !bargeIn) {
                val heard = synchronized(overTalkText) { EchoText.words(overTalkText.toString()) }''')
# Missing AEC *effect* doesn't mean no echo cancelling: call mode cancels echo in the platform itself.
rep('''            // No echo canceller at all: streaming everything would make her interrupt herself.
            if (routedCallSpeaker) fullDuplex = false''',
    '''            // No echo-canceller effect reported. In call mode the phone still cancels echo itself, so with
            // "Stop when I talk" on the mic stays open; otherwise fall back to filtering.
            if (routedCallSpeaker && !bargeIn) fullDuplex = false''')
save(p)

# ---- Prefs ----
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    // ====== How she addresses the user ======''', '''    /** "Stop when I talk": she stops the moment you speak over her. Off = she never stops mid-sentence. */
    var isBargeIn: Boolean
        get() = prefs.getBoolean("barge_in", true)
        set(value) = prefs.edit().putBoolean("barge_in", value).apply()

    // ====== How she addresses the user ======''')
save(p)

# ---- Service: switch drives the session (Echo Guard = the old "never stop" choice) ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''            muteMicWhileSpeaking = prefs.isEchoGuardEnabled || prefs.isScreenRecordingMode,''',
    '''            muteMicWhileSpeaking = !prefs.isBargeIn || prefs.isScreenRecordingMode,''')
rep('''            micGain = com.faiza.ai.ai.live.MicBoost.gainFor(prefs.micBoostLevel)
        ).also { it.start(liveGreeting) }''', '''            micGain = com.faiza.ai.ai.live.MicBoost.gainFor(prefs.micBoostLevel),
            bargeIn = prefs.isBargeIn
        ).also { it.start(liveGreeting) }''')
save(p)

# ---- Settings: the switch, kept in step with Echo Guard (they are opposites) ----
apply("rowLiveVoice", [
    row("bargeIn", "ic_mic", "green", "Stop when I talk",
        "On: talk over her and she stops to listen", "switch"),
])
p = base + r'\ui\SettingsActivity.kt'
load(p)
rep('''        swEcho.isChecked = prefs.isEchoGuardEnabled''', '''        swEcho.isChecked = !prefs.isBargeIn''')
rep('''            prefs.isEchoGuardEnabled = isChecked''', '''            prefs.isEchoGuardEnabled = isChecked
            prefs.isBargeIn = !isChecked
            findViewById<SwitchMaterial>(R.id.swBargeIn).isChecked = !isChecked''')
rep('''        val swLoud = findViewById<SwitchMaterial>(R.id.swLouderVoice)''', '''        // "Stop when I talk" — one clear switch for interrupting her (Echo Guard is its opposite).
        val swBarge = findViewById<SwitchMaterial>(R.id.swBargeIn)
        swBarge.isChecked = prefs.isBargeIn
        swBarge.setOnCheckedChangeListener { _, on ->
            prefs.isBargeIn = on
            prefs.isEchoGuardEnabled = !on
            findViewById<SwitchMaterial>(R.id.swEchoGuard).isChecked = !on
            findViewById<TextView>(R.id.subBargeIn).text =
                if (on) "On: talk over her and she stops to listen" else "Off: she always finishes her sentence"
            FloatingOrbService.instance?.onAudioSettingsChanged()
        }
        findViewById<TextView>(R.id.subBargeIn).text =
            if (prefs.isBargeIn) "On: talk over her and she stops to listen" else "Off: she always finishes her sentence"
        findViewById<View>(R.id.rowBargeIn).setOnClickListener { swBarge.toggle() }

        val swLoud = findViewById<SwitchMaterial>(R.id.swLouderVoice)''')
save(p)
print("ok")
