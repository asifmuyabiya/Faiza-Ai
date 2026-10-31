import sys
sys.path.insert(0, r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools')
from settings_rows import row, apply

root = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
base = root + r'\java\com\faiza\ai'
s = ''


def load(p):
    global s
    s = open(p, encoding='utf-8').read()


def save(p):
    open(p, 'w', encoding='utf-8').write(s)


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


# ---- Accessibility: automation flag + delete-guard hook ----
p = base + r'\service\FaizaAccessibilityService.kt'
load(p)
rep('''        var foregroundListener: ((String) -> Unit)? = null
''', '''        var foregroundListener: ((String) -> Unit)? = null

        /** Until this time (elapsedRealtime) Faiza herself is acting on the screen (a tool is running). */
        @Volatile var automationUntil = 0L
''')
rep('''                foregroundListener?.invoke(pkg)
            }
        }''', '''                foregroundListener?.invoke(pkg)
            }
            // A dialog just opened: is it "Delete…?" (Delete guard)? Give it a moment to fill in.
            if (pkg != packageName && pkg != "com.android.systemui") {
                deleteCheck?.let { handler.removeCallbacks(it) }
                deleteCheck = Runnable { runCatching { com.faiza.ai.ai.guardian.DeleteGuard.check(this) } }
                    .also { handler.postDelayed(it, 300) }
            }
        }''')
rep('''    override fun onInterrupt() {}''', '''    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var deleteCheck: Runnable? = null

    override fun onInterrupt() {}''')
save(p)

# ---- Agent: mark automation while a tool runs; register new skills; prompt rules ----
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''            val result = withTimeout(timeout) {
                withContext(Dispatchers.Main) { skill.run(context, call.args) }
            }''', '''            com.faiza.ai.service.FaizaAccessibilityService.automationUntil = android.os.SystemClock.elapsedRealtime() + timeout
            val result = try {
                withTimeout(timeout) {
                    withContext(Dispatchers.Main) { skill.run(context, call.args) }
                }
            } finally {
                com.faiza.ai.service.FaizaAccessibilityService.automationUntil = android.os.SystemClock.elapsedRealtime() + 2_000
            }''')
rep('''        skills += com.faiza.ai.ai.modes.ModeCenter.skills().filter { it.id !in disabled }''',
    '''        skills += com.faiza.ai.ai.modes.ModeCenter.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.skills.AutoScroll.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.routines.FaizaTimers.skills().filter { it.id !in disabled }''')
rep('''            - Messages (send_sms, whatsapp_message): read back the recipient and exact text, ask for confirmation, and only send with confirmed=true after the user says yes.
''', '''            {MESSAGE_RULE}
            - TALK WHILE YOU WORK: for any task with several steps, say ONE short sentence in the user's language
              BEFORE each tool call about what you're doing right now, and say what happened after — never go silent.
              WhatsApp message in a voice conversation = do it step by step: say "WhatsApp খুলছি, চাঁদের চ্যাটে যাচ্ছি…"
              → open_whatsapp_chat → "চ্যাটে ঢুকেছি, লিখছি: <message>…" → type_text → "সব ঠিক আছে, পাঠাচ্ছি…" →
              press_send → "পাঠিয়ে দিয়েছি ✅". If the user says stop/থামো at any point, stop at once.
            - Taps: a tap result "NOT CONFIRMED" means it did NOT work — never say you clicked. Read the screen
              and try another element (on websites tap the link/title text itself), or look_at_screen + tap_position.
            - "Keep scrolling / every N seconds / until I say stop" → auto_scroll (not scroll). "Stop" → stop_auto_scroll.
            - "Tell me / remind me / জানিও in N seconds/minutes" → remind_in (you will speak at that time).
              Time questions ("কয়টা বাজে", "how long has it been / is left") → time_now. Never guess times.
''')
rep('''    private fun systemPrompt(): String {''', '''    /** Ask before sending messages, or just narrate and send (owner's choice in Settings). */
    private fun messageRule(): String = if (prefs.isConfirmBeforeSend)
        "- Messages (send_sms, whatsapp_message): read back the recipient and exact text, ask for confirmation, and only send with confirmed=true after the user says yes."
    else
        "- Messages: when the user has clearly said WHO and WHAT, send it — do NOT ask \\"shall I send?\\" (they already told you). " +
                "Say the recipient and the message out loud as you go so they can stop you; use confirmed=true. Only ask " +
                "if the person or the text is unclear."

    private fun systemPrompt(): String {''')
save(p)
load(p)
if '{MESSAGE_RULE}' in s and '.replace("{MESSAGE_RULE}"' not in s:
    # find where {PROFILE} is replaced and add ours next to it
    i = s.find('.replace("{PROFILE}"')
    assert i > 0, 'PROFILE replace not found'
    s = s[:i] + '.replace("{MESSAGE_RULE}", messageRule())' + s[i:]
    save(p)

# ---- Skills: confirmation follows the setting; press_send ----
p = base + r'\ai\skills\BuiltInSkills.kt'
load(p)
rep('''        if (!confirmed) return NOT_CONFIRMED
        if (!hasPermission(ctx, Manifest.permission.SEND_SMS))''', '''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend) return NOT_CONFIRMED
        if (!hasPermission(ctx, Manifest.permission.SEND_SMS))''')
rep('''        if (!confirmed) return NOT_CONFIRMED
        val phoneMatch''', '''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend) return NOT_CONFIRMED
        val phoneMatch''')
rep('''        Skill(
            "whatsapp_message", "✉️", "WhatsApp messages",''', '''        Skill(
            "press_send", "📨", "Press Send", "Sends what's typed in the open chat",
            "Tap the Send button in the chat that is open now (WhatsApp, Messenger, SMS…), after type_text.",
            emptyList(),
            needsAccessibility = true
        ) { _, _ ->
            val a = accessibility()
            val before = a.screenSignature()
            val sent = SEND_LABELS.any { a.clickByDescription(it) } ||
                    (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
            if (!sent) return@Skill "ERROR: no Send button on screen. Call read_screen to find it."
            delay(900)
            if (a.screenSignature() != before) "OK: sent" else "NOT CONFIRMED: pressed Send but nothing changed — check with read_screen"
        },

        Skill(
            "whatsapp_message", "✉️", "WhatsApp messages",''')
save(p)

# ---- Prefs ----
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    // ====== Audio ======''', '''    /** Faiza's own "tell me in N seconds" timers. */
    var faizaTimersJson: String?
        get() = str("faiza_timers")
        set(value) = putStr("faiza_timers", value)

    /** Speak up when any app asks "Delete…?". */
    var isDeleteGuard: Boolean
        get() = prefs.getBoolean("delete_guard", true)
        set(value) = prefs.edit().putBoolean("delete_guard", value).apply()

    /** Ask "shall I send?" before messages (off: she narrates and sends what the user dictated). */
    var isConfirmBeforeSend: Boolean
        get() = prefs.getBoolean("confirm_before_send", false)
        set(value) = prefs.edit().putBoolean("confirm_before_send", value).apply()

    // ====== Audio ======''')
save(p)

# ---- Service: timers, warnings, orb tap stops auto-scroll ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */''', '''    /** A "tell me in N seconds" timer finished: say it (the notification is already shown). */
    fun deliverTimer(text: String) {
        if (QuietMode.manualActive() || OwnerGuard.needsCode()) return
        val session = live
        when {
            session != null -> session.sendText("[TIMER] The timer the user asked for is done. Tell them now, exactly: \\"$text\\"")
            prefs.canUseLive() && !micPaused -> startLive("[TIMER] The timer the user asked for is done. Say exactly: \\"$text\\"")
            else -> speak(text)
        }
    }

    /** Delete guard: say a warning right now. False = couldn't speak (quiet mode etc.), show a notification instead. */
    fun warnUser(text: String): Boolean {
        if (!QuietMode.canSpeakUnprompted(this) || OwnerGuard.needsCode()) return false
        val session = live
        if (session != null) session.interruptWith("[DELETE GUARD] Say exactly, urgently and caring: \\"$text\\"")
        else speak(text)
        return true
    }

    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */''')
rep('''    private fun onOrbTapped() {''', '''    private fun onOrbTapped() {
        // Tapping her stops anything running by itself.
        if (com.faiza.ai.ai.skills.AutoScroll.stop()) return''')
save(p)

# ---- Manifest: timer receiver ----
p = root + r'\AndroidManifest.xml'
load(p)
rep('''        <!-- Life modes:''', '''        <receiver
            android:name=".ai.routines.FaizaTimerReceiver"
            android:exported="false" />

        <!-- Life modes:''')
save(p)

# ---- Settings rows ----
apply("rowOwnerLock", [
    row("deleteGuard", "ic_delete", "amber", "Delete guard",
        "Warns you before you delete something in any app", "switch"),
])
apply("rowCalls", [
    row("confirmSend", "ic_send", "blue", "Ask before sending messages",
        "Off: she says who and what as she goes, then sends", "switch"),
])
p = base + r'\ui\SettingsActivity.kt'
load(p)
rep('''        findViewById<View>(R.id.rowOwnerLock).setOnClickListener { startActivity(Intent(this, OwnerLockActivity::class.java)) }''',
    '''        findViewById<View>(R.id.rowOwnerLock).setOnClickListener { startActivity(Intent(this, OwnerLockActivity::class.java)) }
        val swDelete = findViewById<SwitchMaterial>(R.id.swDeleteGuard)
        swDelete.isChecked = prefs.isDeleteGuard
        swDelete.setOnCheckedChangeListener { _, on -> prefs.isDeleteGuard = on }
        findViewById<View>(R.id.rowDeleteGuard).setOnClickListener { swDelete.toggle() }
        val swConfirm = findViewById<SwitchMaterial>(R.id.swConfirmSend)
        swConfirm.isChecked = prefs.isConfirmBeforeSend
        swConfirm.setOnCheckedChangeListener { _, on -> prefs.isConfirmBeforeSend = on; FloatingOrbService.instance?.onAudioSettingsChanged() }
        findViewById<View>(R.id.rowConfirmSend).setOnClickListener { swConfirm.toggle() }''')
save(p)
print("ok")
