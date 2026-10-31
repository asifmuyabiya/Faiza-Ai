package com.faiza.ai.ui.windows

import android.content.Context
import android.provider.Settings
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Calculator
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Keeps track of Faiza's floating mini apps: at most one of each kind. Main thread only. */
object WindowCenter {

    private val open = linkedMapOf<WindowKind, FloatWindow>()

    fun get(kind: WindowKind): FloatWindow? = open[kind]
    fun openKinds(): List<WindowKind> = open.keys.toList()

    /** Called by a window when it closes. */
    fun forget(kind: WindowKind) {
        open.remove(kind)
        if (kind == WindowKind.CAMERA) com.faiza.ai.service.FloatingOrbService.instance?.cameraWindowClosed()
    }

    /** Opens (or brings back) a window. Null when Faiza may not draw over other apps. */
    fun show(ctx: Context, kind: WindowKind): FloatWindow? {
        if (!Settings.canDrawOverlays(ctx)) return null
        open[kind]?.let { it.show(); return it }
        if (kind == WindowKind.CAMERA) com.faiza.ai.service.FloatingOrbService.instance?.cameraWindowOpening()
        val app = ctx.applicationContext
        val w = when (kind) {
            WindowKind.CAMERA -> CameraWindow(app)
            WindowKind.CALCULATOR -> CalculatorWindow(app)
            WindowKind.BROWSER -> BrowserWindow(app)
            WindowKind.NOTES -> NotesWindow(app)
            WindowKind.TIMER -> TimerWindow(app)
            WindowKind.TASKS -> TasksWindow(app)
            WindowKind.TRANSLATE -> TranslateWindow(app)
            WindowKind.CARD -> CardWindow(app)
            WindowKind.MUSIC -> MusicWindow(app)
        }
        open[kind] = w
        w.show()
        if (!w.shown) { open.remove(kind); return null }
        return w
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : FloatWindow> showAs(ctx: Context, kind: WindowKind): T? = show(ctx, kind) as? T

    fun closeAll() = open.values.toList().forEach { it.close() }

    // ---------------- Voice tools ----------------

    private const val NO_OVERLAY = "ERROR: Faiza isn't allowed to show over other apps. Ask the user to turn on " +
            "\"Display over other apps\" for Faiza in Settings."

    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    fun skills(): List<Skill> = listOf(
        Skill(
            "window", "🪟", "Mini windows", "\"ক্যালকুলেটর খোলো\" · \"নোট ছোট করে রাখো\" · \"সব বন্ধ করো\"",
            "Faiza's own floating mini apps over the screen, so the user never has to open a phone app: camera, " +
                    "calculator, research (browser), notes, timer, tasks (plan progress), translate, card (weather/news/info), " +
                    "music (play/pause/next). action=open|minimize|restore|close|close_all|list. Prefer these over opening " +
                    "the phone's own apps (Camera, Calculator, Chrome, Keep, Clock, Translate…).",
            listOf(
                ToolParam("action", ParamType.STRING, "open | minimize | restore | close | close_all | list"),
                ToolParam("window", ParamType.STRING, "camera | calculator | research | notes | timer | tasks | translate | card | music", required = false)
            )
        ) { ctx, args ->
            val action = args.require("action").lowercase()
            main {
                if (action == "list") return@main "OPEN: " + openKinds().joinToString { it.name.lowercase() }.ifEmpty { "none" }
                if (action == "close_all") { closeAll(); return@main "All windows closed." }
                val kind = WindowKind.from(args.str("window")) ?: return@main "ERROR: which window? (camera, calculator, research, notes, timer, tasks, translate, card, music)"
                when (action) {
                    "open", "show", "restore" -> if (show(ctx, kind) != null) "${kind.title} window is open." else NO_OVERLAY
                    "minimize", "hide" -> get(kind)?.let { it.minimize(); "${kind.title} minimised to a bubble." } ?: "${kind.title} isn't open."
                    "close" -> get(kind)?.let { it.close(); "${kind.title} closed." } ?: "${kind.title} isn't open."
                    else -> "ERROR: unknown action"
                }
            }
        },
        Skill(
            "window_camera", "📷", "Camera window", "\"আমাকে দেখো\" · \"পেছনের ক্যামেরা\"",
            "Opens the floating camera window and lets you SEE through it (the frame is sent to you). Use when the user " +
                    "says look at me / see this / what is this / how do I look. watch=true keeps sending a picture every " +
                    "couple of seconds until the user says stop (watch=false).",
            listOf(
                ToolParam("camera", ParamType.STRING, "front | back", required = false),
                ToolParam("watch", ParamType.BOOLEAN, "keep looking (true) or stop (false)", required = false)
            )
        ) { ctx, args ->
            val w = main { showAs<CameraWindow>(ctx, WindowKind.CAMERA) } ?: return@Skill NO_OVERLAY
            args.str("camera")?.let { c -> main { w.useFront(!c.startsWith("b", true)) } }
            delay(1500) // let the camera start
            val svc = com.faiza.ai.service.FloatingOrbService.instance ?: return@Skill "Camera window open."
            val watch = args.get("watch")?.let { args.bool("watch") }
            if (watch != null) svc.setCameraWatch(watch)
            if (main { svc.lookThroughCamera() }) "You are now seeing the camera picture — describe / answer from it."
            else "Camera window open, but the picture couldn't be sent to you (camera blocked or voice chat not on)."
        },
        Skill(
            "window_browser", "🌐", "Research window", "\"গুগলে খোঁজো…\" · \"এই পেজটা পড়ো\"",
            "Faiza's floating browser. action=search (Google the query) | open (a web address) | read (read the page " +
                    "that is showing: title, text and first links) | back. Use for research the user wants to SEE; the user " +
                    "can scroll it, minimise or close it.",
            listOf(
                ToolParam("action", ParamType.STRING, "search | open | read | back"),
                ToolParam("query", ParamType.STRING, "search words or web address", required = false)
            )
        ) { ctx, args ->
            val w = main { showAs<BrowserWindow>(ctx, WindowKind.BROWSER) } ?: return@Skill NO_OVERLAY
            when (args.require("action").lowercase()) {
                "search", "open" -> {
                    main { w.go(args.require("query")) }
                    delay(3500)
                    "Loaded. Page so far:\n" + withContext(Dispatchers.Main) { w.read() }.take(4000)
                }
                "read" -> withContext(Dispatchers.Main) { w.read() }
                else -> "ERROR: unknown action"
            }
        },
        Skill(
            "window_calculator", "🧮", "Calculator window", "\"হিসাবটা দেখাও\"",
            "Shows a sum and its exact answer in the floating calculator (so the user can see and keep calculating).",
            listOf(ToolParam("expression", ParamType.STRING, "e.g. 300+300+30+80"))
        ) { ctx, args ->
            val e = args.require("expression")
            val v = runCatching { Calculator.pretty(Calculator.eval(e)) }.getOrElse { return@Skill "ERROR: ${it.message}" }
            val w = main { showAs<CalculatorWindow>(ctx, WindowKind.CALCULATOR) } ?: return@Skill "RESULT: $v (window not allowed)"
            main { w.showSum(e, v) }
            "RESULT: ${Calculator.normalize(e)} = $v — shown in the calculator window."
        },
        Skill(
            "window_notes", "📝", "Notes window", "\"এটা নোট করো…\" · \"নোটগুলো পড়ো\"",
            "Quick notes in a floating window, saved on the phone. action=add (append a line) | read | clear | open.",
            listOf(
                ToolParam("action", ParamType.STRING, "add | read | clear | open"),
                ToolParam("text", ParamType.STRING, "the note (for add)", required = false)
            )
        ) { ctx, args ->
            when (args.require("action").lowercase()) {
                "add" -> { Notes.add(ctx, args.require("text")) }
                "clear" -> Notes.save(ctx, "")
                "read" -> return@Skill "NOTES:\n" + Notes.text(ctx).ifBlank { "(empty)" }
            }
            main { (showAs<NotesWindow>(ctx, WindowKind.NOTES))?.refresh() }
            "Done. Notes now:\n" + Notes.text(ctx).takeLast(800).ifBlank { "(empty)" }
        },
        Skill(
            "window_timer", "⏱️", "Timer window", "\"৫ মিনিটের টাইমার\" · \"স্টপওয়াচ চালাও\"",
            "Floating countdown / stopwatch the user can watch. action=countdown (seconds, optional label — you are " +
                    "told when it ends and must say it) | stopwatch | stop.",
            listOf(
                ToolParam("action", ParamType.STRING, "countdown | stopwatch | stop"),
                ToolParam("seconds", ParamType.INTEGER, "length for countdown", required = false),
                ToolParam("label", ParamType.STRING, "what it's for, e.g. \"ভাত নামাও\"", required = false)
            )
        ) { ctx, args ->
            val w = main { showAs<TimerWindow>(ctx, WindowKind.TIMER) } ?: return@Skill NO_OVERLAY
            main {
                when (args.require("action").lowercase()) {
                    "countdown" -> {
                        val s = args.int("seconds") ?: return@main "ERROR: how many seconds?"
                        w.countdown(s, args.str("label").orEmpty()); "Countdown of $s s started."
                    }
                    "stopwatch" -> { w.stopwatch(); "Stopwatch running." }
                    "stop" -> "Stopped at ${w.stop()}."
                    else -> "ERROR: unknown action"
                }
            }
        },
        Skill(
            "window_translate", "🌍", "Translate window", "\"এটা আরবিতে কী হবে?\"",
            "Floating translator. Give the text and target language code (bn, en, ar, hi, ur, …); the translation is " +
                    "shown in the window and returned to you to say.",
            listOf(
                ToolParam("text", ParamType.STRING, "what to translate"),
                ToolParam("to", ParamType.STRING, "target language code, e.g. en, bn, ar")
            )
        ) { ctx, args ->
            val text = args.require("text"); val to = args.require("to").lowercase().take(5)
            val out = withContext(Dispatchers.IO) { TranslateWindow.fetch(text, to) }
            main { showAs<TranslateWindow>(ctx, WindowKind.TRANSLATE)?.show(text, out ?: "…") }
            if (out != null) "TRANSLATION ($to): $out" else "Couldn't reach the translator — translate it yourself and say it."
        },
        Skill(
            "window_card", "🌤️", "Info card", "\"আবহাওয়াটা দেখাও\" · \"খবরগুলো দেখাও\"",
            "Shows a card on screen with a title and text you write — weather, headlines, prayer times, a list, a " +
                    "summary of research. Use after you got the information (e.g. from weather/news tools) when the user " +
                    "would like to SEE it, or it's long.",
            listOf(
                ToolParam("title", ParamType.STRING, "short heading"),
                ToolParam("text", ParamType.STRING, "the content; one item per line")
            )
        ) { ctx, args ->
            main { showAs<CardWindow>(ctx, WindowKind.CARD)?.fill(args.require("title"), args.require("text")) }
                ?.let { "Card shown." } ?: NO_OVERLAY
        },
        Skill(
            "window_music", "🎵", "Music control", "\"গান থামাও\" · \"পরের গান\"",
            "Controls whatever music/video app is playing and shows a small player window: play, pause, next, previous, " +
                    "louder, quieter.",
            listOf(ToolParam("action", ParamType.STRING, "play | pause | next | previous | louder | quieter"))
        ) { ctx, args ->
            main { showAs<MusicWindow>(ctx, WindowKind.MUSIC)?.control(args.require("action")) } ?: NO_OVERLAY
        }
    )
}
