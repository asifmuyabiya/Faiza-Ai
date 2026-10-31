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


# Prefs
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    // ====== Avatar (living picture instead of the orb) ======''', '''    /** The multi-task plan in progress (JSON), or null. */
    var taskPlanJson: String?
        get() = prefs.getString("task_plan", null)
        set(value) = prefs.edit().putString("task_plan", value).apply()

    // ====== Avatar (living picture instead of the orb) ======''')
save(p)

# Agent: tools, step limit, prompt
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''        private const val MAX_STEPS = 16''', '''        /** Tool rounds per request — a confirmed multi-task plan can need many. */
        private const val MAX_STEPS = 80''')
rep('''        skills += com.faiza.ai.ai.skills.AutoScroll.skills().filter { it.id !in disabled }''',
    '''        skills += com.faiza.ai.ai.skills.AutoScroll.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.skills.TaskPlan.skills()''')
rep('''            - TALK WHILE YOU WORK: for any task with several steps,''', '''            - MANY TASKS AT ONCE: if the user gives two or more tasks in one command (messages to several people,
              posting a story, deleting photos, opening apps…), FIRST call plan_tasks with every task as its own precise
              step in their order (who + the exact message in their words + which app / which photos). Read the plan
              back in ONE short sentence and ask ONCE "শুরু করব?" (skip the question if they already said "সব করে দাও").
              When they agree → start_plan. Then for each step: one short sentence about what you're doing, do it,
              check the screen, say what you did, call step_done. Never ask again inside a confirmed plan. If a step
              fails after two tries → step_done(ok=false, note=why) and go on. At the end summarise and ask them to check.
              "থামো / stop / বাতিল" during a plan → cancel_plan.
              Several WhatsApp messages: for each person open_whatsapp_chat → type_text → press_send (or whatsapp_message
              with confirmed=true). Gallery "delete the 1st, 2nd and 4th photo": open the Gallery app, read_screen
              (newest photos come first), long_press the 1st photo to start selecting, tap the 2nd and 4th, tap
              Delete / Move to bin, confirm. Facebook story "Hi": open Facebook, tap Create story / +, choose Text,
              type the text, tap Share / Post.
            - TALK WHILE YOU WORK: for any task with several steps,''')
save(p)

# Messages inside a confirmed plan count as confirmed
p = base + r'\ai\skills\BuiltInSkills.kt'
load(p)
rep('''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend) return NOT_CONFIRMED
        if (!hasPermission(ctx, Manifest.permission.SEND_SMS))''', '''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend && !TaskPlan.running) return NOT_CONFIRMED
        if (!hasPermission(ctx, Manifest.permission.SEND_SMS))''')
rep('''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend) return NOT_CONFIRMED
        val phoneMatch''', '''        if (!confirmed && FaizaApp.instance.prefs.isConfirmBeforeSend && !TaskPlan.running) return NOT_CONFIRMED
        val phoneMatch''')
save(p)

# Delete guard: quiet while Faiza runs the user's plan
p = base + r'\ai\guardian\DeleteGuard.kt'
load(p)
rep('''        if (SystemClock.elapsedRealtime() < FaizaAccessibilityService.automationUntil) return''',
    '''        if (SystemClock.elapsedRealtime() < FaizaAccessibilityService.automationUntil) return
        if (com.faiza.ai.ai.skills.TaskPlan.running) return''')
save(p)

# Service: a reconnect / new conversation in the middle of a plan carries on
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''        val liveGreeting = if (guardMode) "[GUARD] Say exactly: \\"${OwnerLock.askLine(OwnerGuard.base)}\\"" else openingText''',
    '''        val planNote = if (guardMode) null else com.faiza.ai.ai.skills.TaskPlan.resumeNote()
        val liveGreeting = when {
            guardMode -> "[GUARD] Say exactly: \\"${OwnerLock.askLine(OwnerGuard.base)}\\""
            planNote != null && (openingText == null || openingText.startsWith("The user just") ||
                    openingText == ASSIST_GREETING || openingText == CINEMATIC_GREETING) -> planNote
            else -> openingText
        }''')
save(p)
print("ok")
