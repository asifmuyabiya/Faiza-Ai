package com.faiza.ai.ai.macros

import android.content.Context
import android.os.SystemClock
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolCall
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.BuiltInSkills
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.SkillException
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.service.FaizaAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** App-wide macro state: saved macros, the voice recorder, and the hook that turns actions into steps. */
object MacroCenter {
    val recorder = MacroRecorder()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.faiza.ai.util.CrashReporter.guard)

    /** The element the last tap skill touched (set by the tap skills, read when recording). */
    @Volatile var lastTap: TapInfo? = null

    private val prefs get() = FaizaApp.instance.prefs

    fun all(): List<Macro> = MacroStore.load { prefs.macrosJson }

    fun saveAll(list: List<Macro>) = MacroStore.save({ prefs.macrosJson = it }, list)

    /** Saves [macro]; one with the same name is replaced. */
    fun upsert(macro: Macro) {
        val key = MacroStore.normalize(macro.name)
        saveAll(listOf(macro) + all().filter { MacroStore.normalize(it.name) != key && it.id != macro.id })
    }

    fun update(macro: Macro) = saveAll(all().map { if (it.id == macro.id) macro else it })

    fun delete(id: String) = saveAll(all().filter { it.id != id })

    /** MAYA ships trained tasks like "chatgpt se poochho" / "gemini image": add ours once. */
    fun seedBuiltIns() {
        val prefs = FaizaApp.instance.prefs
        if (prefs.isBuiltInMacrosSeeded) return
        fun ask(app: String, name: String, prompt: String, template: String?) = Macro(
            name = name, builtIn = true,
            steps = listOf(
                MacroStep("open_app", com.google.gson.JsonObject().apply { addProperty("app_name", app) }.toString(), "Open $app"),
                MacroStep("wait", "{\"seconds\":2}", "Wait 2 s"),
                MacroStep(
                    "type_text",
                    com.google.gson.JsonObject().apply {
                        addProperty("text", ""); addProperty("submit", false)
                        template?.let { addProperty("template", it) }
                    }.toString(),
                    "Type your request in $app", askEachTime = true, prompt = prompt
                ),
                MacroStep("tap", description = "Tap “Send”", target = "Send")
            )
        )
        val builtIns = listOf(
            ask("ChatGPT", "Ask ChatGPT", "What should I ask ChatGPT?", null),
            ask("Gemini", "Ask Gemini", "What should I ask Gemini?", null),
            ask("ChatGPT", "ChatGPT image", "What picture should ChatGPT make?", "Create an image: {input}"),
            ask("Gemini", "Gemini image", "What picture should Gemini make?", "Generate an image: {input}")
        )
        val existing = all()
        saveAll(existing + builtIns.filter { b -> existing.none { MacroStore.normalize(it.name) == MacroStore.normalize(b.name) } })
        prefs.isBuiltInMacrosSeeded = true
    }

    fun noteTap(e: FaizaAccessibilityService.ScreenElement, ctx: Context) {
        val m = ctx.resources.displayMetrics
        lastTap = TapInfo(
            label = e.label,
            editable = e.editable,
            xPct = (e.bounds.exactCenterX() / m.widthPixels).coerceIn(0f, 1f),
            yPct = (e.bounds.exactCenterY() / m.heightPixels).coerceIn(0f, 1f)
        )
    }

    /** Called after every skill. While recording, a successful action becomes a step; the AI is told its number. */
    fun afterTool(ctx: Context, call: ToolCall, result: String): String {
        if (!recorder.isRecording) return result
        val m = ctx.resources.displayMetrics
        val step = recorder.onToolResult(
            call.name, call.args, result, lastTap, m.widthPixels, m.heightPixels, foregroundAppName(ctx)
        )
        lastTap = null
        return if (step != null) "$result\nMACRO: saved as step $step." else result
    }

    private fun foregroundAppName(ctx: Context): String? {
        val pkg = FaizaAccessibilityService.instance?.foregroundPackage?.takeIf { it.isNotEmpty() } ?: return null
        val pm = ctx.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
}

/** Replays a macro step by step, waiting for each button to appear like a person would (video 33:47). */
object MacroRunner {
    private const val FIND_TIMEOUT_MS = 8_000L
    private const val FIELD_TIMEOUT_MS = 5_000L
    private const val BETWEEN_STEPS_MS = 600L
    private val SEND_LABELS = listOf("Send", "পাঠান", "भेजें")
    private val NEEDS_SCREEN = setOf("tap", "long_press", "type_text", "scroll", "phone_control")

    private val mutex = Mutex()

    @Volatile var runningName: String? = null
        private set

