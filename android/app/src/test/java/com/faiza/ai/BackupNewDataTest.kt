package com.faiza.ai

import com.faiza.ai.ai.memory.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupNewDataTest {

    @Test fun newSectionsRoundTrip() {
        val json = """
            {"app":"faiza-ai","version":1,"createdAt":1,
             "memory":{"facts":[],"log":[]},
             "khata":[{"id":"a","at":5,"type":"GAVE","amount":500.0,"person":"Karim","note":"","source":"voice"},
                      {"id":"b","at":6,"amount":10.0},
                      {"id":"c","at":7,"type":"EXPENSE","amount":0.0}],
             "medicineJson":"[{\"x\":1}]",
             "extras":{"prayerHanafi":true,"hijriAdjust":-1,"homeAddress":"Mirpur 10","gamingTalk":"NORMAL"}}
        """.trimIndent()
        val b = BackupManager.read(json)
        assertEquals(1, b.khata?.size) // the entry without a type and the 0-taka one are dropped
        assertEquals("Karim", b.khata!![0].person)
        assertEquals("[{\"x\":1}]", b.medicineJson)
        assertEquals(-1, b.extras?.hijriAdjust)
        assertEquals("Mirpur 10", b.extras?.homeAddress)
    }

    @Test fun oldBackupStillLoads() {
        val b = BackupManager.read("""{"app":"faiza-ai","version":1,"createdAt":1,"memory":{"facts":[],"log":[]}}""")
        assertNull(b.khata); assertNull(b.extras); assertNull(b.medicineJson)
    }
}
