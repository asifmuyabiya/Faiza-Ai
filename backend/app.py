"""
MAYA AI Assistant - Backend Server
License Management + API for Android App
"""

from flask import Flask, request, jsonify, render_template_string
from flask_cors import CORS
from models import db, License, Customer, TrialUsage, Order, CrashReport, AppRelease, generate_license_key
from flask import send_from_directory
from functools import wraps
import hmac
from config import Config
from datetime import datetime, timezone
import bcrypt
import os

app = Flask(__name__)
app.config.from_object(Config)
app.config['MAX_CONTENT_LENGTH'] = 120 * 1024 * 1024  # APK uploads
APK_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'apk')
CORS(app)

db.init_app(app)

with app.app_context():
    db.create_all()


# ============================================================
# 🔑 LICENSE API - Used by Android App
# ============================================================

@app.route('/api/license/verify', methods=['POST'])
def verify_license():
    """Verify a license key from the Android app"""
    data = request.get_json()
    license_key = data.get('license_key', '').strip().upper()
    device_id = data.get('device_id', '').strip()

    if not license_key or not device_id:
        return jsonify({
            'valid': False,
            'error': 'License key and device ID are required'
        }), 400

    license = License.query.filter_by(license_key=license_key).first()

    if not license:
        return jsonify({
            'valid': False,
            'error': 'Invalid license key'
        }), 404

    # Check if license is bound to a different device
    if license.device_id and license.device_id != device_id:
        return jsonify({
            'valid': False,
            'error': 'This license is already activated on another device. Use dashboard to change device.',
            'bound_device': True
        }), 403

    # Activate if not yet activated
    if not license.activated_at:
        license.activate(device_id)

    if not license.is_valid():
        return jsonify({
            'valid': False,
            'error': f'License has {license.status}. Please renew.',
            'status': license.status
        }), 403

    # Update verification
    license.last_verified_at = datetime.now(timezone.utc)
    license.verification_count += 1
    license.device_id = device_id
    db.session.commit()

    # Get plan features
    plan_config = Config.PLANS.get(license.plan, {})

    return jsonify({
        'valid': True,
        'plan': license.plan,
        'plan_name': plan_config.get('name', 'Unknown'),
        'features': plan_config.get('features', []),
        'expires_at': license.expires_at.isoformat() if license.expires_at else None,
        'license_key': license.license_key
    })


@app.route('/api/trial/check', methods=['POST'])
def check_trial():
    """Check free trial status for a device"""
    data = request.get_json()
    device_id = data.get('device_id', '').strip()

    if not device_id:
        return jsonify({'error': 'Device ID required'}), 400

    trial = TrialUsage.query.filter_by(device_id=device_id).first()

    if not trial:
        # New device - create trial
        trial = TrialUsage(device_id=device_id)
        db.session.add(trial)
        db.session.commit()
        return jsonify({
            'has_trial': True,
            'minutes_remaining': Config.FREE_TRIAL_MINUTES,
            'minutes_used': 0,
            'message': f'Welcome! You have {Config.FREE_TRIAL_MINUTES} minutes free trial.'
        })

    if trial.has_trial_remaining():
        return jsonify({
            'has_trial': True,
            **trial.to_dict(),
            'message': f'{round(Config.FREE_TRIAL_MINUTES - trial.minutes_used, 1)} minutes remaining in trial.'
        })

    return jsonify({
        'has_trial': False,
        'trial_expired': True,
        'message': 'Free trial expired. Please purchase a license to continue.'
    })


@app.route('/api/trial/update', methods=['POST'])
def update_trial():
    """Update trial usage from Android app"""
    data = request.get_json()
    device_id = data.get('device_id', '').strip()
    minutes_to_add = data.get('minutes', 0)

    if not device_id:
        return jsonify({'error': 'Device ID required'}), 400

    trial = TrialUsage.query.filter_by(device_id=device_id).first()
    if not trial:
        return jsonify({'error': 'No trial found for this device'}), 404

    trial.minutes_used += minutes_to_add
    trial.last_used_at = datetime.now(timezone.utc)

    if trial.minutes_used >= Config.FREE_TRIAL_MINUTES:
        trial.trial_expired = True

    db.session.commit()
    return jsonify(trial.to_dict())


