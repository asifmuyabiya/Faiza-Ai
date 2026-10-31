import os
B = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\backend'

p = os.path.join(B, 'models.py')
s = open(p, encoding='utf-8').read()
if 'class CrashReport' not in s:
    s = s.rstrip() + '''


class CrashReport(db.Model):
    """A crash sent by a customer's app (no personal data: device ID is hashed on the phone)."""
    __tablename__ = 'crash_reports'

    id = db.Column(db.Integer, primary_key=True)
    device = db.Column(db.String(32))
    version = db.Column(db.String(20))
    model = db.Column(db.String(80))
    android = db.Column(db.Integer)
    kind = db.Column(db.String(40))
    first_line = db.Column(db.String(300))
    report = db.Column(db.Text)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self, full=False):
        d = {'id': self.id, 'device': self.device, 'version': self.version, 'model': self.model,
             'android': self.android, 'kind': self.kind, 'first_line': self.first_line,
             'created_at': self.created_at.isoformat() if self.created_at else None}
        if full:
            d['report'] = self.report
        return d
'''
    open(p, 'w', encoding='utf-8').write(s + '\n')

p = os.path.join(B, 'app.py')
s = open(p, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:60], s.count(a))
    s = s.replace(a, b)


rep('from models import db, License, Customer, TrialUsage, Order, generate_license_key',
    'from models import db, License, Customer, TrialUsage, Order, CrashReport, generate_license_key')
rep('''@app.route('/admin')
def admin_page():''', '''# ============================================================
# 🐞 CRASH REPORTS from customers' apps
# ============================================================

@app.route('/api/crash', methods=['POST'])
def receive_crash():
    data = request.get_json(silent=True) or {}
    report = str(data.get('report') or '')[:15000]
    if not report:
        return jsonify({'error': 'empty'}), 400
    lines = report.splitlines()
    kind = next((l[5:].strip() for l in lines if l.startswith('Kind:')), 'crash')[:40]
    # The first line of the stack trace ("java.lang.X: message") identifies the bug.
    first = next((l.strip() for l in lines if ('Exception' in l or 'Error' in l) and not l.startswith(('Kind', 'Time', 'App', 'Thread'))), lines[0] if lines else '')[:300]
    db.session.add(CrashReport(device=str(data.get('device') or '')[:32], version=str(data.get('version') or '')[:20],
                               model=str(data.get('model') or '')[:80], android=int(data.get('android') or 0),
                               kind=kind, first_line=first, report=report))
    # Keep the table small.
    count = CrashReport.query.count()
    if count > 2000:
        for old in CrashReport.query.order_by(CrashReport.id.asc()).limit(count - 2000):
            db.session.delete(old)
    db.session.commit()
    return jsonify({'success': True})


@app.route('/api/admin/crashes', methods=['GET'])
@require_admin
def list_crashes():
    rows = CrashReport.query.order_by(CrashReport.id.desc()).limit(300).all()
    groups = {}
    for r in rows:
        g = groups.setdefault(r.first_line, {'first_line': r.first_line, 'count': 0, 'devices': set(), 'latest': r.to_dict(), 'versions': set()})
        g['count'] += 1
        g['devices'].add(r.device)
        g['versions'].add(r.version)
    out = [{'first_line': g['first_line'], 'count': g['count'], 'devices': len(g['devices']),
            'versions': sorted(v for v in g['versions'] if v), 'latest': g['latest']} for g in groups.values()]
    out.sort(key=lambda g: -g['count'])
    return jsonify({'groups': out, 'total': len(rows)})


@app.route('/api/admin/crashes/<int:crash_id>', methods=['GET'])
@require_admin
def crash_detail(crash_id):
    r = db.session.get(CrashReport, crash_id)
    if not r:
        return jsonify({'error': 'not found'}), 404
    return jsonify(r.to_dict(full=True))


@app.route('/admin')
def admin_page():''')
open(p, 'w', encoding='utf-8').write(s)

# Admin page: crashes section.
p = os.path.join(B, 'admin.html')
s = open(p, encoding='utf-8').read()
rep_html = [
    ('''    <div class="card">
      <b>🔑 নিজে কী বানান</b>''', '''    <div class="card">
      <div class="row" style="justify-content:space-between"><b>🐞 ক্রেতাদের অ্যাপে ক্র্যাশ</b><button class="ghost" onclick="crashes()">↻</button></div>
      <div class="muted">একই সমস্যা কতবার, কতগুলো ফোনে হয়েছে। "দেখুন" চেপে পুরো রিপোর্ট কপি করে Claude-কে দিন, ঠিক করে দেবে।</div>
      <div id="crashes"></div>
    </div>
    <div class="card">
      <b>🔑 নিজে কী বানান</b>'''),
    ('''function copy(t) {''', '''async function crashes() {
  const r = await api('/api/admin/crashes');
  const box = document.getElementById('crashes');
  if (!r.groups.length) { box.innerHTML = '<p class="muted">কোনো ক্র্যাশ নেই 🎉</p>'; return; }
  box.innerHTML = r.groups.map(g => `<div class="order">
      <div><b>${esc(g.count)}×</b> · ${esc(g.devices)} ফোন · v${esc(g.versions.join(', '))}</div>
      <div class="key" style="white-space:normal">${esc(g.first_line)}</div>
      <div class="muted">${esc(g.latest.model)} · Android API ${esc(g.latest.android)} · ${new Date(g.latest.created_at).toLocaleString()}
        <button class="ghost" onclick="crashDetail(${g.latest.id})">দেখুন</button></div></div>`).join('');
}

async function crashDetail(id) {
  const r = await api('/api/admin/crashes/' + id);
  copy(r.report);
}

function copy(t) {'''),
    ('''  try { await stats(); document.getElementById('login').hidden = true; document.getElementById('app').hidden = false; load(); }''',
     '''  try { await stats(); document.getElementById('login').hidden = true; document.getElementById('app').hidden = false; load(); crashes(); }'''),
]
for a, b in rep_html:
    assert s.count(a) == 1, (a[:50], s.count(a))
    s = s.replace(a, b)
open(p, 'w', encoding='utf-8').write(s)

# Tests.
p = os.path.join(B, 'test_backend.py')
s = open(p, encoding='utf-8').read()
a = "print('\\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))"
assert s.count(a) == 1
s = s.replace(a, '''# Crash reports.
rep = "Kind: crash\\nTime: x\\nApp: 3.0.0\\nThread: main\\n\\njava.lang.IllegalStateException: boom\\n\\tat com.faiza.X"
check('crash accepted', c.post('/api/crash', json={'device': 'abc', 'version': '3.0.0', 'model': 'Honor', 'android': 34, 'report': rep}).status_code == 200)
c.post('/api/crash', json={'device': 'def', 'version': '3.0.0', 'model': 'Honor', 'android': 34, 'report': rep})
check('empty crash refused', c.post('/api/crash', json={}).status_code == 400)
check('crash list needs token', c.get('/api/admin/crashes').status_code == 401)
g = c.get('/api/admin/crashes', headers=ADMIN).get_json()
check('crashes grouped', g['groups'][0]['count'] == 2 and g['groups'][0]['devices'] == 2 and 'IllegalStateException' in g['groups'][0]['first_line'])
check('crash detail', 'boom' in c.get('/api/admin/crashes/' + str(g['groups'][0]['latest']['id']), headers=ADMIN).get_json()['report'])

''' + a)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
