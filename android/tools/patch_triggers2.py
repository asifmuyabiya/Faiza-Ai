import os, sys
sys.path.insert(0, os.path.join(os.getcwd(), 'tools'))
import settings_rows as sr
sr.apply('rowCalls', [sr.row('headsetTrigger', 'ic_mic', 'blue', 'Earphone button', 'Press the earphone button twice quickly to talk to Faiza', 'switch')])
p = os.path.join('app','src','main','java','com','faiza','ai','ui','SettingsActivity.kt')
s = open(p, encoding='utf-8').read()
a = '''        findViewById<View>(R.id.rowCalls).setOnClickListener { swCalls.toggle() }
'''
assert s.count(a) == 1
s = s.replace(a, a + '''
        val swHeadset = findViewById<SwitchMaterial>(R.id.swHeadsetTrigger)
        swHeadset.isChecked = prefs.isHeadsetTrigger
        swHeadset.setOnCheckedChangeListener { _, isChecked -> prefs.isHeadsetTrigger = isChecked }
        findViewById<View>(R.id.rowHeadsetTrigger).setOnClickListener { swHeadset.toggle() }
''')
open(p, 'w', encoding='utf-8').write(s)
print('ok')
