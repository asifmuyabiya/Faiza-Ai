import os, sys
android = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android'
main = os.path.join(android, 'app', 'src', 'main')
sys.path.insert(0, os.path.join(android, 'tools'))


def edit(p, a, b, cnt=1):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == cnt, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


J = 'java/com/faiza/ai/'

edit(J + 'util/PrefsManager.kt', '''    /** Who sent which WhatsApp voice message''', '''    // Prayer
    var prayerLat: Float
        get() = prefs.getFloat("prayer_lat", 0f)
        set(value) = prefs.edit().putFloat("prayer_lat", value).apply()
    var prayerLng: Float
        get() = prefs.getFloat("prayer_lng", 0f)
        set(value) = prefs.edit().putFloat("prayer_lng", value).apply()
    var prayerMethod: String
        get() = prefs.getString("prayer_method", "KARACHI") ?: "KARACHI"
        set(value) = prefs.edit().putString("prayer_method", value).apply()
    var prayerHanafi: Boolean
        get() = prefs.getBoolean("prayer_hanafi", true)
        set(value) = prefs.edit().putBoolean("prayer_hanafi", value).apply()
    var hijriAdjust: Int
        get() = prefs.getInt("hijri_adjust", 0)
        set(value) = prefs.edit().putInt("hijri_adjust", value).apply()
    var azanReminders: Boolean
        get() = prefs.getBoolean("azan_reminders", false)
        set(value) = prefs.edit().putBoolean("azan_reminders", value).apply()
    var azanBeforeMin: Int
        get() = prefs.getInt("azan_before_min", 0)
        set(value) = prefs.edit().putInt("azan_before_min", value).apply()
    var ramadanReminders: Boolean
        get() = prefs.getBoolean("ramadan_reminders", true)
        set(value) = prefs.edit().putBoolean("ramadan_reminders", value).apply()
    var azanSoundUri: String?
        get() = prefs.getString("azan_sound_uri", null)
        set(value) = prefs.edit().putString("azan_sound_uri", value).apply()

    /** Who sent which WhatsApp voice message''')

# Life modes' prayer window: use the offline times when the online ones aren't there.
edit(J + 'ai/modes/ModeCenter.kt', '''    fun prayerTimesToday(): List<Int> {
        val cached = prefs.prayerTimesJson ?: return emptyList()
        return runCatching {
            val o = JsonParser.parseString(cached).asJsonObject
            if (o.get("day").asString != dayFmt.format(Date())) emptyList()
            else o.getAsJsonArray("times").map { it.asInt }
        }.getOrDefault(emptyList())
    }''', '''    fun prayerTimesToday(): List<Int> {
        val cached = prefs.prayerTimesJson
        val online = if (cached == null) emptyList() else runCatching {
            val o = JsonParser.parseString(cached).asJsonObject
            if (o.get("day").asString != dayFmt.format(Date())) emptyList()
            else o.getAsJsonArray("times").map { it.asInt }
        }.getOrDefault(emptyList())
        if (online.isNotEmpty()) return online
        // No internet copy for today: work them out on the phone.
        return runCatching {
            com.faiza.ai.ai.prayer.PrayerCenter.timesFor(FaizaApp.instance).prayers().map { it.second }.filter { it >= 0 }
        }.getOrDefault(emptyList())
    }''')

edit('AndroidManifest.xml', '''        <activity android:name=".ui.GamingActivity"''', '''        <activity android:name=".ui.PrayerActivity"
            android:launchMode="singleTop"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.FaizaAI" />
        <receiver
            android:name=".ai.prayer.PrayerAlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
        <activity android:name=".ui.GamingActivity"''')

edit(J + 'service/FloatingOrbService.kt', '''        EventTriggers.start(this)
''', '''        EventTriggers.start(this)
        runCatching { com.faiza.ai.ai.prayer.PrayerCenter.schedule(this) }
''')

p = J + 'ai/FaizaAgent.kt'
edit(p, '''        if ("voice_note" !in disabled) skills += com.faiza.ai.ai.voice.VoiceNotes.skill()''',
     '''        if ("voice_note" !in disabled) skills += com.faiza.ai.ai.voice.VoiceNotes.skill()
        if ("prayer" !in disabled) skills += com.faiza.ai.ai.prayer.PrayerCenter.skill()''')
edit(p, '''            - VOICE MESSAGES:''', '''            - PRAYER: নামাজের সময় / আসর কখন / সেহরি-ইফতার / কিবলা / হিজরি তারিখ → prayer (works offline). Never search the web for it.
            - VOICE MESSAGES:''')

edit(J + 'ui/DashboardActivity.kt', '''            Triple(R.drawable.ic_bolt, "Gaming mode")''', '''            Triple(R.drawable.ic_moon, "Prayer times") { startActivity(Intent(this, PrayerActivity::class.java)) },
            Triple(R.drawable.ic_bolt, "Gaming mode")''')
edit(J + 'ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowGaming).setOnClickListener {''', '''        findViewById<View>(R.id.rowPrayer).setOnClickListener {
            startActivity(Intent(this, PrayerActivity::class.java))
        }
        findViewById<View>(R.id.rowGaming).setOnClickListener {''')
import settings_rows as sr
sr.apply('rowGaming', [sr.row('prayer', 'ic_moon', 'green', 'Prayer times &amp; Qibla', 'Azan reminders, Sehri/Iftar, Hijri date, Qibla compass')])
print('ok')
