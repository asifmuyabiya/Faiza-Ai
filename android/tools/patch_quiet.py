import os, sys, subprocess
TOOLS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS)
os.chdir(r"C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main")
J = "java/com/faiza/ai/"


def patch(p, pairs):
    s = open(p, encoding="utf-8").read()
    for a, b in pairs:
        assert s.count(a) == 1, p + " :: " + a[:80]
        s = s.replace(a, b)
    open(p, "w", encoding="utf-8").write(s)


# ---------- Prefs ----------
patch(J + "util/PrefsManager.kt", [
 ("""    /** Small extra boost for her voice on the loudspeaker. */""", """    // Quiet mode: see util/QuietMode.
    var isQuietMode: Boolean
        get() = prefs.getBoolean(KEY_QUIET_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_MODE, value).apply()

    /** When manual quiet mode ends by itself (0 = only when switched off). */
    var quietUntil: Long
        get() = prefs.getLong("quiet_until", 0L)
        set(value) = prefs.edit().putLong("quiet_until", value).apply()

    var isQuietFollowPhone: Boolean
        get() = prefs.getBoolean("quiet_follow_phone", true)
        set(value) = prefs.edit().putBoolean("quiet_follow_phone", value).apply()

    var isQuietHeadphonesOk: Boolean
        get() = prefs.getBoolean("quiet_headphones", true)
        set(value) = prefs.edit().putBoolean("quiet_headphones", value).apply()

    /** Small extra boost for her voice on the loudspeaker. */"""),
 ("""        const val KEY_ROUTINES = "routines\"""", """        const val KEY_ROUTINES = "routines"
        const val KEY_QUIET_MODE = "quiet_mode\""""),
])

# ---------- Service ----------
patch(J + "service/FloatingOrbService.kt", [
 # Startup: in quiet mode she doesn't greet or listen (unless the user just asked something).
 ("""        val typed = pendingTypedText.also { pendingTypedText = null }
        val assist = pendingAssist.also { pendingAssist = false }
        if (prefs.canUseLive()) {""", """        val typed = pendingTypedText.also { pendingTypedText = null }
        val assist = pendingAssist.also { pendingAssist = false }
        if (QuietMode.manualActive() && typed == null && !assist) {
            updateOrbState("idle") // Quiet mode: no greeting, no listening until she's tapped.
        } else if (prefs.canUseLive()) {"""),
 ("""        if (micPaused || live != null || isProcessing || isActiveListening || !prefs.isWakeWordEnabled) return""",
  """        if (micPaused || live != null || isProcessing || isActiveListening || !prefs.isWakeWordEnabled) return
        if (QuietMode.manualActive()) return // quiet mode: no always-on listening"""),
 ("""    private fun announce(text: String) {
        val session = live""", """    private fun announce(text: String) {
        // Quiet mode / phone on silent: never speak up by herself (the notification is still there).
        if (!QuietMode.canSpeakUnprompted(this)) return
        val session = live"""),
 ("""                if (prefs.isProactiveChat && live?.isSpeaking != true && proactiveNudges < PROACTIVE_MAX_NUDGES &&""",
  """                if (prefs.isProactiveChat && live?.isSpeaking != true && proactiveNudges < PROACTIVE_MAX_NUDGES &&
                    QuietMode.canSpeakUnprompted(this@FloatingOrbService) &&"""),
 ("""    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundHasMic) runCatching { startAsForeground() }""", """    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundHasMic) runCatching { startAsForeground() }
        if (intent?.action == ACTION_TOGGLE_QUIET) QuietMode.set(!QuietMode.manualActive())"""),
 ("""            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice" -> if (live != null) restartLive()""",
  """            PrefsManager.KEY_SCREEN_RECORDING, "louder_voice" -> if (live != null) restartLive()
            PrefsManager.KEY_QUIET_MODE -> applyQuietMode()"""),
 ("""    /** Assistant button (power / home long-press, headset): start listening now. */""",
  """    /** Quiet mode switched: go silent (after finishing the current sentence) or come back online. */
    private fun applyQuietMode() {
        if (QuietMode.manualActive()) {
            if (live != null) sleepLive() else {
                wakeEngine?.stop()
                wakeRestartJob?.cancel()
                speechRecognizer?.cancel()
                isActiveListening = false
                updateOrbState("idle")
            }
        } else if (live == null && !isActiveListening && !isProcessing) {
            resumeIdle()
        }
        refreshNotification()
    }

    private fun refreshNotification() {
        val text = if (QuietMode.manualActive()) "Quiet mode — she won't speak or listen on her own"
        else "${prefs.assistantName(persona)} is ready"
        runCatching {
            getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(text))
        }
    }

    /** Assistant button (power / home long-press, headset): start listening now. */"""),
 ("""        return NotificationCompat.Builder(this, FaizaApp.CHANNEL_FOREGROUND)
            .setContentTitle("Faiza AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()""", """        // One tap from the notification shade: go quiet / come back (no need to open the app).
        val quiet = QuietMode.manualActive()
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingOrbService::class.java).setAction(ACTION_TOGGLE_QUIET),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FaizaApp.CHANNEL_FOREGROUND)
            .setContentTitle("Faiza AI")
            .setContentText(if (quiet) "Quiet mode — she won't speak or listen on her own" else text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(0, if (quiet) "Wake up" else "Go quiet", toggle)
            .setOngoing(true)
            .build()"""),
 ("""        /** The assistant button started the service: listen right away (video 7:56). */""",
  """        const val ACTION_TOGGLE_QUIET = "com.faiza.ai.TOGGLE_QUIET"

        /** The assistant button started the service: listen right away (video 7:56). */"""),
 ("""import com.faiza.ai.util.PrefsManager""", """import com.faiza.ai.util.PrefsManager
import com.faiza.ai.util.QuietMode"""),
])

