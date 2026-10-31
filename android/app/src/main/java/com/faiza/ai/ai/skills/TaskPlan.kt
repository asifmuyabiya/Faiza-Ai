package com.faiza.ai.ai.skills

import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.google.gson.Gson

/** One task in a multi-task plan. */
data class PlanStep(val text: String, val state: String = "todo", val note: String = "")

/** A list of tasks the user gave in one go, confirmed once, then done one after another. */
data class Plan(val steps: List<PlanStep>, val confirmed: Boolean = false, val createdAt: Long = System.currentTimeMillis()) {

    val current: Int get() = steps.indexOfFirst { it.state == "todo" }
    val finished: Boolean get() = current < 0
    val running: Boolean get() = confirmed && !finished

    fun complete(note: String, ok: Boolean): Plan {
        val i = current
        if (i < 0) return this
        return copy(steps = steps.mapIndexed { k, s -> if (k == i) s.copy(state = if (ok) "done" else "failed", note = note) else s })
    }

    /** "1. … 2. …" */
    fun numbered(): String = steps.mapIndexed { i, s -> "${i + 1}. ${s.text}" }.joinToString("\n")

    fun summary(): String = steps.mapIndexed { i, s ->
        val mark = when (s.state) { "done" -> "✓"; "failed" -> "✗"; else -> "…" }
        "${i + 1}. $mark ${s.text}" + if (s.note.isNotBlank()) " — ${s.note}" else ""
    }.joinToString("\n")

    /** What the model is told to do now. */
    fun nextInstruction(): String {
        val i = current
        if (i < 0) {
            val failed = steps.count { it.state == "failed" }
            return "ALL ${steps.size} TASKS FINISHED" + (if (failed > 0) " ($failed could not be done)" else "") + ":\n" + summary() +
                    "\nNow tell the user in one or two short sentences that everything is done (mention anything that " +
                    "failed and why), and ask them to check."
        }
        val left = steps.drop(i + 1).mapIndexed { k, s -> "${i + 2 + k}. ${s.text}" }
        return "STEP ${i + 1} OF ${steps.size}: ${steps[i].text}\n" +
                "Say in ONE short sentence what you are doing now (e.g. \"এখন আম্মুর চ্যাটে যাচ্ছি…\"), do it with your tools, " +
                "check it worked, say what you did, then call step_done. Do not ask for confirmation — the user already " +
                "confirmed the whole plan." +
                (if (left.isNotEmpty()) "\nStill to do after this:\n" + left.joinToString("\n") else "\nThis is the last task.")
    }

    companion object {
        /** Steps from "a | b | c" or one per line; numbering ("1." "২)") removed. */
        fun parse(text: String): List<String> =
            text.split('|', '\n')
                .map { it.trim().replace(Regex("^[\\d০-৯]+[.)]\\s*"), "").trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_STEPS)

        const val MAX_STEPS = 40
    }
}

/**
 * Multi-task (owner: "একসাথে অনেক কাজ দিলে একবার কনফার্ম করে সব করবে, বলতে বলতে"). The plan lives on the phone, so
 * a long job doesn't get lost if the AI forgets its place or the conversation reconnects.
 */
object TaskPlan {

    private val gson = Gson()
    private val prefs get() = FaizaApp.instance.prefs

    fun get(): Plan? = runCatching { gson.fromJson(prefs.taskPlanJson ?: return null, Plan::class.java) }.getOrNull()
        ?.takeIf { it.steps.isNotEmpty() }

    private fun put(p: Plan?) {
        prefs.taskPlanJson = p?.let { gson.toJson(it) }
    }

    /** A confirmed plan is underway: messages / deletes inside it count as confirmed. */
    val running: Boolean get() = runCatching { get()?.running == true }.getOrDefault(false)

