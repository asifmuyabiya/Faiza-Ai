p = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai\service\FloatingOrbService.kt'
s = open(p, encoding='utf-8').read()


def rep(a, b, cnt=1):
    global s
    assert s.count(a) == cnt, (a[:70], s.count(a))
    s = s.replace(a, b)


rep("""    private var strangerStrikes = 0
""", """    private var strangerStrikes = 0

    // Owner lock: this conversation is with someone who hasn't said the secret code yet.
    private var guardMode = false
    private val guardHeard = StringBuilder()
    private var guardJudge: kotlinx.coroutines.Job? = null
""")

rep("""    fun handleTyped(text: String) {
        lastUserActivityAt = System.currentTimeMillis()
        val session = live
        when {""", """    fun handleTyped(text: String) {
        lastUserActivityAt = System.currentTimeMillis()
        val session = live
        if (session != null && guardMode) {
            guardAttempt(text)
            return
        }
        when {""")

rep("""say a short goodbye and call go_to_sleep."
""", """say a short goodbye and call go_to_sleep."

        // Owner lock: someone who hasn't said the secret code gets a guard with no tools and no knowledge of the code.
        guardMode = OwnerGuard.needsCode()
        guardHeard.setLength(0)
        guardJudge?.cancel()
        var openingText = greeting
        if (guardMode && greeting != null && !greeting.startsWith("The user just") && greeting != ASSIST_GREETING &&
            greeting != CINEMATIC_GREETING && !greeting.startsWith("[")
        ) {
            // They typed something: that's their first attempt.
            guardHeard.append(greeting)
            openingText = null
        }
        val liveTools = if (guardMode) emptyList() else tools
        val livePrompt = if (guardMode) OwnerLock.guardPrompt(prefs.assistantName(persona), ownerName(), language.promptName) else prompt
        val liveGreeting = if (guardMode) "[GUARD] Say exactly: \\"${OwnerLock.askLine(OwnerGuard.base)}\\"" else openingText
""")
rep("""            systemPrompt = prompt,
            tools = tools,""", """            systemPrompt = livePrompt,
            tools = liveTools,""")
rep("""            louderVoice = prefs.isLouderVoice
        ).also { it.start(greeting) }""", """            louderVoice = prefs.isLouderVoice,
            callSpeaker = prefs.isCallSpeaker,
            micGain = com.faiza.ai.ai.live.MicBoost.gainFor(prefs.micBoostLevel)
        ).also { it.start(liveGreeting) }
        if (guardMode && guardHeard.isNotEmpty()) scheduleGuardJudge(2_500)""")

rep("""    private fun stopLive() {
        flushTranscript(userTranscript, "user")""", """    private fun stopLive(endConversation: Boolean = true) {
        guardJudge?.cancel()
        guardHeard.setLength(0)
        if (endConversation && live != null && !guardMode) OwnerGuard.onConversationEnded()
        flushTranscript(userTranscript, "user")""")

rep("""        override fun onUserSpeech(text: String) {
            flushTranscript(modelTranscript, "assistant")""", """        override fun onUserSpeech(text: String) {
            if (guardMode) {
                // Never log what a stranger (or the secret code) says; check for the code as the words arrive.
                modelTranscript.setLength(0)
                guardHeard.append(text)
                if (OwnerGuard.saysCode(guardHeard.toString())) ownerUnlocked() else scheduleGuardJudge(1_600)
                return
            }
            flushTranscript(modelTranscript, "assistant")""")
rep("""        override fun onModelSpeech(text: String) {
            flushTranscript(userTranscript, "user")""", """        override fun onModelSpeech(text: String) {
            if (guardMode) return
            flushTranscript(userTranscript, "user")""")

rep("""    private fun onGuardianVerdict(v: SpeakerVerifier.Verdict) {
        if (!v.reliable || live == null) return""", """    private fun onGuardianVerdict(v: SpeakerVerifier.Verdict) {
        if (!v.reliable || live == null) return
        if (guardMode) {
            // Voice Guardian recognised the boss's own voice: no code needed.
            if (v.allowed && prefs.voiceGuardianMode == PrefsManager.VG_MODE_OWNER) ownerUnlocked()
            return
        }""")

