package com.faiza.ai

import com.faiza.ai.ai.AppLanguage
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.SpeakingStyle
import com.faiza.ai.ai.files.FilePlanner
import com.faiza.ai.ai.files.FilePlanner.Entry
import com.faiza.ai.util.UpdateChecker
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Features from the STONIC video (files, speaking styles) + the update check. */
class StonicFeaturesTest {

    @Test fun organizePlanGroupsLooseFilesOnly() {
        val plan = FilePlanner.organizePlan(listOf(
            Entry("IMG_001.jpg", false), Entry("Bill.PDF", false), Entry("song.mp3", false),
            Entry("faiza.apk", false), Entry("big.zip.crdownload", false), Entry(".nomedia", false),
            Entry("Images", true), Entry("random.xyz", false), Entry("clip.mp4", false), Entry("data.csv", false)
        ))
        assertEquals(listOf("Images", "Videos", "Audio", "Documents", "APKs", "Others"), plan.keys.toList())
        assertEquals(listOf("Bill.PDF", "data.csv"), plan["Documents"])
        assertFalse("unfinished downloads stay", plan.values.flatten().contains("big.zip.crdownload"))
        assertFalse("hidden files stay", plan.values.flatten().contains(".nomedia"))
        assertEquals("1 → Images, 1 → Videos, 1 → Audio, 2 → Documents, 1 → APKs, 1 → Others", FilePlanner.describePlan(plan))
    }

    @Test fun freeNamesAndSafeNames() {
        val taken = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", FilePlanner.freeName("report.pdf") { it in taken })
        assertEquals("new.pdf", FilePlanner.freeName("new.pdf") { it in taken })
        assertEquals("Clients_ 2026.csv", FilePlanner.safeFileName("Clients/ 2026", "csv"))
        assertEquals("notes.md", FilePlanner.safeFileName("notes.md", "txt"))
        assertEquals("Faiza note.txt", FilePlanner.safeFileName("  ", "txt"))
    }

    @Test fun findsFilesByWords() {
        assertTrue(FilePlanner.nameMatches("resume pdf", "My_Resume_2026.pdf"))
        assertTrue(FilePlanner.nameMatches("", "anything.txt"))
        assertFalse(FilePlanner.nameMatches("resume pdf", "Resume.docx"))
        assertTrue(FilePlanner.nameMatches("বিল", "বিদ্যুৎ বিল মার্চ.pdf"))
        assertEquals("application/pdf", FilePlanner.mimeType("x.PDF"))
        assertEquals("2.5 MB", FilePlanner.formatSize(2_621_440))
    }

    @Test fun speakingStylesPickMatchingVoices() {
        assertEquals(SpeakingStyle.SLEEPY, SpeakingStyle.from("sleepy"))
        assertEquals(SpeakingStyle.NORMAL, SpeakingStyle.from("nonsense"))
        assertEquals("Achernar", SpeakingStyle.SLEEPY.voiceFor(Persona.FAIZA))
        assertEquals("Algenib", SpeakingStyle.SLEEPY.voiceFor(Persona.VENOM))
        assertNull(SpeakingStyle.NORMAL.voiceFor(Persona.FAIZA))
        assertEquals("Gacrux", SpeakingStyle.from("husky_love").voiceFor(Persona.FAIZA))
        assertTrue(SpeakingStyle.HUSKY_LOVE.isTender)
        assertFalse(SpeakingStyle.BUTLER.isTender)
        // The loving styles are slower and lower than her normal voice.
        for (s in SpeakingStyle.values().filter { it.isTender }) assertTrue(s.rateFactor < 0.9f)
        // The sweet style is a light, high young woman's voice; the husky ones are low.
        assertEquals("Leda", SpeakingStyle.SWEET_LOVE.voiceFor(Persona.FAIZA))
        assertTrue(SpeakingStyle.SWEET_LOVE.pitchFactor > 1f && SpeakingStyle.HUSKY_LOVE.pitchFactor < 0.9f)
        // Every style has a sample line in every language, and the chosen voices exist.
        for (s in SpeakingStyle.values()) {
            for (l in AppLanguage.values()) assertTrue(SpeakingStyle.previewLine(s, l, "Faiza").isNotBlank())
            s.femaleVoice?.let { assertTrue(it, it in Persona.FAIZA.liveVoiceChoices) }
            s.maleVoice?.let { assertTrue(it, it in Persona.VENOM.liveVoiceChoices) }
        }
    }

    @Test fun creatorAndStayWithMeLines() {
        // The maker is always Mohammad Asif Muaviya; only he is told "you made me" and gets the romantic answers.
        val him = com.faiza.ai.ai.HeartLines.promptSection("Asif", romantic = true, girlfriendMode = true, random = kotlin.random.Random(1))
        assertTrue(him.contains("created by Mohammad Asif Muaviya"))
        assertTrue(him.contains("আমাকে বানিয়েছে মোহাম্মদ আসিফ মুয়াবিয়া"))
        assertTrue(him.contains("another girlfriend"))
        assertFalse(him.contains("{"))
        val other = com.faiza.ai.ai.HeartLines.promptSection("Rahim", romantic = true, girlfriendMode = false, random = kotlin.random.Random(1))
        assertTrue(other.contains("created by Mohammad Asif Muaviya"))
        assertFalse("no romance for someone else", other.contains("কবিতা"))
        val plain = com.faiza.ai.ai.HeartLines.promptSection("  ", romantic = false, girlfriendMode = false)
        assertTrue(plain.contains("Mohammad Asif Muaviya"))
        assertFalse("no flirting without girlfriend mode", plain.contains("another girlfriend"))
        assertFalse(plain.contains("কবিতা"))
    }

    @Test fun updateInfoIsParsedSafely() {
        val info = UpdateChecker.parse(JsonParser.parseString(
            """{"versionCode":5,"versionName":"3.2.0","url":"https://x.com/f.apk","notes":"New","minSupported":3}"""
        ).asJsonObject)!!
        assertTrue(UpdateChecker.isNewer(4, info))
        assertFalse(UpdateChecker.isNewer(5, info))
        assertTrue(UpdateChecker.isRequired(2, info))
        assertFalse(UpdateChecker.isRequired(3, info))
        val noUrl = UpdateChecker.parse(JsonParser.parseString("""{"versionCode":9}""").asJsonObject)!!
        assertFalse("no download link = nothing to offer", UpdateChecker.isNewer(1, noUrl))
        assertNull(UpdateChecker.parse(JsonParser.parseString("""{"x":1}""").asJsonObject))
    }
}
