package com.faiza.ai.ai.skills

import com.faiza.ai.util.PrefsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** A user-taught routine: when the user says something like [trigger], follow [instructions]. */
data class CustomSkill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trigger: String,
    val instructions: String,
    val enabled: Boolean = true
)

/** A specialist the main assistant can hand a task to; it works with its own instructions and memory-free context. */
data class SubAgent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val instructions: String
)

object UserSkillStore {

    private val gson = Gson()

    private val DEFAULT_SKILLS = listOf(
        CustomSkill(
            name = "Good Morning",
            trigger = "good morning / শুভ সকাল",
            instructions = "Greet me warmly, tell me today's date and my battery level, " +
                    "then summarize my latest 3 notifications in one or two sentences."
        ),
        CustomSkill(
            name = "Good Night",
            trigger = "good night / শুভ রাত্রি",
            instructions = "Set an alarm for 7:00 AM, set the volume to 20 percent, and wish me good night."
        )
    )

    private val DEFAULT_AGENTS = listOf(
        SubAgent(
            name = "Translator", emoji = "🌐",
            instructions = "You are an expert translator. Translate the given text accurately into the " +
                    "requested language, keeping the tone. Reply with only the translation."
        ),
        SubAgent(
            name = "Message Writer", emoji = "✍️",
            instructions = "You write short, polite, natural messages (SMS, WhatsApp, email) for the user " +
                    "based on their instructions. Reply with only the message text."
        ),
        SubAgent(
            name = "Study Tutor", emoji = "📚",
            instructions = "You are a patient tutor for students. Explain the topic simply, step by step, " +
                    "in at most 5 sentences, with one everyday example."
        )
    )

    fun customSkills(prefs: PrefsManager): List<CustomSkill> {
        val json = prefs.customSkillsJson ?: return DEFAULT_SKILLS.also { saveCustomSkills(prefs, it) }
        return runCatching {
            gson.fromJson<List<CustomSkill>>(json, object : TypeToken<List<CustomSkill>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun saveCustomSkills(prefs: PrefsManager, skills: List<CustomSkill>) {
        prefs.customSkillsJson = gson.toJson(skills)
    }

    fun subAgents(prefs: PrefsManager): List<SubAgent> {
        val json = prefs.subAgentsJson ?: return DEFAULT_AGENTS.also { saveSubAgents(prefs, it) }
        return runCatching {
            gson.fromJson<List<SubAgent>>(json, object : TypeToken<List<SubAgent>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun saveSubAgents(prefs: PrefsManager, agents: List<SubAgent>) {
        prefs.subAgentsJson = gson.toJson(agents)
    }
}
