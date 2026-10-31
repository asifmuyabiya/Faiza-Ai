"""Backend checks: python test_backend.py (uses a temporary database)."""
import os
import sys
import tempfile

db_file = os.path.join(tempfile.mkdtemp(), 'test.db')
os.environ['DATABASE_URL'] = 'sqlite:///' + db_file.replace('\\', '/')
os.environ['ADMIN_TOKEN'] = 'test-admin-token-1234567890'
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app import app  # noqa: E402
import app as app_module  # noqa: E402
app_module.APK_DIR = os.path.join(tempfile.mkdtemp(), 'apk')  # test uploads never land in the project

c = app.test_client()
ADMIN = {'X-Admin-Token': os.environ['ADMIN_TOKEN']}
fails = 0


def check(name, cond):
    global fails
    print(('PASS ' if cond else 'FAIL ') + name)
    if not cond:
        fails += 1


# Admin is locked without the token.
check('admin generate needs token', c.post('/api/admin/generate-license', json={'plan': 'pro'}).status_code == 401)
check('admin stats needs token', c.get('/api/admin/stats').status_code == 401)
check('admin wrong token', c.get('/api/admin/stats', headers={'X-Admin-Token': 'nope'}).status_code == 401)
check('admin with token', c.get('/api/admin/stats', headers=ADMIN).status_code == 200)

# A buyer orders: no key until approved.
r = c.post('/api/order', json={'name': 'Rahim', 'email': 'rahim@example.com', 'phone': '01712345678',
                               'plan': 'pro', 'payment_method': 'bKash', 'transaction_id': 'BJ71ABC123'})
check('order created', r.status_code == 200 and r.get_json()['success'])
oid = r.get_json()['order_id']
st = c.get(f'/api/order/{oid}?email=rahim@example.com').get_json()
check('pending has no key', st['status'] == 'pending' and st['license_key'] is None)
check('wrong email hides order', c.get(f'/api/order/{oid}?email=x@y.com').status_code == 404)
check('same TrxID twice refused', c.post('/api/order', json={'name': 'X', 'email': 'x@y.com', 'plan': 'pro',
                                                            'transaction_id': 'BJ71ABC123'}).status_code == 409)
check('missing TrxID refused', c.post('/api/order', json={'name': 'X', 'email': 'x@y.com', 'plan': 'pro'}).status_code == 400)
check('order list needs token', c.get('/api/admin/orders').status_code == 401)
check('approve needs token', c.post(f'/api/admin/orders/{oid}/approve').status_code == 401)

# Owner approves → key works in the app.
a = c.post(f'/api/admin/orders/{oid}/approve', headers=ADMIN).get_json()
key = a.get('license_key')
check('approve gives key', bool(key) and key.startswith('FAIZA-PRO-'))
st = c.get(f'/api/order/{oid}?email=rahim@example.com').get_json()
check('buyer sees key', st['status'] == 'approved' and st['license_key'] == key)
v = c.post('/api/license/verify', json={'license_key': key, 'device_id': 'phone-1'}).get_json()
check('key verifies in app', v.get('valid') is True and v.get('plan') == 'pro')
v2 = c.post('/api/license/verify', json={'license_key': key, 'device_id': 'phone-2'})
check('key locked to first phone', v2.status_code == 403)
again = c.post(f'/api/admin/orders/{oid}/approve', headers=ADMIN).get_json()
check('approving twice keeps the same key', again.get('license_key') == key)

# Reject flow.
r2 = c.post('/api/order', json={'name': 'Fake', 'email': 'f@k.e', 'plan': 'basic', 'transaction_id': 'FAKE000001'}).get_json()
c.post(f"/api/admin/orders/{r2['order_id']}/reject", headers=ADMIN, json={'note': 'No such payment'})
st2 = c.get(f"/api/order/{r2['order_id']}?email=f@k.e").get_json()
check('rejected shows note, no key', st2['status'] == 'rejected' and st2['license_key'] is None and 'No such' in st2['note'])
check('admin page served', c.get('/admin').status_code == 200)

# Crash reports.
rep = "Kind: crash\nTime: x\nApp: 3.0.0\nThread: main\n\njava.lang.IllegalStateException: boom\n\tat com.faiza.X"
check('crash accepted', c.post('/api/crash', json={'device': 'abc', 'version': '3.0.0', 'model': 'Honor', 'android': 34, 'report': rep}).status_code == 200)
c.post('/api/crash', json={'device': 'def', 'version': '3.0.0', 'model': 'Honor', 'android': 34, 'report': rep})
check('empty crash refused', c.post('/api/crash', json={}).status_code == 400)
check('crash list needs token', c.get('/api/admin/crashes').status_code == 401)
g = c.get('/api/admin/crashes', headers=ADMIN).get_json()
check('crashes grouped', g['groups'][0]['count'] == 2 and g['groups'][0]['devices'] == 2 and 'IllegalStateException' in g['groups'][0]['first_line'])
check('crash detail', 'boom' in c.get('/api/admin/crashes/' + str(g['groups'][0]['latest']['id']), headers=ADMIN).get_json()['report'])

# Publishing an update.
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

print('\n' + ('ALL PASSED' if fails == 0 else f'{fails} FAILED'))
sys.exit(1 if fails else 0)
