import os, sys
android = os.getcwd(); main = os.path.join(android, 'app', 'src', 'main')
sys.path.insert(0, os.path.join(android, 'tools'))
def edit(p, a, b):
    p = os.path.join(main, *p.split('/')); s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:60], s.count(a)); open(p, 'w', encoding='utf-8').write(s.replace(a, b))
J = 'java/com/faiza/ai/'
edit('AndroidManifest.xml', '        <activity android:name=".ui.DrivingActivity"', '''        <activity android:name=".ui.HelpActivity"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.DrivingActivity"''')
edit(J + 'ui/DashboardActivity.kt', '''            Triple(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },''', '''            Triple(R.drawable.ic_info, "How to use") { startActivity(Intent(this, HelpActivity::class.java)) },
            Triple(R.drawable.ic_settings, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) },''')
edit(J + 'ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowDriving).setOnClickListener {''', '''        findViewById<View>(R.id.rowHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        findViewById<View>(R.id.rowDriving).setOnClickListener {''')
import settings_rows as sr
sr.apply('rowDriving', [sr.row('help', 'ic_info', 'slate', 'How to use (বাংলা গাইড)', 'Setup checklist and what to say for every feature')])
print('ok')
