import os
base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b, cnt=1):
    p = os.path.join(base, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- CallAssist: ringing expires (app calls have no "ended" signal) ----------
p = 'ai/calls/CallAssist.kt'
edit(p, '''    @Volatile var ringing: Ringing? = null
        private set''', '''    @Volatile private var current: Ringing? = null

    /** The call ringing now; app calls have no "ended" signal, so it expires after a minute. */
    val ringing: Ringing? get() = current?.takeIf { System.currentTimeMillis() - it.at < 60_000 }''')
edit(p, '''        val old = ringing
        ringing = Ringing(number ?: old?.number, name ?: old?.name, old?.app, old?.sbn)''', '''        val old = ringing
        current = Ringing(number ?: old?.number, name ?: old?.name, old?.app, old?.sbn)''')
edit(p, '''        ringing = Ringing(null, caller, CALL_APPS[sbn.packageName] ?: "app", sbn)''',
     '''        current = Ringing(null, caller, CALL_APPS[sbn.packageName] ?: "app", sbn)''')
edit(p, '''    fun ended() { ringing = null }''', '''    fun ended() { current = null }''')

# ---------- ProactiveAnnouncer ----------
p = 'service/ProactiveAnnouncer.kt'
edit(p, '''    private val speak: (String) -> Unit
) {''', '''    private val speak: (String) -> Unit,
    /** An incoming call: the line to say ("বস, আম্মু ফোন দিছে"); the service also lets her take orders. */
    private val onCall: (String) -> Unit = speak,
    /** The phone call was picked up (true) or ended (false). */
    private val onCallEnded: (Boolean) -> Unit = {}
) {''')
edit(p, '''                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (number != null) pendingNumber = number''', '''                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    if (number != null) pendingNumber = number
                    com.faiza.ai.ai.calls.CallAssist.phoneRinging(number, null)''')
edit(p, '''                else -> {
                    isRinging = false
                    pendingNumber = null
                    lastCallAnnouncedAt = 0L
                    handler.removeCallbacks(announceCallFallback)
                }''', '''                else -> {
                    val offhook = intent.getStringExtra(TelephonyManager.EXTRA_STATE) == TelephonyManager.EXTRA_STATE_OFFHOOK
                    val wasRinging = isRinging
                    isRinging = false
                    pendingNumber = null
                    lastCallAnnouncedAt = 0L
                    handler.removeCallbacks(announceCallFallback)
                    com.faiza.ai.ai.calls.CallAssist.ended()
                    if (wasRinging || offhook) onCallEnded(offhook)
                }''')
edit(p, '''        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        handler.removeCallbacks(announceCallFallback)
        announceCall(title?.takeIf { it.isNotEmpty() })''', '''        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        handler.removeCallbacks(announceCallFallback)
        val ca = com.faiza.ai.ai.calls.CallAssist
        if (sbn.packageName in ca.CALL_APPS) ca.appRinging(sbn, title) else ca.phoneRinging(null, title)
        announceCall(title)''')
edit(p, '''    private fun announceCall(caller: String?) {
        lastCallAnnouncedAt = System.currentTimeMillis()
        speak(Phrases.incomingCall(language, caller))
    }''', '''    private fun announceCall(caller: String?) {
        lastCallAnnouncedAt = System.currentTimeMillis()
        val ca = com.faiza.ai.ai.calls.CallAssist
        val r = ca.ringing
        val number = r?.number ?: pendingNumber
        val bangla = language.base == com.faiza.ai.ai.PhraseBase.BENGALI
        val address = prefs.userAddress.trim().ifEmpty { if (bangla) "বস" else "Boss" }
        val name = ca.friendlyName(number, caller ?: number?.let { lookupContactName(it) })
        onCall(ca.announceLine(bangla, address, name, r?.app, number))
    }''')

# ---------- Service ----------
p = 'service/FloatingOrbService.kt'
edit(p, '''            speak = { announce(it) }
        ).also { it.refresh() }''', '''            speak = { announce(it) },
            onCall = { onIncomingCall(it) },
            onCallEnded = { offhook -> onPhoneCallEnded(offhook) }
        ).also { it.refresh() }''')
edit(p, '''    // ---------------- Gaming mode ----------------''', '''    // ---------------- Calls ----------------

    /** A live chat started just to announce a call (ended again once the call is handled). */
    private var callLive = false
    /** We stopped the live chat so the phone call can have the microphone. */
    private var stoppedForCall = false
    /** A phone call is in progress: don't (re)open the live chat. */
    @Volatile private var inPhoneCall = false

    private fun onIncomingCall(line: String) {
        if (guardMode || OwnerGuard.needsCode() || !QuietMode.canSpeakUnprompted(this)) return
        val ask = "[INCOMING CALL] Say exactly: \\"$line\\" — then ask in a few words what to do (ধরব, কেটে দেব, নাকি " +
                "\\"পরে কল দিচ্ছি\\" মেসেজ পাঠাব?). When the user answers, use the incoming_call tool. If they say nothing, stay quiet."
        val session = live
        when {
            session != null -> session.interruptWith(ask)
            prefs.canUseLive() && !micPaused -> { callLive = true; startLive(ask) }
            else -> speak(line)
        }
    }

    /** The user told her to answer / decline / reply. */
    fun onCallHandled(answered: Boolean) {
        scope.launch {
            if (answered) {
                // The call needs the microphone right away.
                delay(600)
                if (callLive || com.faiza.ai.ai.game.GameMode.active.value) { stopLive(endConversation = false); stoppedForCall = true }
                callLive = false
            } else if (callLive) {
                callLive = false
                delay(2500)
                sleepLive()
            }
        }
    }

    private fun onPhoneCallEnded(offhook: Boolean) {
        if (offhook) {
            inPhoneCall = true
            if (live != null && (callLive || com.faiza.ai.ai.game.GameMode.active.value)) { stopLive(endConversation = false); stoppedForCall = true }
            callLive = false
            return
        }
        inPhoneCall = false
        if (callLive) { callLive = false; sleepLive() }
        else if (stoppedForCall && live == null && !com.faiza.ai.ai.game.GameMode.active.value) resumeIdle()
        stoppedForCall = false
    }

    // ---------------- Gaming mode ----------------''')
edit(p, '''            if (session == null) {
                missing++''', '''            if (session == null) {
                if (inPhoneCall) { delay(1000); continue }
                missing++''')

# ---------- Prefs ----------
edit('util/PrefsManager.kt', '''    // Gaming mode''', '''    /** What she texts a caller when the user says "পরে কল দিচ্ছি বলে দাও" (empty = default). */
    var busyReplyText: String
        get() = prefs.getString("busy_reply_text", "") ?: ""
        set(value) = prefs.edit().putString("busy_reply_text", value).apply()

    // Gaming mode''')

# ---------- Agent ----------
p = 'ai/FaizaAgent.kt'
edit(p, '''        if ("ff_info" !in disabled) skills += com.faiza.ai.ai.game.FreeFireData.skill()''',
     '''        if ("ff_info" !in disabled) skills += com.faiza.ai.ai.game.FreeFireData.skill()
        if ("incoming_call" !in disabled) skills += com.faiza.ai.ai.calls.CallAssist.skill()''')
edit(p, '''            - GAMES:''', '''            - RINGING CALL: "ধরো/রিসিভ করো" → incoming_call answer; "কেটে দাও" → decline; "পরে কল দিচ্ছি বলে দাও / মেসেজ
              দিয়ে দাও" → reply (text in the user's words if they gave one).
            - GAMES:''')
edit('ai/game/GameMode.kt', '''    val GAMING_TOOLS = setOf("gaming_mode", "ff_info", "window_music", "calculate")''',
     '''    val GAMING_TOOLS = setOf("gaming_mode", "ff_info", "incoming_call", "window_music", "calculate")''')
print('ok')
