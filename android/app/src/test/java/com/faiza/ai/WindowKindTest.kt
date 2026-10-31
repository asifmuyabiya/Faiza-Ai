package com.faiza.ai

import com.faiza.ai.ui.windows.WindowKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowKindTest {
    @Test fun namesAndAliases() {
        assertEquals(WindowKind.CAMERA, WindowKind.from("camera"))
        assertEquals(WindowKind.BROWSER, WindowKind.from("Research"))
        assertEquals(WindowKind.BROWSER, WindowKind.from("google"))
        assertEquals(WindowKind.CALCULATOR, WindowKind.from(" calc "))
        assertEquals(WindowKind.CARD, WindowKind.from("weather"))
        assertEquals(WindowKind.MUSIC, WindowKind.from("song"))
        assertEquals(WindowKind.TASKS, WindowKind.from("progress"))
        assertNull(WindowKind.from("whatsapp"))
        assertNull(WindowKind.from(null))
    }
}
