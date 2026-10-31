import os
root = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'


def edit(p, a, b, cnt=1):
    p = os.path.join(root, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


edit('res/values/strings.xml', '</resources>', '''    <string name="shortcut_talk">Talk to Faiza</string>
    <string name="shortcut_talk_long">Talk to Faiza now</string>
    <string name="shortcut_gaming">Gaming mode</string>
    <string name="shortcut_code">Code Studio</string>
    <string name="tile_label">Faiza</string>
</resources>''')

edit('AndroidManifest.xml', '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>''', '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />
        </activity>

        <!-- Quick Settings tile: pull down the shade, tap "Faiza", talk. -->
        <service
            android:name=".assist.FaizaTileService"
            android:exported="true"
            android:icon="@drawable/ic_mic"
            android:label="@string/tile_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>''')

# Earphone double press needs key events.
edit('res/xml/accessibility_config.xml', '''    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows"''',
     '''    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows|flagRequestFilterKeyEvents"
    android:canRequestFilterKeyEvents="true"''')

p = 'java/com/faiza/ai/service/FaizaAccessibilityService.kt'
edit(p, '''    override fun onInterrupt() {}''', '''    override fun onInterrupt() {}

    private val headset = com.faiza.ai.assist.HeadsetDoublePress()

    /** Earphone button pressed twice quickly → Faiza listens (the first press still plays/pauses music). */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (!com.faiza.ai.FaizaApp.instance.prefs.isHeadsetTrigger) return false
        return headset.onKey(event.keyCode, event.action, event.eventTime) {
            handler.post { com.faiza.ai.assist.AssistLauncher.activate(this) }
        }
    }''')

edit('java/com/faiza/ai/util/PrefsManager.kt', '''    /** What she texts a caller when''', '''    /** Double-press the earphone button to talk to Faiza. */
    var isHeadsetTrigger: Boolean
        get() = prefs.getBoolean("headset_trigger", true)
        set(value) = prefs.edit().putBoolean("headset_trigger", value).apply()

    /** What she texts a caller when''')
print('ok')
