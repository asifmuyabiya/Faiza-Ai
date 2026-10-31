package com.faiza.ai

import com.faiza.ai.ai.macros.Macro
import com.faiza.ai.ai.macros.MacroRecorder
import com.faiza.ai.ai.macros.MacroStep
import com.faiza.ai.ai.macros.MacroStore
import com.faiza.ai.ai.macros.TapInfo
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Macros (video 33:47–39:10): voice recording, name matching, safe storage. */
class MacroTest {

    private fun args(vararg pairs: Pair<String, Any>) = JsonObject().apply {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> addProperty(k, v)
                is Int -> addProperty(k, v)
                is Boolean -> addProperty(k, v)
            }
        }
    }

    private fun MacroRecorder.ok(tool: String, a: JsonObject, result: String = "OK", tap: TapInfo? = null, app: String? = null) =
        onToolResult(tool, a, result, tap, 1080, 2400, app)

    @Test fun recordsTheVideoExampleStepByStep() {
        val r = MacroRecorder()
        r.start("ChatGPT image")
        assertEquals(1, r.ok("open_app", args("app_name" to "chatgpt"), "OK: opened ChatGPT"))
        assertNull("reading the screen is not a step", r.ok("read_screen", JsonObject(), "App: ..."))
        assertEquals(2, r.ok("tap_element", args("number" to 7), "OK: tapped [7] Message",
            TapInfo("Message", editable = true, xPct = 0.5f, yPct = 0.9f)))
        assertEquals(3, r.ok("type_text", args("text" to "a cat in space", "submit" to false), app = "ChatGPT"))
        assertEquals(4, r.ok("tap_on", args("text" to "send"), "OK: tapped \"Send message\"",
            TapInfo("Send message", editable = false, xPct = 0.92f, yPct = 0.9f)))
        val m = r.finish()!!
        assertFalse(r.isRecording)
        assertEquals("ChatGPT image", m.name)
        assertEquals(listOf("Open ChatGPT", "Tap “Message”", "Type your text in ChatGPT", "Tap “Send message”"),
            m.steps.map { it.description })
        assertTrue(m.steps[1].targetEditable)
        // Typing asks for new text every time by default (video 38:40).
        assertEquals(1, m.inputSteps.size)
        assertEquals("What should I type in ChatGPT?", m.inputSteps[0].prompt)
        assertEquals("a cat in space", m.steps[2].args().get("text").asString)
    }

    @Test fun failedAndUnsafeActionsAreNotRecorded() {
        val r = MacroRecorder()
        r.start("x")
        assertNull(r.ok("tap_on", args("text" to "Send"), "ERROR: nothing like \"Send\""))
        assertNull("messages always need a fresh yes", r.ok("whatsapp_message", args("contact" to "Mom", "message" to "hi")))
        assertNull(r.ok("activate_sos", JsonObject()))
        assertNull(r.ok("remember", args("fact" to "x")))
        assertNull("nothing recorded → nothing saved", r.finish())
    }

    @Test fun nothingIsRecordedWhenNotRecording() {
        val r = MacroRecorder()
        assertNull(r.ok("open_app", args("app_name" to "YouTube"), "OK: opened YouTube"))
    }

    @Test fun removeLastStepUndoes() {
        val r = MacroRecorder()
        r.start("notes")
        r.ok("open_app", args("app_name" to "keep"), "OK: opened Keep Notes")
        r.ok("scroll", args("direction" to "down", "times" to 2))
        assertEquals("Scroll down ×2", r.removeLast()!!.description)
        assertEquals(1, r.state.value!!.steps.size)
        r.removeLast()
        assertNull("no steps left", r.removeLast())
    }

    @Test fun tapPositionIsStoredAsScreenFraction() {
        val step = MacroRecorder.toStep("tap_position", args("x" to 540, "y" to 1200), "OK", null, 1080, 2400, null)!!
        assertEquals("tap", step.action)
        assertEquals(0.5f, step.xPct!!, 0.001f)
        assertEquals(0.5f, step.yPct!!, 0.001f)
        assertNull(step.target)
    }

    @Test fun findsMacrosBySpokenName() {
        val list = listOf(
            Macro(name = "Notes", steps = listOf(MacroStep("wait", description = "Wait 1 s"))),
            Macro(name = "ChatGPT image", steps = listOf(MacroStep("wait", description = "Wait 1 s"))),
            Macro(name = "শুভ সকাল", steps = listOf(MacroStep("wait", description = "Wait 1 s")))
        )
        assertEquals("Notes", MacroStore.find(list, "notes")?.name)
        assertEquals("Notes", MacroStore.find(list, "the notes macro")?.name)
        assertEquals("ChatGPT image", MacroStore.find(list, "chatgpt image")?.name)
        assertEquals("ChatGPT image", MacroStore.find(list, "Chat GPT image")?.name)
        assertEquals("শুভ সকাল", MacroStore.find(list, "শুভ সকাল ম্যাক্রো")?.name)
        assertNull(MacroStore.find(list, "weather"))
        assertNull(MacroStore.find(list, ""))
    }

    @Test fun storageSurvivesRoundTripAndDamage() {
        var raw: String? = null
        val m = Macro(name = "Notes", steps = listOf(MacroStep("open_app", "{\"app_name\":\"Keep\"}", "Open Keep")))
        MacroStore.save({ raw = it }, listOf(m))
        val back = MacroStore.load { raw }
        assertEquals(1, back.size)
        assertEquals("Keep", back[0].steps[0].args().get("app_name").asString)

        assertTrue(MacroStore.load { "not json" }.isEmpty())
        assertTrue(MacroStore.load { null }.isEmpty())
        val damaged = MacroStore.load { """[{"name":"A","steps":[{"action":"wait"}]},{"name":null,"steps":[]},{"name":"B","steps":[{"action":"wait","description":"Wait 1 s"}]}]""" }
        assertEquals(listOf("B"), damaged.map { it.name })
        assertNotNull(damaged[0].id)
        assertEquals("{}", damaged[0].steps[0].argsJson)
    }

    @Test fun inputsAreSplitPerTypingStep() {
        assertEquals(listOf("hello | world"), MacroRecorder.splitInputs("hello | world", 1))
        assertEquals(listOf("hello", "world"), MacroRecorder.splitInputs("hello | world", 2))
        assertTrue(MacroRecorder.splitInputs(null, 1).isEmpty())
        assertTrue(MacroRecorder.splitInputs("x", 0).isEmpty())
    }
}
