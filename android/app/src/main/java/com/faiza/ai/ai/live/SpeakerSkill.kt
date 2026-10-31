package com.faiza.ai.ai.live

import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require

/** "লাউডস্পিকারে কথা বলো" → loudspeaker; "আস্তে / কানের কাছে" → back to the call stream. */
object SpeakerSkill {
    fun skill() = Skill(
        "speaker_output", "🔊", "Loudspeaker", "\"লাউডস্পিকারে কথা বলো\"",
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
