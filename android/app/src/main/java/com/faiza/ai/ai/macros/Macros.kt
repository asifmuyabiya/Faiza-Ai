package com.faiza.ai.ai.macros

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One recorded action. Taps remember the button's NAME (not its position), so a macro keeps
 * working when the app's layout shifts a little.
 */
data class MacroStep(
    /** The skill to run: open_app, tap, long_press, type_text, scroll, phone_control, wait, … */
    val action: String,
    /** The skill's arguments as JSON. */
    val argsJson: String = "{}",
    /** Shown to the user and read back by Faiza, e.g. "Tap “Send”". */
    val description: String,
    /** Taps: the label of the element to look for. */
    val target: String? = null,
    /** Taps: the element was a text box. */
    val targetEditable: Boolean = false,
    /** Taps without a label: position as a fraction of the screen (0..1). */
    val xPct: Float? = null,
    val yPct: Float? = null,
    /** type_text: ask the user what to type every time the macro runs (video 38:40). */
    val askEachTime: Boolean = false,
    /** The question Faiza asks for [askEachTime] steps. */
    val prompt: String? = null
) {
    fun args(): JsonObject = runCatching { JsonParser.parseString(argsJson).asJsonObject }.getOrNull() ?: JsonObject()
}

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val runCount: Int = 0,
    val lastRunAt: Long = 0L,
    /** Comes with the app (MAYA's built-in trained tasks). */
    val builtIn: Boolean = false
) {
    /** Steps that need something from the user each run, in order. */
    val inputSteps: List<MacroStep> get() = steps.filter { it.askEachTime }
}

/** Saved macros, as JSON in the app's settings (included in backups). */
object MacroStore {
    private val gson = Gson()
    const val MAX_MACROS = 50
    const val MAX_STEPS = 40

    /** [get]/[put] are the raw JSON storage (prefs in the app, a variable in tests). */
    fun load(get: () -> String?): List<Macro> {
        val json = get() ?: return emptyList()
        val list = runCatching { gson.fromJson<List<Macro>>(json, object : TypeToken<List<Macro>>() {}.type) }
            .getOrNull().orEmpty()
        return sanitize(list)
    }

    fun save(put: (String) -> Unit, macros: List<Macro>) = put(gson.toJson(macros.take(MAX_MACROS)))

    /** Gson ignores Kotlin null-safety: drop anything damaged. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun sanitize(list: List<Macro>?): List<Macro> = (list ?: emptyList())
        .filter { it != null && it.name != null && it.name.isNotBlank() && it.steps != null }
        .map { m ->
            m.copy(
                id = m.id ?: UUID.randomUUID().toString(),
                steps = m.steps.filter { it != null && it.action != null && it.description != null }
                    .map { it.copy(argsJson = it.argsJson ?: "{}") }
            )
        }
        .filter { it.steps.isNotEmpty() }

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        return buildString {
            for (ch in n) {
                val t = Character.getType(ch)
                if (ch.isLetterOrDigit() || t == Character.NON_SPACING_MARK.toInt() ||
                    t == Character.COMBINING_SPACING_MARK.toInt()
                ) append(ch) else if (ch.isWhitespace()) append(' ')
            }
        }.trim().replace(Regex(" +"), " ")
    }

    /** "macro", "ম্যাক্রো" etc. are filler when the user says "run the notes macro". */
    private val FILLER = setOf("macro", "macros", "the", "my", "ম্যাক্রো", "মেক্রো", "मैक्रो")

    private fun key(s: String) = normalize(s).split(' ').filter { it.isNotEmpty() && it !in FILLER }.joinToString(" ")

    /** Best macro for a spoken name, or null. Exact beats prefix beats word overlap. */
    fun find(macros: List<Macro>, spoken: String): Macro? {
        val q = key(spoken)
        if (q.isEmpty()) return null
        val qWords = q.split(' ').toSet()
        return macros.mapNotNull { m ->
            val k = key(m.name)
            if (k.isEmpty()) return@mapNotNull null
            val score = when {
                k == q -> 100
                k.replace(" ", "") == q.replace(" ", "") -> 95
                k.startsWith(q) || q.startsWith(k) -> 80
                k.contains(q) || q.contains(k) -> 70
                else -> {
                    val words = k.split(' ').toSet()
                    val common = words.intersect(qWords).size
                    if (common == 0) return@mapNotNull null
                    40 + 30 * common / maxOf(words.size, qWords.size)
                }
            }
            m to score
        }.maxByOrNull { it.second }?.takeIf { it.second >= 55 }?.first
    }

