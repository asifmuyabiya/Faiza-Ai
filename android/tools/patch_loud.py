import os, sys
sys.path.insert(0, os.path.dirname(__file__))
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


patch(J + "ai/live/GeminiLiveSession.kt", [
 ("""    /** Screen-recording mode: always use the loudspeaker so a recording can pick her voice up. */
    private val forceSpeaker: Boolean = false
) {""", """    /** Screen-recording mode: always use the loudspeaker so a recording can pick her voice up. */
    private val forceSpeaker: Boolean = false,
    /** Boost her voice a little (+6 dB) on the loudspeaker. */
    private val louderVoice: Boolean = true
) {"""),
 ("""        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )""", """        // On the phone's own speaker she plays on the MEDIA stream: loud like a WhatsApp call on speaker, and the
        // volume buttons work. (The call stream, without a real call, is quiet and stuck at the call volume.)
        // A Bluetooth headset keeps the call stream, which is what carries audio to it.
        val usage = if (routedBluetooth) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )"""),
 ("""        t.play()
        track = t
        scope.launch(Dispatchers.IO) {
            for (chunk in playback) {""", """        t.play()
        track = t
        if (louderVoice && !routedBluetooth) {
            loudness = runCatching {
                android.media.audiofx.LoudnessEnhancer(t.audioSessionId).apply {
                    setTargetGain(600) // +6 dB
                    enabled = true
                }
            }.getOrNull()
        }
        scope.launch(Dispatchers.IO) {
            for (chunk in playback) {"""),
 ("""    private var track: AudioTrack? = null""", """    private var track: AudioTrack? = null
    private var loudness: android.media.audiofx.LoudnessEnhancer? = null"""),
 ("""        track?.runCatching { pause(); flush(); release() }
        track = null""", """        loudness?.runCatching { release() }
        loudness = null
        track?.runCatching { pause(); flush(); release() }
        track = null"""),
])

patch(J + "util/PrefsManager.kt", [
 ("""    /** How Faiza sounds (see SpeakingStyle). */""", """    /** Small extra boost for her voice on the loudspeaker. */
    var isLouderVoice: Boolean
        get() = prefs.getBoolean("louder_voice", true)
        set(value) = prefs.edit().putBoolean("louder_voice", value).apply()

    /** How Faiza sounds (see SpeakingStyle). */"""),
])

patch(J + "service/FloatingOrbService.kt", [
 ("""            forceSpeaker = prefs.isScreenRecordingMode
        ).also { it.start(greeting) }""", """            forceSpeaker = prefs.isScreenRecordingMode,
            louderVoice = prefs.isLouderVoice
        ).also { it.start(greeting) }"""),
 ("""            PrefsManager.KEY_SCREEN_RECORDING -> if (live != null) restartLive()""",
  """            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice" -> if (live != null) restartLive()"""),
])

import settings_rows as sr
sr.apply("rowEchoGuard", [
    sr.row("louderVoice", "ic_campaign", "orange", "Louder voice", "Extra boost on the loudspeaker", "switch"),
])

patch(J + "ui/SettingsActivity.kt", [
 ("""        findViewById<View>(R.id.rowEchoGuard).setOnClickListener { swEcho.toggle() }""",
  """        findViewById<View>(R.id.rowEchoGuard).setOnClickListener { swEcho.toggle() }

        val swLoud = findViewById<SwitchMaterial>(R.id.swLouderVoice)
        swLoud.isChecked = prefs.isLouderVoice
        swLoud.setOnCheckedChangeListener { _, on -> prefs.isLouderVoice = on }
        findViewById<View>(R.id.rowLouderVoice).setOnClickListener { swLoud.toggle() }"""),
])
print("louder voice done")
