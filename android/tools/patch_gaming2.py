import sys, os
sys.path.insert(0, r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\tools')
base = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main'
def edit(p, a, b):
    p = os.path.join(base, *p.split('|'))
    s = open(p, encoding='utf-8').read(); assert s.count(a) == 1, (p, a[:60], s.count(a)); open(p, 'w', encoding='utf-8').write(s.replace(a, b))
edit('AndroidManifest.xml', '        <activity android:name=".ui.CodeStudioActivity"', '''        <activity android:name=".ui.GamingActivity"
            android:launchMode="singleTop"
            android:theme="@style/Theme.FaizaAI" />
        <activity android:name=".ui.CodeStudioActivity"''')
edit('java|com|faiza|ai|ui|DashboardActivity.kt', '            Triple(R.drawable.ic_chip, "Code Studio")', '''            Triple(R.drawable.ic_bolt, "Gaming mode") { startActivity(Intent(this, GamingActivity::class.java)) },
            Triple(R.drawable.ic_chip, "Code Studio")''')
edit('java|com|faiza|ai|ui|SettingsActivity.kt', '        findViewById<View>(R.id.rowCodeStudio).setOnClickListener {', '''        findViewById<View>(R.id.rowGaming).setOnClickListener {
            startActivity(Intent(this, GamingActivity::class.java))
        }
        findViewById<View>(R.id.rowCodeStudio).setOnClickListener {''')
import settings_rows as sr
sr.apply('rowCodeStudio', [sr.row('gaming', 'ic_bolt', 'pink', 'Gaming mode', 'Live coach &amp; funny commentary while you play')])
print('ok')
