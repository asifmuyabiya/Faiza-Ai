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


p = base + r'\ai\FaizaAgent.kt'
load(p)
rep('''        skills += com.faiza.ai.ai.files.FileManager.skills().filter { it.id !in disabled }''',
    '''        skills += com.faiza.ai.ai.files.FileManager.skills().filter { it.id !in disabled }
        skills += com.faiza.ai.ai.skills.Calculator.skills()''')
rep('''            - FILES & APPS:''', '''            - MATHS: ANY calculation → calculate (exact). NEVER open the Calculator app or type into it. Put the whole sum in
              one expression with digits ("300+300+30+80+10+18+1800"), converting number words first (তিনশো=300,
              আঠারোশো=1800, দেড় হাজার=1500, সাড়ে তিনশো=350, এক লাখ=100000). If the user dictates numbers over several
              sentences ("লিখে রাখো… আরও যোগ করো… মোট কত?"), use calc_tape add each time and calc_tape total at the end.
              Say the answer clearly with the digits grouped (3,598).
            - FILES & APPS:''')
save(p)
print("ok")
