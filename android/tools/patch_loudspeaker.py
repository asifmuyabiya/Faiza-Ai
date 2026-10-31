import os, sys
android = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android'
main = os.path.join(android, 'app', 'src', 'main', 'java', 'com', 'faiza', 'ai')
sys.path.insert(0, os.path.join(android, 'tools'))


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


p = 'ai/live/GeminiLiveSession.kt'
edit(p, '''    private val gamingFast: Boolean = false
) {''', '''    private val gamingFast: Boolean = false,
    /**
     * Owner: "WhatsApp/imo ভিডিও কলের মতো লাউডস্পিকারে কথা বলবে". Without a headset her voice always goes to the
     * loudspeaker — never the small ear speaker at the top of the phone.
     */
    private val loudspeaker: Boolean = true
) {''')
# The call-stream-without-call-mode trick sends sound to the EAR speaker on Honor/Huawei: not with loudspeaker on.
edit(p, '''            if (callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {
                liftCallVolume()
                callStreamOnly = true
                return
            }''', '''            if (callSpeaker && !forceSpeaker && !headsetConnected() && bargeIn && !muteMicWhileSpeaking) {
                if (loudspeaker) return // media stream → loudspeaker, mic stays open for "stop when I talk"
                liftCallVolume()
                callStreamOnly = true
                return
            }''')
edit(p, '''        t.play()
        loudness?.runCatching { release() }''', '''        // No headset: tell Android to use the loudspeaker for her voice, whatever the stream.
        if (loudspeaker && !routedBluetooth && !headsetConnected()) runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { t.setPreferredDevice(it) }
        }
        t.play()
        loudness?.runCatching { release() }''')

p = 'service/FloatingOrbService.kt'
edit(p, '''            bargeIn = prefs.isBargeIn,
            gamingFast = gaming''', '''            bargeIn = prefs.isBargeIn,
            gamingFast = gaming,
            loudspeaker = prefs.isLoudspeaker''')
edit(p, '''            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice" -> if (live != null) restartLive()''',
     '''            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice", "loudspeaker" -> if (live != null) restartLive()''')

edit('util/PrefsManager.kt', '''    /** 0 off, 1 soft voices, 2 whispers (MicBoost). */''', '''    /** Her voice on the loudspeaker (like a WhatsApp video call), never the ear speaker. */
    var isLoudspeaker: Boolean
        get() = prefs.getBoolean("loudspeaker", true)
        set(value) = prefs.edit().putBoolean("loudspeaker", value).apply()
    /** 0 off, 1 soft voices, 2 whispers (MicBoost). */''')

edit('ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowCallSpeaker).setOnClickListener { swCall.toggle() }''', '''        findViewById<View>(R.id.rowCallSpeaker).setOnClickListener { swCall.toggle() }
        val swLoudspeaker = findViewById<SwitchMaterial>(R.id.swLoudspeaker)
        swLoudspeaker.isChecked = prefs.isLoudspeaker
        swLoudspeaker.setOnCheckedChangeListener { _, on -> prefs.isLoudspeaker = on }
        findViewById<View>(R.id.rowLoudspeaker).setOnClickListener { swLoudspeaker.toggle() }''')

# Voice: "লাউডস্পিকারে কথা বলো" / "কানের স্পিকারে বলো".
open(os.path.join(main, 'ai', 'live', 'SpeakerSkill.kt'), 'w', encoding='utf-8').write('''package com.faiza.ai.ai.live

import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require

/** "লাউডস্পিকারে কথা বলো" → loudspeaker; "আস্তে / কানের কাছে" → back to the call stream. */
object SpeakerSkill {
    fun skill() = Skill(
        "speaker_output", "🔊", "Loudspeaker", "\\"লাউডস্পিকারে কথা বলো\\"",
        "Where your voice comes out. loud = the phone's loudspeaker, like a WhatsApp/imo video call (default); " +
                "normal = the older call-volume mode. Use when the user says they can't hear you well, or asks for the " +
                "loudspeaker / speaker phone.",
        listOf(ToolParam("mode", ParamType.STRING, "loud | normal"))
    ) { _, args ->
        val loud = args.require("mode").lowercase() != "normal"
        FaizaApp.instance.prefs.isLoudspeaker = loud
        // The voice chat restarts with the new output in a moment.
        if (loud) "OK — switching to the loudspeaker now. Say one short line." else "OK — back to the call-volume speaker."
    }
}
''')
p = 'ai/FaizaAgent.kt'
edit(p, '''        if ("khata" !in disabled) skills += com.faiza.ai.ai.khata.Khata.skill()''', '''        if ("khata" !in disabled) skills += com.faiza.ai.ai.khata.Khata.skill()
        skills += com.faiza.ai.ai.live.SpeakerSkill.skill()''')

import settings_rows as sr
sr.apply('rowCallSpeaker', [sr.row('loudspeaker', 'ic_volume_off', 'blue', 'Loudspeaker (like a video call)',
                                   'Her voice always on the loud speaker, never the ear speaker', 'switch')])
print('ok')