    /** (macro name, current step, total) while a macro runs — shown as a "3/7" badge on screen (MAYA). */
    private val _progress = kotlinx.coroutines.flow.MutableStateFlow<Triple<String, Int, Int>?>(null)
    val progress: kotlinx.coroutines.flow.StateFlow<Triple<String, Int, Int>?> get() = _progress

    suspend fun run(ctx: Context, macro: Macro, inputs: List<String>): String = withContext(Dispatchers.Main) {
        if (!mutex.tryLock()) return@withContext "ERROR: another macro (${runningName ?: "one"}) is still running."
        runningName = macro.name
        try {
            execute(ctx, macro, inputs)
        } finally {
            runningName = null
            _progress.value = null
            mutex.unlock()
        }
    }

    private suspend fun execute(ctx: Context, macro: Macro, inputs: List<String>): String {
        if (macro.steps.any { it.action in NEEDS_SCREEN } && FaizaAccessibilityService.instance == null) {
            return "ERROR: Accessibility is off, so I can't tap or type. Ask the user to turn it on in Faiza AI → Settings → Phone access."
        }
        var inputIndex = 0
        for ((i, step) in macro.steps.withIndex()) {
            _progress.value = Triple(macro.name, i + 1, macro.steps.size)
            val error = try {
                when (step.action) {
                    "tap", "long_press" -> tap(step)
                    "type_text" -> {
                        val raw = (if (step.askEachTime) inputs.getOrNull(inputIndex++) else null) ?: step.args().str("text").orEmpty()
                        // Built-in tasks wrap the user's words, e.g. "Create an image: {input}".
                        val template = step.args().str("template")
                        type(step, template?.replace("{input}", raw) ?: raw)
                    }
                    else -> runSkill(ctx, step)
                }
            } catch (e: SkillException) {
                e.message ?: "it didn't work"
            } catch (e: SecurityException) {
                "a permission is missing"
            }
            if (error != null) {
                val done = if (i == 0) "Nothing was done yet." else "Steps 1–$i were done."
                val rest = macro.steps.drop(i).mapIndexed { j, s -> "${i + j + 1}. ${s.description}" }.joinToString("; ")
                return "STOPPED at step ${i + 1} of ${macro.steps.size} (${step.description}): $error. $done " +
                        "Remaining: $rest. Tell the user briefly; you can finish the rest with read_screen and tap_element."
            }
            delay(BETWEEN_STEPS_MS)
        }
        MacroCenter.all().firstOrNull { it.id == macro.id }?.let {
            MacroCenter.update(it.copy(runCount = it.runCount + 1, lastRunAt = System.currentTimeMillis()))
        }
        com.faiza.ai.util.ActivityLog.add("macro", "Macro “${macro.name}” finished", "${macro.steps.size} steps")
        return "OK: macro “${macro.name}” finished (${macro.steps.size} steps)."
    }

    private fun accessibility() = FaizaAccessibilityService.instance
        ?: throw SkillException("Accessibility turned off")

    /** Labels to try: the full one, then shorter forms (chat rows include times/last messages that change). */
    private fun candidates(target: String): List<String> {
        val list = mutableListOf(target)
        target.substringBefore(" · ").trim().takeIf { it.isNotEmpty() && it != target }?.let { list += it }
        target.substringBefore(" (").trim().takeIf { it.isNotEmpty() && it !in list }?.let { list += it }
        return list
    }

    /** null = done, else what went wrong. */
    private suspend fun tap(step: MacroStep): String? {
        val a = accessibility()
        val long = step.action == "long_press"
        val target = step.target
        if (target.isNullOrBlank()) {
            val x = step.xPct ?: return "this step has nothing to tap"
            val y = step.yPct ?: return "this step has nothing to tap"
            delay(500)
            val m = a.resources.displayMetrics
            return if (a.tap(x * m.widthPixels, y * m.heightPixels)) { delay(900); null } else "the tap didn't work"
        }
        val deadline = SystemClock.elapsedRealtime() + FIND_TIMEOUT_MS
        while (true) {
            val e = candidates(target).firstNotNullOfOrNull { t ->
                a.findBest(t) { el -> !step.targetEditable || el.editable }
            }
            if (e != null) {
                val ok = if (long) a.longPressElement(e) else a.tapElement(e)
                delay(900)
                return if (ok) null else "the tap on “${e.label}” didn't work"
            }
            if (SystemClock.elapsedRealtime() > deadline) return "couldn't find “$target” on the screen"
            delay(500)
        }
    }

    private suspend fun type(step: MacroStep, text: String): String? {
        if (text.isEmpty()) return "there was nothing to type"
        val a = accessibility()
        val deadline = SystemClock.elapsedRealtime() + FIELD_TIMEOUT_MS
        while (a.snapshot().none { it.editable } && SystemClock.elapsedRealtime() < deadline) delay(400)
        val typed = if (a.typeLikePerson()) a.typeTextHuman(text) else a.typeText(text)
        if (!typed) return "there was no text box to type in"
        delay(300)
        val submit = step.args().get("submit")?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
        if (submit) {
            if (!a.pressEnter()) SEND_LABELS.any { a.clickByDescription(it) }
            delay(900)
        }
        return null
    }