@app.route('/api/license/change-device', methods=['POST'])
def change_device():
    """Change device for a license key"""
    data = request.get_json()
    license_key = data.get('license_key', '').strip().upper()
    new_device_id = data.get('new_device_id', '').strip()
    email = data.get('email', '').strip()

    if not all([license_key, new_device_id, email]):
        return jsonify({'error': 'License key, email, and new device ID required'}), 400

    license = License.query.filter_by(license_key=license_key).first()
    if not license:
        return jsonify({'error': 'Invalid license key'}), 404

    # Verify ownership via email
    if license.customer and license.customer.email != email:
        return jsonify({'error': 'Email does not match license owner'}), 403

    license.change_device(new_device_id)
    db.session.commit()

    return jsonify({
        'success': True,
        'message': 'Device changed successfully',
        'license': license.to_dict()
    })


# ============================================================
# 🛒 ADMIN API - License Management
# ============================================================

def require_admin(f):
    """Every admin call must send the secret ADMIN_TOKEN (set it on the server) in the X-Admin-Token header."""
    @wraps(f)
    def wrapper(*args, **kwargs):
        token = os.getenv('ADMIN_TOKEN', '')
        if len(token) < 16:
            return jsonify({'error': 'Admin is disabled: set ADMIN_TOKEN (16+ characters) on the server.'}), 503
        sent = request.headers.get('X-Admin-Token', '')
        if not hmac.compare_digest(sent.encode(), token.encode()):
            return jsonify({'error': 'Not allowed'}), 401
        return f(*args, **kwargs)
    return wrapper


def _make_keys(plan, quantity=1, customer=None, payment_method=None, payment_amount=None, transaction_id=None):
    keys = []
    for _ in range(quantity):
        key = generate_license_key(prefix=Config.LICENSE_PREFIX, plan=plan)
        while License.query.filter_by(license_key=key).first():
            key = generate_license_key(prefix=Config.LICENSE_PREFIX, plan=plan)
        db.session.add(License(
            license_key=key, plan=plan, status='unused',
            customer_id=customer.id if customer else None,
            payment_method=payment_method, payment_amount=payment_amount, transaction_id=transaction_id
        ))
        keys.append(key)
    return keys

@app.route('/api/admin/generate-license', methods=['POST'])
@require_admin
def generate_license_api():
    """Generate a new license key (admin)"""
    data = request.get_json()
    plan = data.get('plan', 'basic').lower()
    customer_email = data.get('customer_email')
    customer_name = data.get('customer_name')
    payment_method = data.get('payment_method')
    payment_amount = data.get('payment_amount')
    transaction_id = data.get('transaction_id')
    quantity = min(data.get('quantity', 1), 50)  # Max 50 at a time

    if plan not in Config.PLANS:
        return jsonify({'error': f'Invalid plan. Choose from: {list(Config.PLANS.keys())}'}), 400

    # Find or create customer
    customer = None
    if customer_email:
        customer = Customer.query.filter_by(email=customer_email).first()
        if not customer:
            customer = Customer(
                name=customer_name or 'Unknown',
                email=customer_email
            )
            db.session.add(customer)
            db.session.flush()

    generated_keys = []
    for _ in range(quantity):
        key = generate_license_key(prefix=Config.LICENSE_PREFIX, plan=plan)

        # Ensure uniqueness
        while License.query.filter_by(license_key=key).first():
            key = generate_license_key(prefix=Config.LICENSE_PREFIX, plan=plan)

        license = License(
            license_key=key,
            plan=plan,
            status='unused',
            customer_id=customer.id if customer else None,
            payment_method=payment_method,
            payment_amount=payment_amount,
            transaction_id=transaction_id
        )
        db.session.add(license)
        generated_keys.append(key)

    db.session.commit()

    return jsonify({
        'success': True,
        'plan': plan,
        'quantity': quantity,
        'license_keys': generated_keys,
        'message': f'{quantity} {plan} license key(s) generated successfully!'
    })