# ---------- Reminders & watches respect quiet mode ----------
patch(J + "ai/routines/RoutineCenter.kt", [
 ("""        notify(ctx, t.label, t.instruction, id.hashCode())
        com.faiza.ai.util.ActivityLog.add("reminder", t.label, t.instruction)
        ask(ctx,""", """        notify(ctx, t.label, t.instruction, id.hashCode())
        com.faiza.ai.util.ActivityLog.add("reminder", t.label, t.instruction)
        if (!com.faiza.ai.util.QuietMode.canSpeakUnprompted(ctx)) {
            // Quiet: the notification is the reminder; any real job runs silently in the background.
            runSilently(ctx, t.instruction)
            return
        }
        ask(ctx,"""),
 ("""                WatchAction.TELL -> {
                    com.faiza.ai.util.ActivityLog.add("watch", "$sender on $app", text.take(120))
                    ask(ctx,""", """                WatchAction.TELL -> {
                    com.faiza.ai.util.ActivityLog.add("watch", "$sender on $app", text.take(120))
                    if (!com.faiza.ai.util.QuietMode.canSpeakUnprompted(ctx)) {
                        notify(ctx, w.label, "$sender: ${text.take(120)}", key.hashCode())
                        true
                    } else ask(ctx,"""),
 ("""                WatchAction.TASK -> {
                    ask(ctx,""", """                WatchAction.TASK -> {
                    if (!com.faiza.ai.util.QuietMode.canSpeakUnprompted(ctx)) {
                        runSilently(ctx, "The user asked you to: ${w.instruction} (a message on $app from ${quoted(sender)} triggered it).")
                    } else ask(ctx,"""),
 ("""    /** Hands an instruction to Faiza: running → now; not running → start her with it. */""",
  """    /** Quiet mode: do the job with tools but without speaking; the result goes to the 🔔 inbox. */
    private fun runSilently(ctx: Context, instruction: String) {
        val provider = com.faiza.ai.ai.provider.AIProviders.fromPrefs(prefs) ?: return
        scope.launch {
            val answer = runCatching {
                com.faiza.ai.ai.FaizaAgent(ctx.applicationContext, provider).handle(
                    "[QUIET MODE — the user can't listen now, nothing will be spoken] $instruction " +
                            "Do it with your tools only if it's a real job; then reply in one short sentence for their inbox."
                )
            }.getOrNull() ?: return@launch
            com.faiza.ai.util.ActivityLog.add("info", "Done quietly", answer)
        }
    }

    /** Hands an instruction to Faiza: running → now; not running → start her with it. */"""),
])

# ---------- Voice command ----------
patch(J + "ai/skills/ExtraSkills.kt", [
 ("""        Skill(
            "set_mood",""", """        Skill(
            "quiet_mode", "🌙", "Quiet mode", "\\"I'm going out, stay quiet\\"",
            "Quiet mode on: you stop speaking on your own (no announcements, reminders out loud or proactive chat) and " +
                    "stop always-on listening, e.g. when the user goes out or is in a meeting. Off: everything back on.",
            listOf(
                ToolParam("on", ParamType.BOOLEAN, "true = go quiet, false = everything back on"),
                ToolParam("minutes", ParamType.INTEGER, "Only for this many minutes (empty = until switched off)", required = false)
            )
        ) { _, args ->
            val on = args.bool("on")
            val minutes = args.int("minutes")?.takeIf { it in 1..(24 * 60) }
            com.faiza.ai.util.QuietMode.set(on, minutes)
            if (on) {
                val until = minutes?.let { " for $it minutes" } ?: ""
                "OK: quiet mode is on$until. Say only one very short goodbye (e.g. \\"Okay, I'll stay quiet — tap me when " +
                        "you need me.\\") and nothing more; your microphone switches off right after."
            } else "OK: everything is back on — announcements, reminders and listening. Say so in one short sentence."
        },

        Skill(
            "set_mood","""),
])
patch(J + "ai/FaizaAgent.kt", [
 ("""            - MOOD: when the user's mood is clear""", """            - QUIET: "I'm going out / in a meeting / stay quiet / চুপ থাকো / বাইরে যাচ্ছি" → quiet_mode on (with minutes if
              they say how long). "Turn everything on / I'm back / সব চালু করো" → quiet_mode off.
            - MOOD: when the user's mood is clear"""),
])

