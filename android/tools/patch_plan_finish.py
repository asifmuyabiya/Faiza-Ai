base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'
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


# ---- Accessibility: what's typed in the message box right now ----
p = base + r'\service\FaizaAccessibilityService.kt'
load(p)
rep('''    private fun currentText(field: AccessibilityNodeInfo): String {''', '''    /** Text in the message / text box on screen now (hint ignored), or null when there is no box. */
    fun messageBoxText(): String? {
        val root = appRoot() ?: return null
        var found: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 40 || found != null) return
            if (n.isEditable && n.isVisibleToUser) { found = n; return }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        return found?.let { currentText(it) }
    }

    private fun currentText(field: AccessibilityNodeInfo): String {''')
save(p)

# ---- Send: press it, then check the box really emptied ----
p = base + r'\ai\skills\BuiltInSkills.kt'
load(p)
rep('''            val a = accessibility()
            val before = a.screenSignature()
            val sent = SEND_LABELS.any { a.clickByDescription(it) } ||
                    (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
            if (!sent) return@Skill "ERROR: no Send button on screen. Call read_screen to find it."
            delay(900)
            if (a.screenSignature() != before) "OK: sent" else "NOT CONFIRMED: pressed Send but nothing changed — check with read_screen"
        },''', '''            pressSendVerified(accessibility(), "the open chat")
        },''')
rep('''        val sent = SEND_LABELS.any { a.clickByDescription(it) } ||
                (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
        return if (sent) "OK: WhatsApp message sent to $chatName"
        else "OK: the message is typed in the chat with $chatName, but the Send button wasn't found. Use read_screen and tap Send."
    }''', '''        return pressSendVerified(a, chatName)
    }

    /**
     * Presses Send and checks it really went: the message box must be empty afterwards. Tries twice. Never reports
     * "sent" when the text is still sitting in the box.
     */
    private suspend fun pressSendVerified(a: FaizaAccessibilityService, chatName: String): String {
        repeat(2) { attempt ->
            val typed = a.messageBoxText()
            if (typed != null && typed.isBlank() && attempt == 0) {
                return "ERROR: the message box in the chat with $chatName is empty — type the message first (type_text)."
            }
            val pressed = SEND_LABELS.any { a.clickByDescription(it) } ||
                    (a.findBest("Send") { it.clickable && !it.editable }?.let { a.tapElement(it) } ?: false)
            if (!pressed) return "ERROR: no Send button found in the chat with $chatName. Call read_screen and tap Send."
            var waited = 0
            while (waited < 1_800) {
                delay(300)
                waited += 300
                val now = a.messageBoxText()
                if (now == null || now.isBlank()) return "OK: message sent to $chatName (the message box is empty now)"
            }
        }
        return "NOT SENT: pressed Send twice but the message is still in the box in the chat with $chatName. " +
                "Do NOT say it was sent. Call read_screen, find the Send button and tap it."
    }''')
save(p)

# ---- Prompt: one reliable tool per message ----
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''              Several WhatsApp messages: for each person open_whatsapp_chat → type_text → press_send (or whatsapp_message
              with confirmed=true).''', '''              Several WhatsApp messages: ONE whatsapp_message call per person with confirmed=true (it finds them, opens
              the chat, types, presses Send and checks it went) — say one short sentence before each.''')
rep('''              WhatsApp message in a voice conversation = do it step by step: say "WhatsApp খুলছি, চাঁদের চ্যাটে যাচ্ছি…"
              → open_whatsapp_chat → "চ্যাটে ঢুকেছি, লিখছি: <message>…" → type_text → "সব ঠিক আছে, পাঠাচ্ছি…" →
              press_send → "পাঠিয়ে দিয়েছি ✅". If the user says stop/থামো at any point, stop at once.''',
    '''              WhatsApp message: say "চাঁদকে লিখছি: <message>…", call whatsapp_message with confirmed=true, and only when it
              returns OK say "পাঠিয়ে দিয়েছি ✅". If it returns NOT SENT or ERROR, say so honestly and fix it. If the user
              says stop/থামো at any point, stop at once.''')
save(p)

# ---- Service: a confirmed plan never silently stalls ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''    private var liveTicker: Job? = null''', '''    private var liveTicker: Job? = null
    /** Keeps a confirmed task plan moving when the AI stops after a step. */
    private var planWatch: Job? = null''')
rep('''    private fun startLiveTicker() {
        liveTicker?.cancel()''', '''    /**
     * If a confirmed plan still has tasks but she has gone quiet (no tool, not speaking, the user silent for a few
     * seconds), hand her the next task again. At most 3 reminders per step, so it can't loop.
     */
    private fun startPlanWatch() {
        planWatch?.cancel()
        planWatch = scope.launch {
            var step = -1
            var nudges = 0
            while (true) {
                delay(2_000)
                val session = live ?: break
                val plan = runCatching { com.faiza.ai.ai.skills.TaskPlan.get() }.getOrNull()
                if (plan == null || !plan.running) { step = -1; nudges = 0; continue }
                if (plan.current != step) { step = plan.current; nudges = 0 }
                val quietFor = System.currentTimeMillis() - lastUserActivityAt
                if (session.isSpeaking || quietFor < 4_500 || nudges >= 3) continue
                nudges++
                lastUserActivityAt = System.currentTimeMillis()
                session.sendText("[PLAN CONTINUE] The user already confirmed the whole plan — do not ask anything, just carry on.\\n" +
                        plan.nextInstruction())
            }
        }
    }

    private fun startLiveTicker() {
        startPlanWatch()
        liveTicker?.cancel()''')
rep('''        liveTicker?.cancel()
        liveTicker = null''', '''        liveTicker?.cancel()
        liveTicker = null
        planWatch?.cancel()
        planWatch = null''')
save(p)
print("ok")
