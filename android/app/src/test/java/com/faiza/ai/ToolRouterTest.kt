package com.faiza.ai

import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolCall
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.ToolRouter
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRouterTest {

    private fun skill(id: String, desc: String = "Does $id. More words here.", vararg p: ToolParam) =
        Skill(id, "x", id, "", desc, p.toList()) { _, _ -> "ran $id" }

    @Test fun coreStaysFullRestGoesToCatalog() {
        val all = listOf(skill("open_app"), skill("voice_note", "Reads WhatsApp voice messages. Long text…",
            ToolParam("who", ParamType.STRING, "sender", required = false)), skill("khata"))
        val (core, rest) = ToolRouter.split(all)
        assertEquals(listOf("open_app", "khata"), core.map { it.id })
        assertEquals(listOf("voice_note"), rest.map { it.id })
        val cat = ToolRouter.catalog(rest)
        assertTrue(cat.contains("- voice_note(who?) — Reads WhatsApp voice messages."))
        assertFalse(cat.contains("Long text"))
    }

    @Test fun unwrapsUseTool() {
        val args = JsonObject().apply { addProperty("name", "prayer"); addProperty("args_json", "{\"action\":\"qibla\"}") }
        val c = ToolRouter.unwrap(ToolCall("1", "use_tool", args))
        assertEquals("prayer", c.name)
        assertEquals("qibla", c.args.get("action").asString)
        assertEquals("1", c.id)
        // Already an object, or missing / broken JSON.
        val obj = JsonObject().apply { addProperty("name", "x"); add("args_json", JsonObject().apply { addProperty("a", 1) }) }
        assertEquals(1, ToolRouter.unwrap(ToolCall("2", "use_tool", obj)).args.get("a").asInt)
        val bad = JsonObject().apply { addProperty("name", "x"); addProperty("args_json", "not json") }
        assertEquals(0, ToolRouter.unwrap(ToolCall("3", "use_tool", bad)).args.size())
        // Other calls untouched.
        val plain = ToolCall("4", "open_app", JsonObject())
        assertTrue(ToolRouter.unwrap(plain) === plain)
    }

    @Test fun gistIsShort() {
        assertEquals("One.", ToolRouter.gist("One. Two three."))
        assertTrue(ToolRouter.gist("x".repeat(300)).length <= 110)
    }

    @Test fun everyPhoneLaneToolIsCore() {
        // Phone tools must never hide behind use_tool, or two of them could drive the screen at once.
        listOf("open_app", "whatsapp_message", "call_contact", "send_sms", "tap_element", "type_text", "read_screen", "press_send")
            .forEach { assertTrue(it, it in ToolRouter.CORE) }
    }
}