# ---------- Settings (Announcements card) ----------
import settings_rows as sr
sr.apply("rowCalls", [
    sr.row("quietMode", "ic_moon", "slate", "Quiet mode", "Won't speak or listen on her own", "switch"),
    sr.row("quietFollowPhone", "ic_bell", "slate", "Quiet when phone is silent", "Follows silent, vibrate and DND", "switch"),
    sr.row("quietHeadphones", "ic_campaign", "green", "Speak in earphones anyway", "Private, so it's fine in public", "switch"),
])
patch(J + "ui/SettingsActivity.kt", [
 ("""        findViewById<View>(R.id.rowCalls).setOnClickListener { swCalls.toggle() }""",
  """        findViewById<View>(R.id.rowCalls).setOnClickListener { swCalls.toggle() }

        fun bindSwitch(rowId: Int, swId: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
            val sw = findViewById<SwitchMaterial>(swId)
            sw.isChecked = get()
            sw.setOnCheckedChangeListener { _, on -> set(on) }
            findViewById<View>(rowId).setOnClickListener { sw.toggle() }
        }
        bindSwitch(R.id.rowQuietMode, R.id.swQuietMode, { com.faiza.ai.util.QuietMode.manualActive() }) {
            com.faiza.ai.util.QuietMode.set(it)
        }
        bindSwitch(R.id.rowQuietFollowPhone, R.id.swQuietFollowPhone, { prefs.isQuietFollowPhone }) { prefs.isQuietFollowPhone = it }
        bindSwitch(R.id.rowQuietHeadphones, R.id.swQuietHeadphones, { prefs.isQuietHeadphonesOk }) { prefs.isQuietHeadphonesOk = it }"""),
])

# ---------- Home: 🌙 button in the top bar ----------
g = os.path.join(TOOLS, "gen_dashboard.py")
s = open(g, encoding="utf-8").read()
a = '''                <FrameLayout
                    android:layout_width="44dp"
                    android:layout_height="44dp">

                    <ImageButton
                        android:id="@+id/btnBell"'''
b = '''                <ImageButton
                    android:id="@+id/btnQuiet"
                    android:layout_width="44dp"
                    android:layout_height="44dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="Quiet mode"
                    android:padding="11dp"
                    android:scaleType="fitCenter"
                    android:src="@drawable/ic_moon"
                    app:tint="@color/text_primary" />

                <FrameLayout
                    android:layout_width="44dp"
                    android:layout_height="44dp">

                    <ImageButton
                        android:id="@+id/btnBell"'''
assert s.count(a) == 1
open(g, "w", encoding="utf-8").write(s.replace(a, b))
subprocess.check_call([sys.executable, g])

patch(J + "ui/DashboardActivity.kt", [
 ("""        binding.btnBell.setOnClickListener { startActivity(Intent(this, ActivityLogActivity::class.java)) }""",
  """        binding.btnBell.setOnClickListener { startActivity(Intent(this, ActivityLogActivity::class.java)) }
        binding.btnQuiet.setOnClickListener {
            val nowQuiet = !com.faiza.ai.util.QuietMode.manualActive()
            com.faiza.ai.util.QuietMode.set(nowQuiet)
            Toast.makeText(
                this,
                if (nowQuiet) "Quiet mode on — she won't speak or listen on her own" else "Everything is back on",
                Toast.LENGTH_SHORT
            ).show()
            updateStatus()
        }"""),
 ("""        // MAYA: "Maya is asleep — say 'Hey Maya'".
        if (isAiRunning) {""", """        // Quiet mode shows first: it's what matters when you're out.
        val quiet = com.faiza.ai.util.QuietMode.manualActive()
        binding.btnQuiet.imageTintList = getColorStateList(if (quiet) R.color.warning else R.color.text_primary)
        if (quiet) {
            binding.ivLiveStatus.setImageResource(R.drawable.ic_moon)
            binding.ivLiveStatus.imageTintList = getColorStateList(R.color.warning)
            binding.tvLiveStatus.text = com.faiza.ai.util.QuietMode.statusText()
            return
        }
        // MAYA: "Maya is asleep — say 'Hey Maya'".
        if (isAiRunning) {"""),
])
print("quiet mode wired")
