package com.faiza.ai.ai.skills

import com.faiza.ai.ai.provider.ToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper agents (owner: "Faiza keeps talking while her helpers do the work"). In a live conversation, slow actions
 * don't block her voice: the tool answers "STARTED" at once, she says what she's doing, and the helper reports back
 * with "[TASK DONE]".
 *
 * Two lanes, because a phone has ONE screen:
 *  - PHONE lane: anything that drives the screen (apps, WhatsApp, calls, camera…) — strictly one after another, in order.
 *  - WEB lane: internet work (search, news, weather, images…) — in parallel.
 * Tools that need to SEE the screen (read_screen, tap…) wait for the phone lane to finish first, so order never breaks.
 */
object TaskLanes {

    enum class Lane { PHONE, WEB }

    /** Slow actions that are self-contained: run by a helper while she talks. */
    private val PHONE_ASYNC = setOf(
        "open_app", "whatsapp_message", "call_contact", "send_sms", "play_music", "play_youtube", "navigate_to",
        "share_file", "run_macro", "phone_settings", "take_photo", "web_search", "set_alarm", "set_timer", "open_whatsapp_chat",
        "app_settings", "uninstall_app"
    )
    private val WEB_ASYNC = setOf(
        "search_web", "latest_news", "get_weather", "find_nearby", "travel_time", "read_webpage", "generate_image",
        "youtube_channel_stats", "youtube_video_details", "find_files", "organize_folder", "create_file",
        "storage_report", "find_duplicates", "bin_duplicates", "move_file", "rename_file", "bin_files", "restore_from_bin"
    )

    /** Tools that look at / touch the screen: they wait until the phone lane is free. */
    private val NEEDS_SCREEN = setOf(
        "read_screen", "look_at_screen", "tap_element", "tap_on", "tap_position", "long_press", "type_text", "scroll",
        "press_send", "phone_control", "wait", "auto_scroll", "step_done", "start_macro_recording", "save_macro"
    )

    fun laneOf(tool: String): Lane? = when (tool) {
        in PHONE_ASYNC -> Lane.PHONE
        in WEB_ASYNC -> Lane.WEB
        else -> null
    }

    fun needsScreen(tool: String) = tool in NEEDS_SCREEN

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + com.faiza.ai.util.CrashReporter.guard)
    private val phoneLock = Mutex()
    private val phonePending = AtomicInteger(0)
    private val webPending = AtomicInteger(0)

    /** Something is still running in the background. */
    val busy: Boolean get() = phonePending.get() > 0 || webPending.get() > 0
    val phoneBusy: Boolean get() = phonePending.get() > 0

    /** The last few results, so step_done / the plan can mention what actually happened. */
    private val recent = ArrayDeque<String>()
    fun recentResults(): List<String> = synchronized(recent) { recent.toList() }

    /**
     * Starts [call] on its lane and returns at once. [run] does the real work; [done] gets the result.
     * Returns the "STARTED" text for the model, with the queue position for the phone lane.
     */
    fun submit(call: ToolCall, run: suspend (ToolCall) -> String, done: (ToolCall, String) -> Unit): String {
        val lane = laneOf(call.name) ?: return "ERROR: not a background task"
        val position = if (lane == Lane.PHONE) phonePending.incrementAndGet() else webPending.incrementAndGet()
        scope.launch {
            val result = try {
                if (lane == Lane.PHONE) phoneLock.withLock { run(call) } else run(call)
            } catch (e: Exception) {
                "ERROR: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                if (lane == Lane.PHONE) phonePending.decrementAndGet() else webPending.decrementAndGet()
            }
            synchronized(recent) {
                recent.addLast("${call.name}: ${result.take(160)}")
                while (recent.size > 6) recent.removeFirst()
            }
            done(call, result)
        }
        val queue = if (lane == Lane.PHONE && position > 1) " (queued — ${position - 1} phone task(s) before it)" else ""
        return "STARTED in the background$queue: ${call.name}. Right now say ONE short natural sentence about what is " +
                "happening (e.g. \"হ্যাঁ বস, ক্যামেরা খুলছি…\" / \"আম্মুকে মেসেজটা লিখছি…\") and keep talking normally. " +
                "Do NOT say it's finished yet — the result arrives as a [TASK DONE] message."
    }

    /** Runs [block] holding the one screen (used by the task manager's screen agents). */
    suspend fun <T> withScreen(block: suspend () -> T): T {
        phonePending.incrementAndGet()
        try {
            return phoneLock.withLock { block() }
        } finally {
            phonePending.decrementAndGet()
        }
    }

    /** Waits (up to [maxMs]) until every phone-lane task has finished. */
    suspend fun awaitPhoneIdle(maxMs: Long = 90_000) {
        withTimeoutOrNull(maxMs) {
            while (phonePending.get() > 0) kotlinx.coroutines.delay(150)
        }
    }

    /** Waits until both lanes are idle (used before a plan step is marked done). */
    suspend fun awaitAllIdle(maxMs: Long = 120_000) {
        withTimeoutOrNull(maxMs) {
            while (busy) kotlinx.coroutines.delay(150)
        }
    }
}