@app.route('/api/admin/licenses', methods=['GET'])
@require_admin
def list_licenses():
    """List all licenses with filters"""
    status = request.args.get('status')
    plan = request.args.get('plan')
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)

    query = License.query

    if status:
        query = query.filter_by(status=status)
    if plan:
        query = query.filter_by(plan=plan)

    query = query.order_by(License.created_at.desc())
    pagination = query.paginate(page=page, per_page=per_page, error_out=False)

    return jsonify({
        'licenses': [lic.to_dict() for lic in pagination.items],
        'total': pagination.total,
        'pages': pagination.pages,
        'current_page': page
    })


@app.route('/api/admin/licenses/<license_id>/revoke', methods=['POST'])
@require_admin
def revoke_license(license_id):
    """Revoke a license"""
    license = License.query.get(license_id)
    if not license:
        return jsonify({'error': 'License not found'}), 404

    license.status = 'revoked'
    db.session.commit()

    return jsonify({
        'success': True,
        'message': f'License {license.license_key} has been revoked',
        'license': license.to_dict()
    })


@app.route('/api/admin/stats', methods=['GET'])
@require_admin
def admin_stats():
    """Get dashboard statistics"""
    total_licenses = License.query.count()
    active_licenses = License.query.filter_by(status='active').count()
    expired_licenses = License.query.filter_by(status='expired').count()
    unused_licenses = License.query.filter_by(status='unused').count()
    revoked_licenses = License.query.filter_by(status='revoked').count()

    total_customers = Customer.query.count()
    total_trials = TrialUsage.query.count()
    expired_trials = TrialUsage.query.filter_by(trial_expired=True).count()

    # Revenue calculation
    total_revenue = db.session.query(
        db.func.sum(License.payment_amount)
    ).filter(License.payment_amount.isnot(None)).scalar() or 0

    # Plan breakdown
    plan_stats = {}
    for plan_key in Config.PLANS:
        count = License.query.filter_by(plan=plan_key).count()
        plan_stats[plan_key] = count

    return jsonify({
        'licenses': {
            'total': total_licenses,
            'active': active_licenses,
            'expired': expired_licenses,
            'unused': unused_licenses,
            'revoked': revoked_licenses
        },
        'customers': {
            'total': total_customers
        },
        'trials': {
            'total': total_trials,
            'expired': expired_trials,
            'active': total_trials - expired_trials
        },
        'revenue': {
            'total': round(total_revenue, 2)
        },
        'plans': plan_stats
    })


@app.route('/api/admin/customers', methods=['GET'])
@require_admin
def list_customers():
    """List all customers"""
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)

    pagination = Customer.query.order_by(
        Customer.created_at.desc()
    ).paginate(page=page, per_page=per_page, error_out=False)

    return jsonify({
        'customers': [c.to_dict() for c in pagination.items],
        'total': pagination.total,
        'pages': pagination.pages,
        'current_page': page
    })


# ============================================================
# 📊 PLANS API - Public
# ============================================================

@app.route('/api/plans', methods=['GET'])
def get_plans():
    """Get available plans and pricing"""
    plans = []
    for key, plan in Config.PLANS.items():
        plans.append({
            'id': key,
            **plan
        })
    return jsonify({'plans': plans})


@app.route('/api/app/latest', methods=['GET'])
def latest_app():
    """Newest app version, for the in-app update check"""
    r = AppRelease.query.order_by(AppRelease.id.desc()).first()
    if r:
        return jsonify({'versionCode': r.version_code, 'versionName': r.version_name, 'url': r.url or '',
                        'notes': r.notes or '', 'minSupported': r.min_supported or 1})
    return jsonify({
        'versionCode': Config.LATEST_VERSION_CODE,
        'versionName': Config.LATEST_VERSION_NAME,
        'url': Config.LATEST_APK_URL,
        'notes': Config.LATEST_NOTES,
        'minSupported': Config.MIN_SUPPORTED_VERSION_CODE,
    })


# ============================================================
# 🏠 WEB ROUTES
# ============================================================

# ============================================================
# 🧾 ORDERS - the website's "Buy" form (payment checked by the owner)
# ============================================================

