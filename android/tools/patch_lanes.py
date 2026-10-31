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


# ---- take_photo tool ----
p = base + r'\ai\skills\BuiltInSkills.kt'
load(p)
rep('''        Skill(
            "press_send", "📨", "Press Send",''', '''        Skill(
            "take_photo", "📸", "Take a photo", "\\"আমার একটা ছবি তোলো\\"",
            "Open the camera and take a photo: front camera for a selfie / \\"আমার ছবি\\", back camera for things around. " +
                    "Counts down so the user can pose, saves to the gallery (Pictures/Faiza AI) and shows it.",
            listOf(
                ToolParam("camera", ParamType.STRING, "front or back", required = false, enum = listOf("front", "back")),
                ToolParam("countdown", ParamType.INTEGER, "Seconds to count down (default 3)", required = false)
            )
        ) { ctx, args ->
            if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@Skill "ERROR: camera permission is off — ask the user to allow Camera for Faiza in Settings → Phone access"
            }
            val wait = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
            com.faiza.ai.ui.PhotoActivity.pending = wait
            launch(ctx, Intent(ctx, com.faiza.ai.ui.PhotoActivity::class.java)
                .putExtra(com.faiza.ai.ui.PhotoActivity.EXTRA_FRONT, args.str("camera") != "back")
                .putExtra(com.faiza.ai.ui.PhotoActivity.EXTRA_COUNTDOWN, (args.int("countdown") ?: 3).coerceIn(0, 10)))
            val uri = kotlinx.coroutines.withTimeoutOrNull(40_000) { wait.await() }
            if (uri != null) "OK: photo taken and saved to the gallery (Pictures/Faiza AI)" else "ERROR: the photo wasn't taken (camera closed or busy)"
        },

        Skill(
            "press_send", "📨", "Press Send",''')
save(p)

# ---- Service: helper lanes in the live conversation ----
p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''            return agent?.executeTool(call) ?: "ERROR: AI is not configured"
        }''', '''            val a = agent ?: return "ERROR: AI is not configured"
            // Slow, self-contained actions go to a helper; she keeps talking and hears back via [TASK DONE].
            if (com.faiza.ai.ai.skills.TaskLanes.laneOf(call.name) != null) {
                return com.faiza.ai.ai.skills.TaskLanes.submit(call, { c -> a.executeTool(c) }) { c, result ->
                    deliverTaskResult(c.name, result)
                }
            }
            // Anything that looks at / touches the screen waits until earlier phone tasks are finished.
            if (com.faiza.ai.ai.skills.TaskLanes.needsScreen(call.name)) com.faiza.ai.ai.skills.TaskLanes.awaitPhoneIdle()
            return a.executeTool(call)
        }''')
rep('''    /** The research agent finished: let the live conversation tell the user (the notification is already shown). */''',
    '''    /** Results from helpers, collected for a moment so several finishing together become one message. */
    private val doneResults = mutableListOf<String>()
    private var doneJob: Job? = null

    /** A helper finished: tell her (when she isn't mid-sentence), so she can tell the user and carry on. */
    private fun deliverTaskResult(tool: String, result: String) {
        synchronized(doneResults) { doneResults += "$tool → ${result.take(400)}" }
        if (doneJob?.isActive == true) return
        doneJob = scope.launch {
            delay(600)
            var waited = 0
            while (live?.isSpeaking == true && waited < 8_000) { delay(200); waited += 200 }
            val batch = synchronized(doneResults) { doneResults.toList().also { doneResults.clear() } }
            if (batch.isEmpty()) return@launch
            lastUserActivityAt = System.currentTimeMillis()
            live?.sendText(
                "[TASK DONE] " + batch.joinToString("\\n") + "\\nTell the user the outcome in ONE short sentence (say " +
                        "\\"done\\" only if it says OK; if ERROR / NOT SENT, say so honestly). If a task plan is running, " +
                        "call step_done and continue with the next step."
            )
        }
    }

    /** The research agent finished: let the live conversation tell the user (the notification is already shown). */''')
# The plan watchdog waits while helpers are still working.
rep('''                if (session.isSpeaking || quietFor < 4_500 || nudges >= 3) continue''',
    '''                if (session.isSpeaking || quietFor < 4_500 || nudges >= 3 || com.faiza.ai.ai.skills.TaskLanes.busy) continue''')
save(p)

# ---- Plan: a step is only "done" after its helpers finished ----
p = base + r'\ai\skills\TaskPlan.kt'
load(p)
rep('''            val p = get() ?: return@Skill "ERROR: no plan running"
            if (!p.confirmed) return@Skill "ERROR: the plan wasn't started — ask the user, then call start_plan"''',
    '''            val p = get() ?: return@Skill "ERROR: no plan running"
            if (!p.confirmed) return@Skill "ERROR: the plan wasn't started — ask the user, then call start_plan"
            // Helpers may still be sending / opening something for this step: wait for them first.
            TaskLanes.awaitAllIdle()''')
save(p)

# ---- Prompt ----
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''            - TALK WHILE YOU WORK: for any task with several steps,''', '''            - HELPERS (voice conversation): slow actions (opening apps, WhatsApp, calls, camera, searching…) are done by
              your helper agents. Their tool answers "STARTED" immediately: at once say a short, lively sentence about
              what's happening ("হ্যাঁ বস, ক্যামেরা খুলছি…", "ভাইয়াকে মেসেজটা পাঠাচ্ছি…") and keep the conversation going —
              never go silent. When a "[TASK DONE]" message arrives, give the result in one short sentence. Never say
              something is done before its [TASK DONE] says OK. Photos → take_photo (front camera for "আমার ছবি").
            - TALK WHILE YOU WORK: for any task with several steps,''')
save(p)

# ---- Manifest ----
p = root + r'\AndroidManifest.xml'
load(p)
rep('''        <activity android:name=".ui.ResearchActivity"''', '''        <activity android:name=".ui.PhotoActivity"
            android:excludeFromRecents="true"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.ResearchActivity"''')
save(p)
print("ok")
