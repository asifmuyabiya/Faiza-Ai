package com.faiza.ai

import com.faiza.ai.ai.skills.TaskLanes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskLanesTest {
    @Test
    fun lanesAndScreenRules() {
        // Screen-driving actions share one lane (one screen), web work runs in parallel.
        listOf("whatsapp_message", "open_app", "take_photo", "call_contact").forEach {
            assertEquals(it, TaskLanes.Lane.PHONE, TaskLanes.laneOf(it))
        }
        listOf("search_web", "latest_news", "get_weather", "generate_image").forEach {
            assertEquals(it, TaskLanes.Lane.WEB, TaskLanes.laneOf(it))
        }
        // Quick or result-needed tools stay direct.
        listOf("read_screen", "tap_on", "time_now", "plan_tasks", "flashlight").forEach { assertNull(it, TaskLanes.laneOf(it)) }
        // Looking at / touching the screen waits for earlier phone work.
        assertTrue(TaskLanes.needsScreen("read_screen"))
        assertTrue(TaskLanes.needsScreen("type_text"))
        assertTrue(TaskLanes.needsScreen("step_done"))
        assertFalse(TaskLanes.needsScreen("search_web"))
        assertFalse(TaskLanes.busy)
    }
}
