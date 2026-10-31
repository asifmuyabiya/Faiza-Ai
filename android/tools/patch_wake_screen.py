import sys
sys.path.insert(0, r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools')
from settings_rows import row, apply

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


p = base + r'\util\PrefsManager.kt'
load(p)
rep('''    // ====== Avatar (living picture instead of the orb) ======''', '''    /** Listen for "Hey Faiza" only while the screen is on (saves a lot of battery). */
    var isWakeScreenOnly: Boolean
        get() = prefs.getBoolean("wake_screen_only", false)
        set(value) = prefs.edit().putBoolean("wake_screen_only", value).apply()

    // ====== Avatar (living picture instead of the orb) ======''')
save(p)

p = base + r'\service\FloatingOrbService.kt'
load(p)
rep('''        override fun onReceive(context: android.content.Context, intent: Intent) {
            OwnerGuard.onScreenEvent()
        }''', '''        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) OwnerGuard.onScreenEvent()
            // "Hey Faiza only while the screen is on": rest the mic and processor while it's off.
            if (!prefs.isWakeScreenOnly || live != null) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    wakeEngine?.stop()
                    wakeRestartJob?.cancel()
                    speechRecognizer?.cancel()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startListeningForWakeWord()
            }
        }''')
rep('''                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)''', '''                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)''')
rep('''        if (QuietMode.manualActive()) return // quiet mode: no always-on listening''', '''        if (QuietMode.manualActive()) return // quiet mode: no always-on listening
        if (prefs.isWakeScreenOnly &&
            (getSystemService(POWER_SERVICE) as PowerManager).isInteractive.not()
        ) return // screen off: listening resumes when it turns on''')
save(p)

apply("rowWakeWord", [
    row("wakeScreenOnly", "ic_battery", "green", "Listen only when screen is on",
        'Saves battery: Hey Faiza rests while the screen is off', 'switch'),
])
p = base + r'\ui\SettingsActivity.kt'
load(p)
rep('''        val swWake = findViewById<SwitchMaterial>(R.id.swWakeWord)''', '''        val swWakeScreen = findViewById<SwitchMaterial>(R.id.swWakeScreenOnly)
        swWakeScreen.isChecked = prefs.isWakeScreenOnly
        swWakeScreen.setOnCheckedChangeListener { _, on -> prefs.isWakeScreenOnly = on }
        findViewById<View>(R.id.rowWakeScreenOnly).setOnClickListener { swWakeScreen.toggle() }
        val swWake = findViewById<SwitchMaterial>(R.id.swWakeWord)''')
save(p)
print("ok")
