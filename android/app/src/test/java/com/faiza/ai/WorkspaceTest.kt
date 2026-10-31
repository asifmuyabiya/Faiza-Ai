package com.faiza.ai

import com.faiza.ai.ai.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTest {
    @Test
    fun everyRoomIsComplete() {
        assertEquals(5, Workspace.values().size)
        assertEquals(Workspace.values().size, Workspace.values().map { it.id }.toSet().size)
        Workspace.values().forEach {
            assertEquals(3, it.chips.size) // the chat screen has three starter chips
            assertTrue(it.instruction.length > 80)
            assertEquals(it, Workspace.from(it.id))
        }
        assertTrue(Workspace.MARKETS.instruction.contains("search_web"))
        assertNull(Workspace.from("nope"))
    }
}
