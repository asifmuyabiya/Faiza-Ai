package com.faiza.ai.ai.skills

import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.service.FaizaAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * "Scroll Facebook every 2 seconds until I say stop" (owner): keeps scrolling in the background while the
 * conversation goes on. Stops on "stop", when the user leaves that app, or after [MAX_MINUTES].
 */
object AutoScroll {

    const val MAX_MINUTES = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.faiza.ai.util.CrashReporter.guard)
    private var job: Job? = null
    @Volatile var running = false
        private set
    private var scrolls = 0

    fun start(direction: String, everySeconds: Int, minutes: Int): String {
        val a = FaizaAccessibilityService.instance ?: return "ERROR: Accessibility is off — Faiza can't scroll"
        stop()
        val app = a.foregroundPackage
        val every = everySeconds.coerceIn(1, 120) * 1000L
        val until = System.currentTimeMillis() + minutes.coerceIn(1, MAX_MINUTES) * 60_000L
        scrolls = 0
        running = true
        job = scope.launch {
            try {
                while (isActive && System.currentTimeMillis() < until) {
                    val svc = FaizaAccessibilityService.instance ?: break
                    // The user went to another app: they're doing something else now.
                    if (app.isNotEmpty() && svc.foregroundPackage.isNotEmpty() && svc.foregroundPackage != app) break
                    if (svc.scroll(direction)) scrolls++
                    delay(every)
                }
            } finally {
                running = false
            }
        }
        return "OK: auto-scrolling $direction every ${every / 1000} s (until the user says stop, leaves the app, " +
                "or ${minutes.coerceIn(1, MAX_MINUTES)} min). It runs in the background — tell the user, then keep talking normally. " +
                "When they say stop / থামো / বন্ধ করো, call stop_auto_scroll."
    }

    fun stop(): Boolean {
        val was = running
        job?.cancel()
        job = null
        running = false
        return was
    }

    fun skills(): List<Skill> = listOf(
        Skill(
            "auto_scroll", "🔁", "Keep scrolling", "\"২ সেকেন্ড পর পর স্ক্রল করো\"",
            "Keep scrolling the current app automatically every few seconds (Facebook, Reels, Shorts, TikTok, pages) " +
                    "until the user says stop. Use this — not scroll — whenever they ask for repeated / continuous scrolling.",
            listOf(
                ToolParam("direction", ParamType.STRING, "down = next post/video", enum = listOf("down", "up", "left", "right")),
                ToolParam("every_seconds", ParamType.INTEGER, "Seconds between scrolls (default 3)", required = false),
                ToolParam("minutes", ParamType.INTEGER, "Stop by itself after this many minutes (default and max 30)", required = false)
            ),
            needsAccessibility = true
        ) { _, args ->
            start(args.str("direction") ?: "down", args.int("every_seconds") ?: 3, args.int("minutes") ?: MAX_MINUTES)
        },
        Skill(
            "stop_auto_scroll", "⏹️", "Stop scrolling", "\"থামো\"",
            "Stop the automatic scrolling started by auto_scroll.",
            emptyList()
        ) { _, _ ->
            val n = scrolls
            if (stop()) "OK: stopped auto-scrolling after $n scrolls" else "OK: auto-scroll wasn't running"
        }
    )
}
