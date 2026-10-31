package com.faiza.ai

import com.faiza.ai.ai.memory.BackupManager
import com.faiza.ai.ai.memory.SensitiveFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemoryPrivacyTest {

    @Test
    fun secretsAreDetected() {
        listOf(
            "my password is tiger123",
            "The OTP is 482910",
            "my bkash pin is 4821",
            "pin 7788",
            "card number 4111 1111 1111 1111",
            "4111111111111111",
            "CVV 123",
            "আমার পাসওয়ার্ড হলো abc123",
            "ওটিপি 556677",
            "আমার পিন 1234",
            "मेरा पासवर्ड xyz है",
            "verification code 99881"
        ).forEach { assertTrue("should be secret: $it", SensitiveFilter.isSensitive(it)) }
    }

    @Test
    fun normalThingsAreNotSecrets() {
        listOf(
            "My sister's name is Rima",
            "I love biryani",
            "Chand's number is 01712345678",
            "pin this chat to the top",
            "I'm learning to code in Kotlin",
            "My birthday is 12 March 1999",
            "আমার প্রিয় খাবার বিরিয়ানি"
        ).forEach { assertFalse("should NOT be secret: $it", SensitiveFilter.isSensitive(it)) }
    }

    @Test
    fun redactionHidesTheSecretButKeepsTheSentence() {
        val out = SensitiveFilter.redact("ok my password is tiger123 thanks")
        assertFalse(out.contains("tiger123"))
        assertTrue(out.contains("password"))
        assertTrue(out.contains("[hidden]"))

        val card = SensitiveFilter.redact("pay with 4111 1111 1111 1111 please")
        assertFalse(card.contains("4111"))

        val otp = SensitiveFilter.redact("ওটিপি 556677 এসেছে")
        assertFalse(otp.contains("556677"))
    }

    @Test
    fun validBackupIsRead() {
        val json = """
            {"app":"faiza-ai","version":1,"createdAt":1700000000000,
             "profile":{"userName":"Rahim","assistantName":"Maya"},
             "memory":{"facts":[{"id":"a","text":"User likes tea","createdAt":1},{"text":"  "},{"text":"No id fact","createdAt":2}],
                       "log":[{"at":1,"who":"user","text":"hi"},{"at":2,"text":"missing who"}]}}
        """.trimIndent()
        val b = BackupManager.read(json)
        assertEquals("Maya", b.profile.assistantName)
        assertEquals(listOf("User likes tea", "No id fact"), b.memory.facts.map { it.text })
        assertTrue(b.memory.facts.all { it.id.isNotBlank() })
        assertEquals(1, b.memory.log.size)
    }

    @Test
    fun missingSectionsDontCrash() {
        val b = BackupManager.read("""{"app":"faiza-ai","version":1}""")
        assertEquals(0, b.memory.facts.size)
        assertEquals(null, b.profile.userName)
    }

    @Test
    fun foreignFilesAreRejected() {
        listOf("not json", """{"app":"other"}""", """{"app":"faiza-ai","version":99}""").forEach {
            try {
                BackupManager.read(it)
                fail("should reject: $it")
            } catch (_: BackupManager.InvalidBackupException) {
            }
        }
    }
}
