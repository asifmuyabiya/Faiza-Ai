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


# ---- Chat layout: title gets an id ----
p = root + r'\res\layout\activity_chat.xml'
load(p)
rep('''            <TextView
                style="@style/Text.Faiza.Title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Chat"
                android:textSize="20sp" />''', '''            <TextView
                android:id="@+id/tvChatTitle"
                style="@style/Text.Faiza.Title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Chat"
                android:textSize="20sp" />''')
save(p)

# ---- Chat: workspace rooms ----
p = base + r'\ui\ChatActivity.kt'
load(p)
rep('''    /** Which provider/model the agent was built for, so a settings change gets a fresh one. */
    var agentKey: String? = null
''', '''    /** Which provider/model the agent was built for, so a settings change gets a fresh one. */
    var agentKey: String? = null
    /** The work room this thread belongs to (null = normal chat). */
    var workspace: com.faiza.ai.ai.Workspace? = null
''')
rep('''class ChatActivity : AppCompatActivity() {
''', '''class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORKSPACE = "workspace"
    }
''')
rep('''        val persona = Persona.from(prefs.selectedPersona)
        findViewById<TextView>(R.id.tvChatSubtitle).text = "${prefs.assistantName(persona)} · replies in text"''',
    '''        val persona = Persona.from(prefs.selectedPersona)
        findViewById<TextView>(R.id.tvChatSubtitle).text = "${prefs.assistantName(persona)} · replies in text"
        // A work room from the side menu: its own thread, title and starter ideas.
        val room = com.faiza.ai.ai.Workspace.from(intent.getStringExtra(EXTRA_WORKSPACE))
        if (room != ChatSession.workspace) {
            ChatSession.clear()
            ChatSession.workspace = room
        }
        if (room != null) {
            findViewById<TextView>(R.id.tvChatTitle).text = "${room.emoji} ${room.title}"
            findViewById<TextView>(R.id.tvChatSubtitle).text = room.subtitle
            listOf(R.id.chip1, R.id.chip2, R.id.chip3).forEachIndexed { i, id ->
                findViewById<TextView>(id).text = room.chips[i]
            }
        }''')
rep('''                ChatSession.Message(false, agent.handle(text, files))''',
    '''                ChatSession.Message(false, agent.handle(roomPrefix() + text, files))''')
rep('''    private fun send() {''', '''    /** The room's instructions ride along with every message (short, and survives history trimming). */
    private fun roomPrefix(): String =
        ChatSession.workspace?.let { "[${it.title.uppercase()} ROOM — ${it.instruction}]\\n\\n" } ?: ""

    private fun send() {''')
save(p)

# ---- Prefs: user's own rules ----
p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    /** The multi-task plan in progress (JSON), or null. */''', '''    /** The user's own rules for her, one per line ("Faiza Rules"). */
    var userRules: String
        get() = prefs.getString("user_rules", "") ?: ""
        set(value) = prefs.edit().putString("user_rules", value).apply()

    /** The multi-task plan in progress (JSON), or null. */''')
save(p)

# ---- Agent: rules in every prompt ----
p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''            AddressOptions.promptLine(prefs.userName, prefs.userGender, addr, AppLanguage.from(prefs.language).base)
                .takeIf { it.isNotBlank() }?.let { appendLine(it) }''', '''            AddressOptions.promptLine(prefs.userName, prefs.userGender, addr, AppLanguage.from(prefs.language).base)
                .takeIf { it.isNotBlank() }?.let { appendLine(it) }
            val rules = prefs.userRules.lines().map { it.trim().removePrefix("-").trim() }.filter { it.isNotEmpty() }.take(30)
            if (rules.isNotEmpty()) {
                appendLine("- THE USER'S OWN RULES FOR YOU — always follow them (unless one would be unsafe or illegal):")
                rules.forEach { appendLine("  • $it") }
            }''')
save(p)

# ---- Side menu ----
p = base + r'\ui\DashboardActivity.kt'
load(p)
rep('''            Triple(R.drawable.ic_moon, "Life modes") { startActivity(Intent(this, ModesActivity::class.java)) },''',
    '''            Triple(R.drawable.ic_moon, "Life modes") { startActivity(Intent(this, ModesActivity::class.java)) },
            Triple(R.drawable.ic_today, "Markets") { openRoom("markets") },
            Triple(R.drawable.ic_campaign, "Marketing") { openRoom("marketing") },
            Triple(R.drawable.ic_book, "Documents") { openRoom("documents") },
            Triple(R.drawable.ic_chip, "Website / Coding") { openRoom("coding") },
            Triple(R.drawable.ic_edit, "Study / Whiteboard") { openRoom("study") },
            Triple(R.drawable.ic_check, "Faiza rules") { startActivity(Intent(this, RulesActivity::class.java)) },''')
rep('''    private fun renderModeCard() {''', '''    private fun openRoom(id: String) =
        startActivity(Intent(this, ChatActivity::class.java).putExtra(ChatActivity.EXTRA_WORKSPACE, id))

    private fun renderModeCard() {''')
save(p)

# ---- Manifest ----
p = root + r'\AndroidManifest.xml'
load(p)
rep('''        <activity android:name=".ui.AvatarSetupActivity"''', '''        <activity android:name=".ui.RulesActivity"
            android:theme="@style/Theme.FaizaAI"
            android:windowSoftInputMode="adjustResize" />
        <activity android:name=".ui.AvatarSetupActivity"''')
save(p)
print("ok")