    fun summary(m: Macro): String =
        m.steps.mapIndexed { i, s -> "${i + 1}. ${s.description}" }.joinToString("; ")
}

/** What the tapped element looked like when a tap was recorded. */
data class TapInfo(
    val label: String,
    val editable: Boolean,
    val xPct: Float,
    val yPct: Float
)

/**
 * Voice recording (video 35:37): while it's on, every phone action that succeeds becomes a step.
 * Pure logic; the app feeds it the tool calls and their results.
 */
class MacroRecorder {

    data class State(val name: String, val steps: List<MacroStep>)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> get() = _state

    val isRecording: Boolean get() = _state.value != null

    fun start(name: String) {
        _state.value = State(name.trim(), emptyList())
    }

    fun cancel(): State? = _state.value.also { _state.value = null }

    fun removeLast(): MacroStep? {
        val s = _state.value ?: return null
        val last = s.steps.lastOrNull() ?: return null
        _state.value = s.copy(steps = s.steps.dropLast(1))
        return last
    }

    /** Ends recording and returns the finished macro (null if nothing was recorded). */
    fun finish(): Macro? {
        val s = _state.value ?: return null
        _state.value = null
        if (s.steps.isEmpty()) return null
        return Macro(name = s.name, steps = s.steps)
    }

    /**
     * A tool just ran while recording. Returns the new step number, or null if this action isn't
     * recordable (reading the screen, memory, messages that always need confirmation…) or failed.
     */
    fun onToolResult(
        tool: String, args: JsonObject, result: String,
        tapped: TapInfo?, screenW: Int, screenH: Int, appName: String?
    ): Int? {
        val s = _state.value ?: return null
        if (!result.startsWith("OK")) return null
        if (s.steps.size >= MacroStore.MAX_STEPS) return null
        val step = toStep(tool, args, result, tapped, screenW, screenH, appName) ?: return null
        val steps = s.steps + step
        _state.value = s.copy(steps = steps)
        return steps.size
    }

