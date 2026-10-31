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


p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''class FaizaAgent(
    private val context: Context,
    private val provider: AIProvider
) {''', '''class FaizaAgent(
    private val context: Context,
    private val provider: AIProvider,
    /**
     * A team member working in the background for the task manager (not talking to the user): this text is its role.
     * Workers never ask for confirmation, never plan or delegate, and don't write to the conversation memory.
     */
    private val workerRole: String? = null
) {''')
rep('''        // Owner lock: a stranger gets no answer and no command runs until the secret code is said.
        if (com.faiza.ai.ai.guardian.OwnerGuard.needsCode()) {''', '''        // Owner lock: a stranger gets no answer and no command runs until the secret code is said.
        if (workerRole == null && com.faiza.ai.ai.guardian.OwnerGuard.needsCode()) {''')
rep('''        MemoryStore.logLine(
            "user",
            userText + if (files.isEmpty()) "" else " [attached: ${files.joinToString { it.name }}]"
        )''', '''        if (workerRole == null) MemoryStore.logLine(
            "user",
            userText + if (files.isEmpty()) "" else " [attached: ${files.joinToString { it.name }}]"
        )''')
rep('''                    val answer = reply.text?.trim().orEmpty().ifEmpty { "Done." }
                    MemoryStore.logLine("assistant", answer)''', '''                    val answer = reply.text?.trim().orEmpty().ifEmpty { "Done." }
                    if (workerRole == null) MemoryStore.logLine("assistant", answer)''')
rep('''                val reply = provider.chat(systemPrompt(), history, specs)''',
    '''                val reply = provider.chat(if (workerRole != null) workerPrompt() else systemPrompt(), history, specs)''')
rep('''    fun currentSkills(): List<Skill> = activeSkills()''', '''    fun currentSkills(): List<Skill> = activeSkills()

    /** A worker's instructions: its role, the normal phone-control know-how, and "finish without asking". */
    private fun workerPrompt(): String = """
        $workerRole

        You are a background worker in Faiza's team on the user's Android phone. You do NOT talk to the user.
        The user has ALREADY confirmed this whole job: never ask for confirmation — use confirmed=true on every tool
        that has it (messages, sharing, sending). Work step by step with the tools until the task is really done:
        open apps, read_screen, tap, type, check the screen after each action. If something fails, try another way
        (up to 3 tries). Never claim success unless a tool said OK.
        Finish with ONE line, in English: "DONE: <what you did, with names/details>" or "FAILED: <why>".
        If the task produced something the next steps need (a photo, a file name, a link, a result), mention it in that line.
    """.trimIndent() + "\\n\\nREFERENCE (how the phone tools work):\\n" + systemPrompt()
''')
rep('''    private fun activeSkills(): List<Skill> {''', '''    /** Tools a worker must not use (no planning, no delegating back, no ending the voice session). */
    private val WORKER_EXCLUDED = setOf(
        "plan_tasks", "start_plan", "step_done", "cancel_plan", "delegate_tasks", "cancel_team", "team_status", "go_to_sleep"
    )

    private fun activeSkills(): List<Skill> {
        val all = baseSkills()
        return if (workerRole != null) all.filter { it.id !in WORKER_EXCLUDED } else all
    }

    private fun baseSkills(): List<Skill> {''')
rep('''        skills += com.faiza.ai.ai.skills.TaskPlan.skills()''', '''        skills += com.faiza.ai.ai.skills.TaskPlan.skills()
        skills += com.faiza.ai.ai.agents.TaskManager.skills()''')
save(p)

# take_photo: the photo becomes "the last created file", so share_file(number=0) sends it.
p = base + r'\ai\files\FileSkills.kt'
load(p)
rep('''    private var lastCreated: Pair<String, Uri>? = null''', '''    private var lastCreated: Pair<String, Uri>? = null

    /** A photo / file made elsewhere (camera) becomes "number 0" for share_file. */
    fun setLastCreated(name: String, uri: Uri) {
        lastCreated = name to uri
    }''')
save(p)

p = base + r'\ai\skills\BuiltInSkills.kt'
load(p)
rep('''            if (uri != null) "OK: photo taken and saved to the gallery (Pictures/Faiza AI)" else "ERROR: the photo wasn't taken (camera closed or busy)"''',
    '''            if (uri != null) {
                com.faiza.ai.ai.files.FileSkills.setLastCreated("Faiza photo.jpg", uri)
                "OK: photo taken and saved to the gallery (Pictures/Faiza AI). To send it, use share_file with number 0."
            } else "ERROR: the photo wasn't taken (camera closed or busy)"''')
save(p)

# Shared screen: the team's screen work counts as "phone busy" for TaskLanes.
p = base + r'\ai\skills\TaskLanes.kt'
load(p)
rep('''    /** Waits (up to [maxMs]) until every phone-lane task has finished. */''', '''    /** Runs [block] holding the one screen (used by the task manager's screen agents). */
    suspend fun <T> withScreen(block: suspend () -> T): T {
        phonePending.incrementAndGet()
        try {
            return phoneLock.withLock { block() }
        } finally {
            phonePending.decrementAndGet()
        }
    }

    /** Waits (up to [maxMs]) until every phone-lane task has finished. */''')
save(p)
print("ok")
