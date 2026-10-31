package com.faiza.ai

import com.faiza.ai.ui.TestChecklistActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestChecklistTest {
    @Test fun idsAreUniqueAndCoverTheNewFeatures() {
        val ids = TestChecklistActivity.SECTIONS.flatMap { s -> s.second.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        listOf("setup", "wake", "callreply", "voicenote", "prayer", "khata", "bkash", "homework", "medicine", "offline", "ff", "ffreport", "driving", "backup")
            .forEach { assertTrue(it, it in ids) }
    }
}
