import os
ROOT = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant'
B = os.path.join(ROOT, 'backend')


def edit_file(p, a, b):
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


# ---------- models ----------
p = os.path.join(B, 'models.py')
s = open(p, encoding='utf-8').read()
if 'class AppRelease' not in s:
    s = s.rstrip() + '''


class AppRelease(db.Model):
    """The newest app version the owner published from the admin page (overrides the .env values)."""
    __tablename__ = 'app_releases'

    id = db.Column(db.Integer, primary_key=True)
    version_code = db.Column(db.Integer, nullable=False)
    version_name = db.Column(db.String(30), nullable=False)
    url = db.Column(db.String(400))
    notes = db.Column(db.String(1000))
    min_supported = db.Column(db.Integer, default=1)
    apk_file = db.Column(db.String(200))
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
'''
    open(p, 'w', encoding='utf-8').write(s + '\n')

# ---------- app ----------
p = os.path.join(B, 'app.py')
edit_file(p, 'from models import db, License, Customer, TrialUsage, Order, CrashReport, generate_license_key',
          'from models import db, License, Customer, TrialUsage, Order, CrashReport, AppRelease, generate_license_key\n'
          'from flask import send_from_directory')
edit_file(p, '''app.config.from_object(Config)''', '''app.config.from_object(Config)
app.config['MAX_CONTENT_LENGTH'] = 120 * 1024 * 1024  # APK uploads
APK_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'apk')''')
edit_file(p, '''    """Newest app version, for the in-app update check"""
    return jsonify({''', '''    """Newest app version, for the in-app update check"""
    r = AppRelease.query.order_by(AppRelease.id.desc()).first()
    if r:
        return jsonify({'versionCode': r.version_code, 'versionName': r.version_name, 'url': r.url or '',
                        'notes': r.notes or '', 'minSupported': r.min_supported or 1})
    return jsonify({''')
edit_file(p, '''@app.route('/admin')
def admin_page():''', '''# ============================================================
# 📦 PUBLISH AN UPDATE (admin) + APK download
# ============================================================

@app.route('/api/admin/release', methods=['POST'])
@require_admin
def publish_release():
    f = request.files.get('apk')
    form = request.form if request.form else (request.get_json(silent=True) or {})
    try:
        code = int(form.get('version_code', 0))
    except (TypeError, ValueError):
        code = 0
    name = (form.get('version_name') or '').strip()[:30]
    if code < 1 or not name:
        return jsonify({'error': 'version_code (a whole number, higher than before) and version_name are required'}), 400
    last = AppRelease.query.order_by(AppRelease.id.desc()).first()
    if last and code <= last.version_code:
        return jsonify({'error': f'version_code must be higher than {last.version_code}'}), 400
    url = (form.get('url') or '').strip()[:400]
    apk_name = None
    if f and f.filename:
        if not f.filename.lower().endswith('.apk'):
            return jsonify({'error': 'Only .apk files'}), 400
        os.makedirs(APK_DIR, exist_ok=True)
        apk_name = f'faiza-{code}.apk'
        f.save(os.path.join(APK_DIR, apk_name))
        url = request.host_url.rstrip('/') + '/download/' + apk_name
    if not url.startswith('http'):
        return jsonify({'error': 'Upload the APK file or give a download link'}), 400
    try:
        min_sup = int(form.get('min_supported', 1) or 1)
    except (TypeError, ValueError):
        min_sup = 1
    r = AppRelease(version_code=code, version_name=name, url=url, notes=(form.get('notes') or '')[:1000],
                   min_supported=min_sup, apk_file=apk_name)
    db.session.add(r)
    db.session.commit()
    return jsonify({'success': True, 'url': url, 'versionCode': code})


@app.route('/download/<path:name>')
def download_apk(name):
    if not name.endswith('.apk') or '/' in name or '..' in name:
        return jsonify({'error': 'not found'}), 404
    return send_from_directory(APK_DIR, name, as_attachment=True, mimetype='application/vnd.android.package-archive')


@app.route('/admin')
def admin_page():''')