    companion object {
        /** Actions worth replaying. Messages/SOS are left out: they always need a fresh confirmation. */
        val RECORDABLE = setOf(
            "open_app", "tap_element", "tap_on", "tap_position", "long_press", "type_text", "scroll",
            "phone_control", "wait", "open_whatsapp_chat", "play_youtube", "web_search", "play_music",
            "navigate_to", "set_volume", "flashlight", "set_alarm", "set_timer", "call_contact"
        )

        private fun JsonObject.s(name: String): String? =
            get(name)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifEmpty { null }

        private fun JsonObject.i(name: String): Int? =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.toInt() }.getOrNull() }

        private fun JsonObject.b(name: String): Boolean =
            get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() } ?: false

        private fun quote(s: String) = "“${s.take(40)}”"

        fun toStep(
            tool: String, args: JsonObject, result: String,
            tapped: TapInfo?, screenW: Int, screenH: Int, appName: String?
        ): MacroStep? {
            if (tool !in RECORDABLE) return null
            return when (tool) {
                "open_app" -> {
                    val app = result.removePrefix("OK: opened ").trim().ifEmpty { args.s("app_name") ?: return null }
                    MacroStep("open_app", JsonObject().apply { addProperty("app_name", app) }.toString(), "Open $app")
                }
                "tap_element", "tap_on", "long_press" -> {
                    val action = if (tool == "long_press") "long_press" else "tap"
                    val verb = if (action == "long_press") "Press and hold" else "Tap"
                    val label = tapped?.label?.trim().orEmpty().ifEmpty { args.s("text").orEmpty() }
                    when {
                        label.isNotEmpty() -> MacroStep(
                            action, description = "$verb ${quote(label)}", target = label,
                            targetEditable = tapped?.editable ?: false, xPct = tapped?.xPct, yPct = tapped?.yPct
                        )
                        tapped != null -> MacroStep(
                            action, description = "$verb a spot on the screen", xPct = tapped.xPct, yPct = tapped.yPct
                        )
                        else -> null
                    }
                }
                "tap_position" -> {
                    val x = args.i("x") ?: return null
                    val y = args.i("y") ?: return null
                    if (screenW <= 0 || screenH <= 0) return null
                    MacroStep(
                        "tap", description = "Tap a spot on the screen",
                        xPct = (x.toFloat() / screenW).coerceIn(0f, 1f), yPct = (y.toFloat() / screenH).coerceIn(0f, 1f)
                    )
                }
                "type_text" -> {
                    val text = args.s("text") ?: return null
                    val submit = args.b("submit")
                    val where = appName?.let { " in $it" }.orEmpty()
                    MacroStep(
                        "type_text",
                        JsonObject().apply { addProperty("text", text); addProperty("submit", submit) }.toString(),
                        description = "Type your text$where" + if (submit) " and press Enter" else "",
                        askEachTime = true,
                        prompt = "What should I type$where?"
                    )
                }
                "scroll" -> {
                    val dir = args.s("direction") ?: return null
                    val times = (args.i("times") ?: 1).coerceIn(1, 10)
                    MacroStep("scroll", args.toString(), "Scroll $dir" + if (times > 1) " ×$times" else "")
                }
                "phone_control" -> {
                    val a = args.s("action") ?: return null
                    val label = when (a) {
                        "home" -> "Go to the home screen"
                        "back" -> "Go back"
                        "recents" -> "Open recent apps"
                        "notifications" -> "Open notifications"
                        "quick_settings" -> "Open quick settings"
                        "lock_screen" -> "Lock the screen"
                        else -> "Press $a"
                    }
                    MacroStep("phone_control", args.toString(), label)
                }
                "wait" -> MacroStep("wait", args.toString(), "Wait ${(args.i("seconds") ?: 2).coerceIn(1, 10)} s")
                "open_whatsapp_chat" -> MacroStep("open_whatsapp_chat", args.toString(),
                    "Open WhatsApp chat with ${args.s("contact") ?: "contact"}")
                "play_youtube" -> MacroStep("play_youtube", args.toString(), "Search YouTube for ${quote(args.s("query").orEmpty())}")
                "web_search" -> MacroStep("web_search", args.toString(), "Search Google for ${quote(args.s("query").orEmpty())}")
                "play_music" -> MacroStep("play_music", args.toString(),
                    args.s("query")?.let { "Play ${quote(it)}" } ?: "Play your favorite song")
                "navigate_to" -> MacroStep("navigate_to", args.toString(), "Navigate to ${args.s("destination").orEmpty()}")
                "set_volume" -> MacroStep("set_volume", args.toString(), "Set volume to ${args.i("percent") ?: 0}%")
                "flashlight" -> MacroStep("flashlight", args.toString(),
                    if (args.b("on")) "Turn the flashlight on" else "Turn the flashlight off")
                "set_alarm" -> MacroStep("set_alarm", args.toString(),
                    "Set an alarm for %02d:%02d".format(args.i("hour") ?: 0, args.i("minute") ?: 0))
                "set_timer" -> MacroStep("set_timer", args.toString(), "Start a ${args.i("seconds") ?: 0}-second timer")
                "call_contact" -> MacroStep("call_contact",
                    JsonObject().apply {
                        addProperty("contact", args.s("contact").orEmpty())
                        args.s("alternate_names")?.let { addProperty("alternate_names", it) }
                        args.i("sim")?.let { addProperty("sim", it) }
                    }.toString(),
                    "Call ${args.s("contact").orEmpty()}")
                else -> null
            }
        }

        /**
         * Splits what the user wants typed into one value per asking step.
         * Several values are separated by " | " (Faiza is told to do that).
         */
        fun splitInputs(raw: String?, needed: Int): List<String> {
            if (raw.isNullOrBlank() || needed <= 0) return emptyList()
            if (needed == 1) return listOf(raw.trim())
            return raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