    /** Said again when a conversation (re)starts in the middle of a plan. */
    fun resumeNote(): String? {
        val p = get() ?: return null
        if (!p.running) return null
        return "[PLAN IN PROGRESS] You were in the middle of the user's task plan. Continue it now without asking.\n" +
                p.summary() + "\n" + p.nextInstruction()
    }

    fun skills(): List<Skill> = listOf(
        Skill(
            "plan_tasks", "📋", "Many tasks at once", "\"WhatsApp-এ ৪ জনকে মেসেজ, তারপর স্টোরি…\"",
            "Use FIRST whenever the user gives two or more tasks in one go (messages to several people, posting, " +
                    "deleting, opening apps…). Write every task as its own precise step, in the user's order, with all " +
                    "details (who, exact message text in the user's words, which app, which photos). Separate steps with " +
                    "\" | \". confirmed=false: you then read the plan back in one short breath and ask ONCE \"শুরু করব?\". " +
                    "confirmed=true only if the user already clearly said to just do it all.",
            listOf(
                ToolParam("steps", ParamType.STRING, "Tasks in order, separated by \" | \""),
                ToolParam("confirmed", ParamType.BOOLEAN, "true only if the user already told you to go ahead", required = false)
            )
        ) { _, args ->
            val steps = Plan.parse(args.require("steps"))
            if (steps.isEmpty()) return@Skill "ERROR: no steps"
            val plan = Plan(steps.map { PlanStep(it) }, confirmed = args.bool("confirmed"))
            put(plan)
            if (plan.confirmed) "PLAN STARTED (${steps.size} tasks).\n" + plan.nextInstruction()
            else "PLAN SAVED, NOT STARTED (${steps.size} tasks):\n${plan.numbered()}\n" +
                    "Read it to the user very briefly (one sentence, e.g. \"৪ জনকে মেসেজ, ফেসবুকে স্টোরি আর ৩টা ছবি ডিলিট — শুরু করব?\") " +
                    "and ask ONCE. When they agree, call start_plan. If they change something, call plan_tasks again."
        },
        Skill(
            "start_plan", "▶️", "Start the plan", "\"হ্যাঁ, শুরু করো\"",
            "The user agreed to the plan from plan_tasks: start it. You get the first step.",
            emptyList()
        ) { _, _ ->
            val p = get() ?: return@Skill "ERROR: no plan — call plan_tasks first"
            val started = p.copy(confirmed = true)
            put(started)
            "PLAN STARTED (${p.steps.size} tasks).\n" + started.nextInstruction()
        },
        Skill(
            "step_done", "✅", "Task done", "Moves to the next task",
            "Call after finishing (or giving up on) the current plan step. ok=false if it couldn't be done after two " +
                    "tries — then say why in note and carry on with the next step. Returns the next step.",
            listOf(
                ToolParam("note", ParamType.STRING, "What you did, very short (e.g. \"sent to Ammu\")"),
                ToolParam("ok", ParamType.BOOLEAN, "false if it could not be done", required = false)
            )
        ) { _, args ->
            val p = get() ?: return@Skill "ERROR: no plan running"
            if (!p.confirmed) return@Skill "ERROR: the plan wasn't started — ask the user, then call start_plan"
            // Helpers may still be sending / opening something for this step: wait for them first.
            TaskLanes.awaitAllIdle()
            val ok = args.get("ok")?.let { runCatching { it.asBoolean }.getOrNull() } ?: true
            val next = p.complete(args.str("note").orEmpty(), ok)
            put(if (next.finished) null else next)
            next.nextInstruction()
        },
        Skill(
            "cancel_plan", "⏹️", "Stop the plan", "\"থামো, বাকিগুলো করো না\"",
            "Stop the current plan (the user said stop / cancel / থামো). Tell them what was already done.",
            emptyList()
        ) { _, _ ->
            val p = get() ?: return@Skill "OK: no plan was running"
            put(null)
            "OK: plan stopped. Done so far:\n" + p.summary()
        }
    )
}
