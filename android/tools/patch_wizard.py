import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


J = 'java/com/faiza/ai/'
edit(J + 'ui/SetupWizardActivity.kt', '''                doIt.text = "ফাইজাকে চালু করুন"''', '''                doIt.text = "শুরু করি"''')

edit(J + 'util/PrefsManager.kt', '''    /** Screen off: listen for "Hey Faiza" only while charging''', '''    /** The first-start setup wizard was finished or skipped. */
    var setupWizardDone: Boolean
        get() = prefs.getBoolean("setup_wizard_done", false)
        set(value) = prefs.edit().putBoolean("setup_wizard_done", value).apply()
    /** Permissions already asked once (a second "no" means only app settings can change it). */
    var askedPermissions: Set<String>
        get() = prefs.getStringSet("asked_permissions", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("asked_permissions", value).apply()

    /** Screen off: listen for "Hey Faiza" only while charging''')

edit('AndroidManifest.xml', '''        <activity android:name=".ui.HelpActivity"''', '''        <activity android:name=".ui.SetupWizardActivity"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.HelpActivity"''')

edit(J + 'ui/DashboardActivity.kt', '''        setupUI()
        updateStatus()
        showLastCrashIfAny()''', '''        setupUI()
        updateStatus()
        showLastCrashIfAny()
        // First start: walk the new owner through the permissions, one screen each.
        if (!FaizaApp.instance.prefs.setupWizardDone) startActivity(Intent(this, SetupWizardActivity::class.java))''')

# The guide uses the same checklist.
p = J + 'ui/HelpActivity.kt'
s = open(os.path.join(main, *p.split('/')), encoding='utf-8').read()
a = s.index('    private data class Check(')
b = s.index('    private val guide = listOf(')
s = s[:a] + s[b:]
s = s.replace('''        val list = checks()
        val done = list.count { it.ok }''', '''        val list = SetupChecks.all
        val done = list.count { it.ok(this) }''')
s = s.replace('''        list.forEach { c ->
            col.addView(TextView(this).apply {
                text = (if (c.ok) "✅  " else "❌  ") + c.title + "\\n" + c.why
                textSize = 15f; setTextColor(getColor(if (c.ok) R.color.text_primary else R.color.warning)); setLineSpacing(0f, 1.25f)
                setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundResource(R.drawable.bg_input)
                if (!c.ok) setOnClickListener { c.fix() }
            }, lp(top = 6))
        }''', '''        list.forEach { c ->
            val ok = c.ok(this)
            col.addView(TextView(this).apply {
                text = (if (ok) "✅  " else "❌  ") + c.title + "\\n" + c.why + if (!ok) "\\n👉 " + c.how else ""
                textSize = 15f; setTextColor(getColor(if (ok) R.color.text_primary else R.color.warning)); setLineSpacing(0f, 1.25f)
                setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundResource(R.drawable.bg_input)
                if (!ok) setOnClickListener { c.fix(this@HelpActivity) }
            }, lp(top = 6))
        }''')
assert 'SetupChecks.all' in s and 'c.fix(this@HelpActivity)' in s
open(os.path.join(main, *p.split('/')), 'w', encoding='utf-8').write(s)
print('ok')