@app.route('/api/order', methods=['POST'])
def create_order():
    data = request.get_json() or {}
    name = (data.get('name') or '').strip()[:120]
    email = (data.get('email') or '').strip()[:120]
    txn = (data.get('transaction_id') or '').strip()[:80]
    plan = (data.get('plan') or 'basic').lower()
    if not name or '@' not in email or len(txn) < 6:
        return jsonify({'error': 'Name, a valid email and the payment Transaction ID are required.'}), 400
    if plan not in Config.PLANS:
        return jsonify({'error': 'Invalid plan'}), 400
    if Order.query.filter_by(transaction_id=txn).first():
        return jsonify({'error': 'This Transaction ID was already used for an order.'}), 409
    o = Order(name=name, email=email, phone=(data.get('phone') or '').strip()[:40], plan=plan,
              payment_method=(data.get('payment_method') or '')[:30],
              payment_amount=Config.PLANS[plan]['price_bdt'], transaction_id=txn)
    db.session.add(o)
    db.session.commit()
    return jsonify({'success': True, 'order_id': o.id,
                    'message': 'Order received. We will check your payment and send your license key soon. '
                               'Keep this order ID to check the status.'})


@app.route('/api/order/<order_id>', methods=['GET'])
def order_status(order_id):
    email = (request.args.get('email') or '').strip()
    o = Order.query.filter_by(id=order_id.strip().upper()).first()
    if not o or o.email.lower() != email.lower():
        return jsonify({'error': 'Order not found (check the order ID and email).'}), 404
    return jsonify(o.to_dict())


@app.route('/api/admin/orders', methods=['GET'])
@require_admin
def list_orders():
    status = request.args.get('status')
    q = Order.query
    if status:
        q = q.filter_by(status=status)
    return jsonify({'orders': [o.to_dict(admin=True) for o in q.order_by(Order.created_at.desc()).limit(300)]})


@app.route('/api/admin/orders/<order_id>/approve', methods=['POST'])
@require_admin
def approve_order(order_id):
    o = db.session.get(Order, order_id)
    if not o:
        return jsonify({'error': 'No such order'}), 404
    if o.status == 'approved':
        return jsonify({'success': True, 'license_key': o.license_key, 'order': o.to_dict(admin=True)})
    customer = Customer.query.filter_by(email=o.email).first()
    if not customer:
        customer = Customer(name=o.name, email=o.email)
        db.session.add(customer)
        db.session.flush()
    key = _make_keys(o.plan, 1, customer, o.payment_method, o.payment_amount, o.transaction_id)[0]
    o.status, o.license_key, o.decided_at = 'approved', key, datetime.now(timezone.utc)
    db.session.commit()
    return jsonify({'success': True, 'license_key': key, 'order': o.to_dict(admin=True)})


@app.route('/api/admin/orders/<order_id>/reject', methods=['POST'])
@require_admin
def reject_order(order_id):
    o = db.session.get(Order, order_id)
    if not o:
        return jsonify({'error': 'No such order'}), 404
    o.status, o.decided_at = 'rejected', datetime.now(timezone.utc)
    o.note = ((request.get_json(silent=True) or {}).get('note') or 'Payment not found')[:200]
    db.session.commit()
    return jsonify({'success': True, 'order': o.to_dict(admin=True)})


# ============================================================
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


# ============================================================
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
def admin_page():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'admin.html')
    with open(path, encoding='utf-8') as f:
        return f.read()


@app.route('/')
def home():
    """Redirect to website"""
    return jsonify({
        'name': 'Maya AI Assistant API',
        'version': '3.0',
        'status': 'running',
        'endpoints': {
            'verify_license': 'POST /api/license/verify',
            'check_trial': 'POST /api/trial/check',
            'get_plans': 'GET /api/plans',
            'latest_app': 'GET /api/app/latest',
            'admin_stats': 'GET /api/admin/stats',
        }
    })


# ============================================================
# 🚀 RUN
# ============================================================

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
        print("\n" + "="*50)
        print("🤖 Maya AI Assistant - Backend Server")
        print("="*50)
        print(f"📡 Server running at: http://localhost:5000")
        print(f"📊 Admin Stats: http://localhost:5000/api/admin/stats")
        print(f"🔑 Plans: http://localhost:5000/api/plans")
        print("="*50 + "\n")

    app.run(debug=os.getenv('FLASK_DEBUG') == '1', host='0.0.0.0', port=int(os.getenv('PORT', '5000')))
