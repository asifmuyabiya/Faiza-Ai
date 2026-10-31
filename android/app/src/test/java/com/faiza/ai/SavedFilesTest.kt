package com.faiza.ai

import com.faiza.ai.ai.files.SavedFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFilesTest {
    @Test fun sizes() {
        assertEquals("512 B", SavedFiles.sizeText(512))
        assertEquals("12 KB", SavedFiles.sizeText(12 * 1024))
        assertEquals("2.5 MB", SavedFiles.sizeText((2.5 * 1_048_576).toLong()))
    }
}
