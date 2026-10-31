import os, sys
android = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android'
main = os.path.join(android, 'app', 'src', 'main')
sys.path.insert(0, os.path.join(android, 'tools'))


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


J = 'java/com/faiza/ai/'
edit(J + 'util/PrefsManager.kt', '''    /** Medicine courses (JSON). */''', '''    // Driving
    var homeAddress: String
        get() = prefs.getString("home_address", "") ?: ""
        set(value) = prefs.edit().putString("home_address", value).apply()
    var workAddress: String
        get() = prefs.getString("work_address", "") ?: ""
        set(value) = prefs.edit().putString("work_address", value).apply()
    /** Spoken warning above this speed (km/h); 0 = off. */
    var drivingSpeedLimit: Int
        get() = prefs.getInt("driving_speed_limit", 80)
        set(value) = prefs.edit().putInt("driving_speed_limit", value).apply()

    /** Medicine courses (JSON). */''')

edit('AndroidManifest.xml', '''        <activity android:name=".ui.KhataActivity"''', '''        <activity android:name=".ui.DrivingActivity"
            android:launchMode="singleTop"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.KhataActivity"''')

# Messages while driving: the whole text, then she offers to reply.
p = J + 'service/ProactiveAnnouncer.kt'
edit(p, '''    /** The phone call was picked up (true) or ended (false). */
    private val onCallEnded: (Boolean) -> Unit = {}
) {''', '''    /** The phone call was picked up (true) or ended (false). */
    private val onCallEnded: (Boolean) -> Unit = {},
    /** Driving: a message to read out in full (line, app, sender). */
    private val onDrivingMessage: ((String, String, String) -> Unit)? = null
) {''')
edit(p, '''            } else if (prefs.isProactiveMode) {
                handleMessageNotification(sbn)
            }''', '''            } else if (prefs.isProactiveMode || com.faiza.ai.ai.driving.Driving.active(context)) {
                handleMessageNotification(sbn)
            }''')
edit(p, '''        speak(Phrases.newMessage(language, appName, sender))''', '''        if (com.faiza.ai.ai.driving.Driving.active(context) && onDrivingMessage != null) {
            val bangla = language.base == com.faiza.ai.ai.PhraseBase.BENGALI
            val who = com.faiza.ai.ai.calls.CallAssist.friendlyName(null, com.faiza.ai.ai.voice.VoiceNotes.cleanSender(sender)) ?: sender
            onDrivingMessage.invoke(com.faiza.ai.ai.driving.Driving.messageLine(bangla, who, appName, text), appName, who)
            return
        }
        speak(Phrases.newMessage(language, appName, sender))''')
edit(p, '''        if (sbn.packageName == context.packageName) return''', '''        if (sbn.packageName == context.packageName) return
        // Driving: messages count even when "read my messages" is off.''')
edit(p, '''        FloatingOrbService.instance?.let { }''' if False else '''        FaizaNotificationListener.onPosted =
            if (wantCalls || prefs.isProactiveMode) ::onNotification else null''', '''        // Always listen (cheap): calls, proactive messages, and messages while driving.
        FaizaNotificationListener.onPosted = ::onNotification''')

p = J + 'service/FloatingOrbService.kt'
edit(p, '''            onCallEnded = { offhook -> onPhoneCallEnded(offhook) }''', '''            onCallEnded = { offhook -> onPhoneCallEnded(offhook) },
            onDrivingMessage = { line, app, who -> onDrivingMessage(line, app, who) }''')
edit(p, '''    // ---------------- Gaming mode ----------------''', '''    /** Driving: read the message out and offer to reply by voice. */
    private fun onDrivingMessage(line: String, app: String, who: String) {
        if (guardMode || OwnerGuard.needsCode() || !QuietMode.canSpeakUnprompted(this)) return
        val ask = "[DRIVING — new message] Say exactly: \\"$line\\". Then ask in a few words if they want to reply. If they " +
                "dictate a reply, read it back once in a few words and send it to $who on $app with your messaging tool " +
                "(confirmed=true after they agree). Keep everything very short — they are driving."
        val session = live
        when {
            session != null -> session.sendText(ask)
            prefs.canUseLive() && !micPaused -> { callLive = true; startLive(ask) }
            else -> speak(line)
        }
    }

    // ---------------- Gaming mode ----------------''')

p = J + 'ai/FaizaAgent.kt'
edit(p, '''        if ("medicine" !in disabled) skills += com.faiza.ai.ai.vision.Medicine.skill()''', '''        if ("medicine" !in disabled) skills += com.faiza.ai.ai.vision.Medicine.skill()
        if ("driving" !in disabled) skills += com.faiza.ai.ai.driving.Driving.skill()''')
edit(p, '''            - MEDICINE:''', '''            - DRIVING: "গাড়ি চালাচ্ছি / ড্রাইভিং মোড" → driving start; "বাসায় নিয়ে চলো" → driving home; work likewise.
            - MEDICINE:''')

edit(J + 'ui/DashboardActivity.kt', '''            Triple(R.drawable.ic_book, "Money book")''', '''            Triple(R.drawable.ic_place, "Driving") { startActivity(Intent(this, DrivingActivity::class.java)) },
            Triple(R.drawable.ic_book, "Money book")''')
edit(J + 'ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowKhata).setOnClickListener {''', '''        findViewById<View>(R.id.rowDriving).setOnClickListener {
            startActivity(Intent(this, DrivingActivity::class.java))
        }
        findViewById<View>(R.id.rowKhata).setOnClickListener {''')
import settings_rows as sr
sr.apply('rowKhata', [sr.row('driving', 'ic_place', 'cyan', 'Driving', 'Big buttons, speed warning, messages read aloud')])
print('ok')