# ---------- admin page ----------
p = os.path.join(B, 'admin.html')
edit_file(p, '''    <div class="card">
      <b>🔑 নিজে কী বানান</b>''', '''    <div class="card">
      <b>📦 নতুন আপডেট দিন</b>
      <div class="muted">Claude নতুন APK বানালে এখানে আপলোড করুন — ক্রেতাদের অ্যাপে "আপডেট আছে" দেখাবে। Version code আগের চেয়ে বড় হতে হবে।</div>
      <div class="row" style="margin-top:8px">
        <input id="rcode" type="number" placeholder="Version code (যেমন 2)" style="width:170px">
        <input id="rname" placeholder="Version name (যেমন 3.1.0)" style="width:170px">
        <input id="rmin" type="number" placeholder="Min supported (ঐচ্ছিক)" style="width:170px">
      </div>
      <textarea id="rnotes" placeholder="কী নতুন (ক্রেতারা দেখবে)" style="width:100%;margin-top:8px;min-height:60px;border-radius:10px;background:#0d1020;color:var(--text);border:1px solid var(--line);padding:9px"></textarea>
      <div class="row" style="margin-top:8px"><input id="rapk" type="file" accept=".apk"><button onclick="publish()">Publish</button></div>
      <div id="rout" class="muted" style="margin-top:6px"></div>
    </div>
    <div class="card">
      <b>🔑 নিজে কী বানান</b>''')
edit_file(p, '''async function manual() {''', '''async function publish() {
  const fd = new FormData();
  fd.append('version_code', document.getElementById('rcode').value);
  fd.append('version_name', document.getElementById('rname').value);
  fd.append('min_supported', document.getElementById('rmin').value || '1');
  fd.append('notes', document.getElementById('rnotes').value);
  const file = document.getElementById('rapk').files[0];
  if (file) fd.append('apk', file);
  const out = document.getElementById('rout');
  out.textContent = 'Uploading…';
  try {
    const r = await fetch('/api/admin/release', { method: 'POST', body: fd, headers: { 'X-Admin-Token': sessionStorage.getItem('t') || '' } });
    const j = await r.json();
    out.textContent = r.ok ? '✅ Published: ' + j.url : '❌ ' + j.error;
  } catch (e) { out.textContent = '❌ ' + e.message; }
}

async function manual() {''')

# ---------- tests ----------
p = os.path.join(B, 'test_backend.py')
edit_file(p, "print('\\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))", '''# Publishing an update.
import io
check('release needs token', c.post('/api/admin/release', data={'version_code': '2', 'version_name': '3.1.0'}).status_code == 401)
r = c.post('/api/admin/release', headers=ADMIN, content_type='multipart/form-data',
           data={'version_code': '2', 'version_name': '3.1.0', 'notes': 'New stuff',
                 'apk': (io.BytesIO(b'PK fake apk'), 'faiza.apk')})
check('release published', r.status_code == 200 and r.get_json()['url'].endswith('/download/faiza-2.apk'))
latest = c.get('/api/app/latest').get_json()
check('app sees new version', latest['versionCode'] == 2 and latest['versionName'] == '3.1.0' and latest['notes'] == 'New stuff')
check('apk downloads', c.get('/download/faiza-2.apk').data == b'PK fake apk')
check('lower version refused', c.post('/api/admin/release', headers=ADMIN, data={'version_code': '2', 'version_name': 'x', 'url': 'https://a/b.apk'}).status_code == 400)
check('path tricks refused', c.get('/download/..%2Fapp.py').status_code == 404)

print('\\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))''')

# ---------- Android version from gradle.properties ----------
A = os.path.join(ROOT, 'android')
edit_file(os.path.join(A, 'app', 'build.gradle'), '''        versionCode 1
        versionName "3.0.0"''', '''        // Raise FAIZA_VERSION_CODE (android/gradle.properties) by 1 for every update you publish.
        versionCode((project.findProperty('FAIZA_VERSION_CODE') ?: '1').toString().toInteger())
        versionName((project.findProperty('FAIZA_VERSION_NAME') ?: '3.0.0').toString())''')
edit_file(os.path.join(A, 'gradle.properties'), '''FAIZA_SERVER_URL=https://CHANGE-ME.example.com''', '''FAIZA_SERVER_URL=https://CHANGE-ME.example.com
# App version: raise the code by 1 for each update (then publish the APK from the admin page).
FAIZA_VERSION_CODE=1
FAIZA_VERSION_NAME=3.0.0''')
print('ok')