    private suspend fun runSkill(ctx: Context, step: MacroStep): String? {
        val skill = BuiltInSkills.all.firstOrNull { it.id == step.action }
            ?: return "this kind of step (${step.action}) can't be replayed"
        val result = skill.run(ctx, step.args())
        return if (result.startsWith("OK")) null else result.removePrefix("ERROR:").trim().ifEmpty { "it didn't work" }
    }
}

/** The voice commands for macros (video 35:37–39:10). */
object MacroSkills {

    fun skills(): List<Skill> = listOf(
        Skill(
            "start_macro_recording", "⏺️", "Record a macro", "\"Record a macro named notes\"",
            "Start recording a macro (a saved series of phone actions the user can replay by name). After this, " +
                    "do each thing the user asks as usual; every successful action is saved as a step automatically.",
            listOf(ToolParam("name", ParamType.STRING, "Macro name, as the user said it"))
        ) { _, args ->
            val name = args.require("name")
            val r = MacroCenter.recorder
            if (r.isRecording) return@Skill "ERROR: already recording “${r.state.value?.name}”. Save or cancel it first."
            r.start(name)
            "OK: recording macro “$name”. Tell the user to give the steps one by one; say “remove last step” to undo " +
                    "and “save macro” when done."
        },
        Skill(
            "remove_last_macro_step", "↩️", "Remove last step", "\"Remove last step\"",
            "While recording a macro, delete the most recent step."
        ) { _, _ ->
            val r = MacroCenter.recorder
            if (!r.isRecording) return@Skill "ERROR: no macro is being recorded."
            val removed = r.removeLast() ?: return@Skill "There are no steps yet."
            "OK: removed “${removed.description}”. ${r.state.value?.steps?.size ?: 0} step(s) left."
        },
        Skill(
            "save_macro", "💾", "Save macro", "\"Save macro\"",
            "Finish recording and save the macro."
        ) { _, _ ->
            val r = MacroCenter.recorder
            if (!r.isRecording) return@Skill "ERROR: no macro is being recorded."
            val macro = r.finish() ?: return@Skill "NOT SAVED: the macro had no steps, so nothing was saved."
            MacroCenter.upsert(macro)
            "OK: saved macro “${macro.name}” with ${macro.steps.size} steps: ${MacroStore.summary(macro)}. " +
                    "The user can run it by saying “run ${macro.name}”."
        },
        Skill(
            "cancel_macro_recording", "✖️", "Cancel recording", "\"Cancel the macro\"",
            "Stop recording without saving."
        ) { _, _ ->
            val s = MacroCenter.recorder.cancel() ?: return@Skill "No macro was being recorded."
            "OK: stopped recording “${s.name}” without saving."
        },
        Skill(
            "run_macro", "⚡", "Run a macro", "\"Run notes\"",
            "Run a saved macro by name. If it replies NEEDS_INPUT, ask the user that question, then call again with inputs.",
            listOf(
                ToolParam("name", ParamType.STRING, "Macro name"),
                ToolParam(
                    "inputs", ParamType.STRING,
                    "What to type for the macro's typing steps; several answers in order, separated by \" | \"",
                    required = false
                )
            )
        ) { ctx, args ->
            val macros = MacroCenter.all()
            val name = args.require("name")
            val macro = MacroStore.find(macros, name)
                ?: return@Skill if (macros.isEmpty()) "ERROR: no macros are saved yet."
                else "ERROR: no macro called “$name”. Saved macros: ${macros.joinToString { it.name }}."
            val needed = macro.inputSteps
            val inputs = MacroRecorder.splitInputs(args.str("inputs"), needed.size)
            if (inputs.size < needed.size) {
                val questions = needed.drop(inputs.size).joinToString(" / ") { it.prompt ?: "What should I type?" }
                val how = if (needed.size > 1) "all answers in order, separated by \" | \"" else "their answer"
                return@Skill "NEEDS_INPUT: before running “${macro.name}”, ask the user: $questions Then call run_macro " +
                        "again with inputs = $how."
            }
            MacroRunner.run(ctx, macro, inputs)
        },
        Skill(
            "list_macros", "📋", "List macros", "\"What macros do I have?\"",
            "List the saved macros and their steps."
        ) { _, _ ->
            val macros = MacroCenter.all()
            if (macros.isEmpty()) "No macros saved yet."
            else macros.joinToString("\n") { "${it.name}: ${MacroStore.summary(it)}" }
        }
    )
}
