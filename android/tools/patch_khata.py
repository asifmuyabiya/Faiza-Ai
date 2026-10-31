import os, sys
android = os.getcwd()
main = os.path.join(android, 'app', 'src', 'main')
sys.path.insert(0, os.path.join(android, 'tools'))
def edit(p, a, b):
    p = os.path.join(main, *p.split('/')); s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:60], s.count(a)); open(p, 'w', encoding='utf-8').write(s.replace(a, b))
J = 'java/com/faiza/ai/'
edit('AndroidManifest.xml', '        <activity android:name=".ui.PrayerActivity"', '''        <activity android:name=".ui.KhataActivity"
            android:launchMode="singleTop"
            android:theme="@style/Theme.FaizaAI"
            android:windowSoftInputMode="adjustResize" />
        <activity android:name=".ui.PrayerActivity"''')
p = J + 'ai/FaizaAgent.kt'
edit(p, '''        if ("prayer" !in disabled) skills += com.faiza.ai.ai.prayer.PrayerCenter.skill()''', '''        if ("prayer" !in disabled) skills += com.faiza.ai.ai.prayer.PrayerCenter.skill()
        if ("khata" !in disabled) skills += com.faiza.ai.ai.khata.Khata.skill()''')
edit(p, '''            - PRAYER:''', '''            - MONEY BOOK: খরচ / আয় / বাকি / ধার / "কত খরচ হলো?" / "করিম কত দেবে?" → khata. Never use calculate for these.
            - PRAYER:''')
edit(J + 'ui/DashboardActivity.kt', '''            Triple(R.drawable.ic_moon, "Prayer times")''', '''            Triple(R.drawable.ic_book, "Money book") { startActivity(Intent(this, KhataActivity::class.java)) },
            Triple(R.drawable.ic_moon, "Prayer times")''')
edit(J + 'ui/SettingsActivity.kt', '''        findViewById<View>(R.id.rowPrayer).setOnClickListener {''', '''        findViewById<View>(R.id.rowKhata).setOnClickListener {
            startActivity(Intent(this, KhataActivity::class.java))
        }
        findViewById<View>(R.id.rowPrayer).setOnClickListener {''')
import settings_rows as sr
sr.apply('rowPrayer', [sr.row('khata', 'ic_book', 'amber', 'Money book (খাতা)', 'Expenses, income, who owes whom — by voice')])
print('ok')
