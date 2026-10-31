package com.faiza.ai

import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.FEMALE_VOICES
import com.faiza.ai.ai.MALE_VOICES
import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.Phrases
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.VOICE_TRAITS
import com.faiza.ai.ai.skills.CustomSkill
import com.faiza.ai.ai.skills.SkillStore
import com.faiza.ai.ai.voiceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Things added after re-watching MAYA 3.0: languages, voice names, skill store. */
class MayaGapTest {

    @Test fun everyLanguageHasFixedPhrases() {
        assertTrue(AppLanguage.values().size >= 20)
        assertEquals(PhraseBase.BENGALI, AppLanguage.from("banglish").base)
        assertEquals(PhraseBase.HINDI, AppLanguage.from("ur").base)
        assertEquals(PhraseBase.ENGLISH, AppLanguage.from("ta").base)
        assertEquals(AppLanguage.ENGLISH, AppLanguage.from("unknown"))
        // Old saved codes keep working.
        assertEquals(AppLanguage.BENGALI, AppLanguage.from("bn"))
        assertEquals(AppLanguage.HINDI, AppLanguage.from("hi"))
        for (l in AppLanguage.values()) {
            assertTrue(l.name, Phrases.wakeReply(Persona.FAIZA, l).isNotBlank())
            assertTrue(l.name, Phrases.didntHear(l).isNotBlank())
            assertTrue(l.name, l.promptName.isNotBlank())
        }
        assertEquals("All language codes are unique", AppLanguage.values().size, AppLanguage.values().map { it.code }.toSet().size)
    }

    @Test fun everyVoiceHasACharacter() {
        (FEMALE_VOICES + MALE_VOICES).forEach { assertTrue(it, it in VOICE_TRAITS) }
        assertEquals("Breezy · Aoede", voiceLabel("Aoede"))
        assertEquals("Mystery", voiceLabel("Mystery"))
    }

    @Test fun skillStoreIsComplete() {
        val ids = SkillStore.BUILT_IN.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SkillStore.BUILT_IN.forEach {
            assertTrue(it.name, it.trigger.isNotBlank() && it.instructions.length > 40 && it.description.isNotBlank())
        }
        val s = SkillStore.BUILT_IN.first()
        assertFalse(SkillStore.isInstalled(emptyList(), s))
        val installed = listOf(SkillStore.install(s))
        assertTrue(SkillStore.isInstalled(installed, s))
        assertTrue(SkillStore.isInstalled(listOf(CustomSkill(name = s.name.uppercase(), trigger = "x", instructions = "y")), s))
    }

    @Test fun quietModeDecisions() {
        val q = com.faiza.ai.util.QuietMode
        // Manual quiet: silent and not listening.
        q.decide(true, 0, 1000, followPhone = false, phoneSilent = false, allowHeadphones = true, headphones = false).let {
            assertTrue(it.blocksSpeech); assertTrue(it.blocksListening)
        }
        // ...but earphones in: announcements are private, still allowed; listening stays off.
        q.decide(true, 0, 1000, false, false, allowHeadphones = true, headphones = true).let {
            assertFalse(it.blocksSpeech); assertTrue(it.blocksListening)
        }
        // Timed quiet runs out.
        q.decide(true, until = 500, now = 1000, followPhone = false, phoneSilent = false, allowHeadphones = false, headphones = false).let {
            assertFalse(it.blocksSpeech); assertFalse(it.blocksListening)
        }
        // Phone on silent: no unprompted speech, but the wake word keeps working.
        q.decide(false, 0, 1000, followPhone = true, phoneSilent = true, allowHeadphones = false, headphones = false).let {
            assertTrue(it.blocksSpeech); assertFalse(it.blocksListening)
        }
        // Not following the phone: normal.
        assertFalse(q.decide(false, 0, 1000, followPhone = false, phoneSilent = true, allowHeadphones = false, headphones = false).blocksSpeech)
    }
}
