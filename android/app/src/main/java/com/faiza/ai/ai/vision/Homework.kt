package com.faiza.ai.ai.vision

import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.str
import com.faiza.ai.ui.windows.CardWindow
import com.faiza.ai.ui.windows.WindowCenter
import com.faiza.ai.ui.windows.WindowKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 📚 Photo of a maths problem / homework page → a patient step-by-step explanation in Bangla. */
object Homework {

    enum class Mode(val rule: String) {
        EXPLAIN("Solve it and EXPLAIN every step so the student understands how, then give the final answer."),
        CHECK("The picture shows the student's own work: check it line by line, say which steps are right, point out the exact mistake and how to fix it. Then give the correct answer."),
        HINT("Do NOT give the answer. Give 1–3 hints that lead the student to solve it themselves.");

        companion object { fun from(s: String?) = values().firstOrNull { it.name.equals(s, true) } ?: EXPLAIN }
    }

    fun system(mode: Mode, level: String?, language: String): String =
        "You are a kind, excellent teacher for a student in Bangladesh" + (level?.let { " ($it)" } ?: "") + ". " +
                "Look at the picture carefully. First write exactly what the question says (QUESTION:). Then " + mode.rule +
                " Use simple $language a child understands; keep the subject's own terms (e.g. \"লসাগু\", \"ভগ্নাংশ\", " +
                "\"equation\"). Show maths working line by line with numbers. Double-check calculations before answering. " +
                "For reading / language / science questions explain the meaning in simple words with one example. " +
                "Format:\nQUESTION: …\nSTEPS:\n1. …\n2. …\nANSWER: …\nSAY: <2 short sentences Faiza will say aloud>. " +
                "If the picture is blurry or cut off, say what is unclear and ask for a clearer photo."

    /** The part Faiza says; the full solution goes on the card. */
    fun spoken(reply: String): String =
        Regex("""(?s)SAY:\s*(.+)$""").find(reply)?.groupValues?.get(1)?.trim()?.take(400)
            ?: Regex("""(?m)^ANSWER:\s*(.+)$""").find(reply)?.groupValues?.get(1)?.trim()
            ?: reply.take(300)

    fun card(reply: String): String = reply.replace(Regex("""(?s)\n?SAY:.*$"""), "").trim()

    fun skill() = Skill(
        "homework", "📚", "Homework helper", "\"এই অঙ্কটা বুঝিয়ে দাও\" · \"আমার উত্তর ঠিক আছে কিনা দেখো\"",
        "Takes a photo of a homework problem (maths, science, English, Bangla…) and explains it step by step, shown on " +
                "a card on screen. source = camera (default: back camera, the user holds the page) | screen (a problem " +
                "on the screen) | gallery (newest photo). mode = explain | check (check the student's own answer) | hint. " +
                "level = class (e.g. \"class 6\"). Then say the SAY part and tell them the full steps are on the card.",
        listOf(
            ToolParam("source", ParamType.STRING, "camera | screen | gallery", required = false),
            ToolParam("mode", ParamType.STRING, "explain | check | hint", required = false),
            ToolParam("level", ParamType.STRING, "class / level, e.g. class 6", required = false),
            ToolParam("question", ParamType.STRING, "which problem, if the page has many (e.g. \"number 3\")", required = false)
        )
    ) { ctx, args ->
        val shot = PhotoAsk.get(ctx, args.str("source"))
        if (shot is PhotoAsk.Shot.Fail) return@Skill "ERROR: ${shot.why}"
        val bangla = com.faiza.ai.ai.AppLanguage.from(com.faiza.ai.FaizaApp.instance.prefs.language).base == com.faiza.ai.ai.PhraseBase.BENGALI
        val mode = Mode.from(args.str("mode"))
        val reply = PhotoAsk.ask(
            system(mode, args.str("level"), if (bangla) "Bangla" else "English"),
            args.str("question")?.let { "Help with: $it" } ?: "Help with the problem in this picture.",
            (shot as PhotoAsk.Shot.Ok).jpegB64
        )
        if (reply.startsWith("ERROR")) return@Skill reply
        withContext(Dispatchers.Main) {
            (WindowCenter.show(ctx, WindowKind.CARD) as? CardWindow)?.fill("📚 সমাধান", card(reply))
        }
        "SOLVED (full steps are on the card on screen). Say this to the user: ${spoken(reply)}"
    }
}
