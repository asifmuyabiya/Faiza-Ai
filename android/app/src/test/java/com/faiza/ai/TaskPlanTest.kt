package com.faiza.ai

import com.faiza.ai.ai.skills.Plan
import com.faiza.ai.ai.skills.PlanStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPlanTest {

    private val owner = "WhatsApp: Faiza-কে লেখো 'তোমার আসতে কতক্ষণ লাগবে?' | আম্মুকে লেখো 'আম্মু আমি একটু ব্যস্ত, বিকাশে টাকাটা একটু পরে দিচ্ছি' | " +
            "ভাইয়াকে লেখো 'ভাইয়া মালের প্রাইসটা একটু কমানো যাবে?' | খালাতো ভাইকে লেখো 'আপনি কখন আসবেন?'"

    @Test
    fun parsesStepsAnyWay() {
        assertEquals(4, Plan.parse(owner).size)
        assertEquals(listOf("open WhatsApp", "post story", "delete photos"), Plan.parse("1. open WhatsApp\n2) post story\n৩. delete photos"))
        assertTrue(Plan.parse(" | | ").isEmpty())
        assertEquals(Plan.MAX_STEPS, Plan.parse((1..100).joinToString(" | ") { "step $it" }).size)
    }

    @Test
    fun walksThroughTheStepsInOrder() {
        var p = Plan(Plan.parse(owner).map { PlanStep(it) })
        assertFalse(p.running) // not confirmed yet
        p = p.copy(confirmed = true)
        assertTrue(p.running)
        assertEquals(0, p.current)
        assertTrue(p.nextInstruction().startsWith("STEP 1 OF 4"))
        assertTrue(p.nextInstruction().contains("Do not ask for confirmation"))
        p = p.complete("sent to Faiza", ok = true)
        assertEquals(1, p.current)
        assertTrue(p.nextInstruction().contains("STEP 2 OF 4"))
        assertTrue(p.nextInstruction().contains("Still to do"))
        p = p.complete("sent to Ammu", ok = true)
        p = p.complete("Bhaiya not found", ok = false)
        assertTrue(p.nextInstruction().contains("This is the last task"))
        p = p.complete("sent", ok = true)
        assertTrue(p.finished)
        assertFalse(p.running)
        val end = p.nextInstruction()
        assertTrue(end.startsWith("ALL 4 TASKS FINISHED (1 could not be done)"))
        assertTrue(end.contains("✗") && end.contains("Bhaiya not found"))
    }
}