rep("""    // ========== MIC SHARING ==========""", """    // ========== OWNER LOCK (secret code) ==========

    /** They stopped talking without saying the code: judge what they said. */
    private fun scheduleGuardJudge(afterMs: Long) {
        guardJudge?.cancel()
        guardJudge = scope.launch {
            delay(afterMs)
            val heard = guardHeard.toString().trim()
            guardHeard.setLength(0)
            if (heard.isNotEmpty() && guardMode) guardAttempt(heard)
        }
    }

    private fun guardAttempt(text: String) {
        val o = OwnerGuard.attempt(this, text, lockAfterMs = 7_000)
        when (o.kind) {
            OwnerGuard.Kind.UNLOCKED -> ownerUnlocked()
            OwnerGuard.Kind.LOCKING -> {
                live?.interruptWith("[GUARD] Say exactly, sternly and quickly: \\"${o.line}\\"")
                scope.launch {
                    delay(7_500)
                    stopLive()
                    resumeIdle()
                }
            }
            else -> live?.interruptWith("[GUARD] Say exactly, in a stern voice: \\"${o.line}\\"")
        }
    }

    /** The secret code was said (or the boss's voice recognised): a normal conversation with all her skills. */
    private fun ownerUnlocked() {
        if (!guardMode) return
        OwnerGuard.markVerified()
        guardMode = false
        stopLive(endConversation = false)
        startLive("[The owner just proved who they are. Say exactly, warmly: \\"${OwnerLock.welcomeLine(OwnerGuard.base)}\\"]")
    }

    // ========== MIC SHARING ==========""")

rep("""        if (!QuietMode.canSpeakUnprompted(this)) return
        val session = live""", """        if (!QuietMode.canSpeakUnprompted(this)) return
        // Owner lock: never read the boss's notifications to someone who hasn't said the code.
        if (guardMode || OwnerGuard.needsCode()) return
        val session = live""")
rep("""                if (prefs.isProactiveChat && live?.isSpeaking != true && proactiveNudges < PROACTIVE_MAX_NUDGES &&""",
    """                if (prefs.isProactiveChat && !guardMode && live?.isSpeaking != true && proactiveNudges < PROACTIVE_MAX_NUDGES &&""")

rep("""    private val lifeModeReceiver = com.faiza.ai.ai.modes.LifeModeReceiver()
""", """    private val lifeModeReceiver = com.faiza.ai.ai.modes.LifeModeReceiver()

    /** Screen off / phone unlocked: Owner lock asks for the code again ("after each unlock"). */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            OwnerGuard.onScreenEvent()
        }
    }
""")
rep("""        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(lifeModeReceiver, filter, RECEIVER_EXPORTED)
            else registerReceiver(lifeModeReceiver, filter)
        }""", """        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(lifeModeReceiver, filter, RECEIVER_EXPORTED)
            else registerReceiver(lifeModeReceiver, filter)
        }
        runCatching {
            registerReceiver(screenReceiver, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            })
        }""")
rep("""        runCatching { unregisterReceiver(lifeModeReceiver) }""", """        runCatching { unregisterReceiver(lifeModeReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }""")
rep("""import com.faiza.ai.ai.guardian.SpeakerVerifier
""", """import com.faiza.ai.ai.guardian.OwnerGuard
import com.faiza.ai.ai.guardian.OwnerLock
import com.faiza.ai.ai.guardian.SpeakerVerifier
""")
rep("""    /** A Life mode started or ended: update the notification and the dashboard. */""", """    /** Call speaker / soft-voice settings changed: apply them to the conversation now. */
    fun onAudioSettingsChanged() {
        if (live != null && !guardMode) restartLive()
    }

    /** A Life mode started or ended: update the notification and the dashboard. */""")
open(p, 'w', encoding='utf-8').write(s)
print("ok")
