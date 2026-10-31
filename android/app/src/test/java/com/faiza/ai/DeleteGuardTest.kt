package com.faiza.ai

import com.faiza.ai.ai.PhraseBase
import com.faiza.ai.ai.guardian.DeleteGuard
import com.faiza.ai.ai.guardian.DeleteGuard.Level
import com.faiza.ai.ai.routines.FaizaTimers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteGuardTest {

    @Test
    fun recognisesDeleteConfirmations() {
        // Gallery: one photo
        assertEquals(Level.NORMAL, DeleteGuard.analyze(listOf("Delete this photo?"), listOf("Cancel", "Delete")))
        // Many items / permanent / chats / uninstall / factory reset → strong warning
        assertEquals(Level.IMPORTANT, DeleteGuard.analyze(listOf("Delete 25 items?"), listOf("Cancel", "Delete")))
        assertEquals(Level.IMPORTANT, DeleteGuard.analyze(listOf("These files will be permanently deleted."), listOf("Cancel", "Delete")))
        assertEquals(Level.IMPORTANT, DeleteGuard.analyze(listOf("Delete this chat?"), listOf("Cancel", "Delete chat")))
        assertEquals(Level.IMPORTANT, DeleteGuard.analyze(listOf("Do you want to uninstall this app?"), listOf("Cancel", "OK", "Uninstall")))
        assertEquals(Level.IMPORTANT, DeleteGuard.analyze(listOf("Erase all data (factory reset)"), listOf("Cancel", "Erase all data")))
        // Bangla dialog
        assertEquals(Level.NORMAL, DeleteGuard.analyze(listOf("এই ছবিটি মুছবেন?"), listOf("বাতিল", "মুছুন")))
        // Duplicates are fine
        assertEquals(Level.DUPLICATE, DeleteGuard.analyze(listOf("Delete 3 duplicate photos?"), listOf("Cancel", "Delete")))
    }

    @Test
    fun ignoresOrdinaryScreens() {
        assertEquals(Level.NONE, DeleteGuard.analyze(listOf("Welcome back"), listOf("OK")))
        assertEquals(Level.NONE, DeleteGuard.analyze(listOf("Inbox", "Hello there"), listOf("Compose", "Search")))
        // A delete button on a normal screen with no question / cancel is not a confirmation
        assertEquals(Level.NONE, DeleteGuard.analyze(listOf("Photo details"), listOf("Share", "Edit", "Delete")))
    }

    @Test
    fun linesInEveryLanguage() {
        PhraseBase.values().forEach { b ->
            Level.values().filter { it != Level.NONE }.forEach { assertTrue(DeleteGuard.line(it, b).isNotBlank()) }
        }
        assertTrue(DeleteGuard.line(Level.IMPORTANT, PhraseBase.BENGALI).contains("আরেকবার চেক"))
    }

    @Test
    fun timerDurations() {
        assertEquals("15 seconds", FaizaTimers.durationText(15))
        assertEquals("1 minute", FaizaTimers.durationText(60))
        assertEquals("2 minutes 30 seconds", FaizaTimers.durationText(150))
        assertEquals("1 hour 5 minutes", FaizaTimers.durationText(3900))
    }
}
