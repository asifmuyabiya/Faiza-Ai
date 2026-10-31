package com.faiza.ai.ai.skills

import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolCall
import com.faiza.ai.ai.provider.ToolParam
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Faster answers (owner: "সাধারণ কথায় দেরি"): Faiza has 120+ tools, and sending every tool's full description with
 * every request makes each answer slower (and the model pick worse). The everyday tools still go in full; all the
 * others are listed in one short line each, reached through use_tool (and tool_help for their details).
 */
object ToolRouter {

    /** Always sent in full: phone control (order matters on one screen), memory, plans, quick everyday jobs. */
    val CORE = setOf(
        // phone / screen
        "open_app", "whatsapp_message", "open_whatsapp_chat", "call_contact", "send_sms", "play_music", "play_youtube",
        "navigate_to", "share_file", "run_macro", "phone_settings", "take_photo", "set_alarm", "set_timer",
        "read_screen", "look_at_screen", "tap_element", "tap_on", "tap_position", "long_press", "type_text", "scroll",
        "press_send", "phone_control", "wait", "auto_scroll",
        // web
        "web_search", "search_web", "latest_news", "get_weather",
        // memory & plans
        "remember", "recall", "forget", "plan_tasks", "start_plan", "step_done", "cancel_plan",
        // quick everyday
        "calculate", "incoming_call", "window", "gaming_mode", "life_mode", "khata", "prayer", "speaker_output"
    )

    const val USE = "use_tool"
    const val HELP = "tool_help"

    fun split(all: List<Skill>): Pair<List<Skill>, List<Skill>> = all.partition { it.id in CORE }

    /** First sentence of the tool's description, short. */
    fun gist(description: String, max: Int = 110): String {
        val first = description.split(Regex("(?<=[.!?])\\s")).firstOrNull()?.trim().orEmpty().ifEmpty { description.trim() }
        return if (first.length <= max) first else first.take(max - 1).substringBeforeLast(' ') + "…"
    }

    fun signature(s: Skill): String {
        val ps = s.spec.params.joinToString(", ") { if (it.required) it.name else it.name + "?" }
        return "${s.id}($ps)"
    }

    /** The extra-tools list that goes into the system prompt. */
    fun catalog(rest: List<Skill>): String = if (rest.isEmpty()) "" else buildString {
        appendLine("MORE TOOLS — call them with use_tool(name, args_json). If you're unsure of the arguments, call tool_help(name) first.")
        rest.sortedBy { it.id }.forEach { appendLine("- ${signature(it)} — ${gist(it.spec.description)}") }
    }.trimEnd()

    /** use_tool + tool_help, wrapping [rest]. */
    fun metaSkills(rest: List<Skill>): List<Skill> = listOf(
        Skill(
            USE, "🧰", "More tools", "",
            "Run one of the MORE TOOLS listed in your instructions. name = the tool name; args_json = its arguments as a " +
                    "JSON object, e.g. {\"action\":\"today\"}.",
            listOf(
                ToolParam("name", ParamType.STRING, "tool name from the MORE TOOLS list"),
                ToolParam("args_json", ParamType.STRING, "arguments as a JSON object", required = false)
            )
        ) { _, _ -> "ERROR: use_tool is unwrapped before running" },
        Skill(
            HELP, "❓", "Tool help", "",
            "Full description and arguments of one of the MORE TOOLS.",
            listOf(ToolParam("name", ParamType.STRING, "tool name"))
        ) { _, args ->
            val n = args.require("name")
            val s = rest.firstOrNull { it.id == n } ?: return@Skill "No tool called \"$n\"."
            buildString {
                appendLine("${s.id}: ${s.spec.description}")
                s.spec.params.forEach { p ->
                    appendLine("- ${p.name} (${p.type.name.lowercase()}${if (p.required) "" else ", optional"}): ${p.description}" +
                            (p.enum?.let { " one of $it" } ?: ""))
                }
                append("Call it with use_tool(name=\"${s.id}\", args_json={…}).")
            }
        }
    )

    /** use_tool(name, args_json) → the real call, so lanes, permissions and logging see the real tool. */
    fun unwrap(call: ToolCall): ToolCall {
        if (call.name != USE) return call
        val name = call.args.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val raw = call.args.get("args_json")
        val args: JsonObject = when {
            raw == null || raw.isJsonNull -> JsonObject()
            raw.isJsonObject -> raw.asJsonObject
            else -> runCatching { JsonParser.parseString(raw.asString).asJsonObject }.getOrDefault(JsonObject())
        }
        return ToolCall(call.id, name, args)
    }
}
