import os
B = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\backend'

ORDER_MODEL = '''

class Order(db.Model):
    """A purchase from the website. The owner checks the bKash/Nagad payment, then approves it (which creates the key)."""
    __tablename__ = 'orders'

    id = db.Column(db.String(12), primary_key=True, default=lambda: secrets.token_hex(4).upper())
    name = db.Column(db.String(120), nullable=False)
    email = db.Column(db.String(120), nullable=False)
    phone = db.Column(db.String(40))
    plan = db.Column(db.String(20), nullable=False)
    payment_method = db.Column(db.String(30))
    payment_amount = db.Column(db.Float)
    transaction_id = db.Column(db.String(80), nullable=False)
    status = db.Column(db.String(20), default='pending')  # pending | approved | rejected
    license_key = db.Column(db.String(64))
    note = db.Column(db.String(200))
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
    decided_at = db.Column(db.DateTime)

    def to_dict(self, admin=False):
        d = {
            'id': self.id, 'plan': self.plan, 'status': self.status,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'license_key': self.license_key if self.status == 'approved' else None,
            'note': self.note,
        }
        if admin:
            d.update({'name': self.name, 'email': self.email, 'phone': self.phone,
                      'payment_method': self.payment_method, 'payment_amount': self.payment_amount,
                      'transaction_id': self.transaction_id,
                      'decided_at': self.decided_at.isoformat() if self.decided_at else None})
        return d
'''

p = os.path.join(B, 'models.py')
s = open(p, encoding='utf-8').read()
if 'class Order' not in s:
    open(p, 'w', encoding='utf-8').write(s.rstrip() + '\n' + ORDER_MODEL)

p = os.path.join(B, 'app.py')
s = open(p, encoding='utf-8').read()


def rep(a, b):
    global s
    assert s.count(a) == 1, (a[:60], s.count(a))
    s = s.replace(a, b)


rep('''from models import db, License, Customer, TrialUsage, generate_license_key''',
    '''from models import db, License, Customer, TrialUsage, Order, generate_license_key
from functools import wraps
import hmac''')
rep('''# 🛒 ADMIN API - License Management
# ============================================================
''', '''# 🛒 ADMIN API - License Management
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
''')
for route in ["@app.route('/api/admin/generate-license', methods=['POST'])",
              "@app.route('/api/admin/licenses', methods=['GET'])",
              "@app.route('/api/admin/licenses/<license_id>/revoke', methods=['POST'])",
              "@app.route('/api/admin/stats', methods=['GET'])",
              "@app.route('/api/admin/customers', methods=['GET'])"]:
    rep(route + "\n", route + "\n@require_admin\n")
rep('''    app.run(debug=True, host='0.0.0.0', port=5000)''',
    '''    app.run(debug=os.getenv('FLASK_DEBUG') == '1', host='0.0.0.0', port=int(os.getenv('PORT', '5000')))''')

rep('''@app.route('/')
def home():''', '''# ============================================================
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


@app.route('/admin')
def admin_page():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'admin.html')
    with open(path, encoding='utf-8') as f:
        return f.read()


@app.route('/')
def home():''')
open(p, 'w', encoding='utf-8').write(s)

r = os.path.join(B, 'requirements.txt')
req = open(r, encoding='utf-8').read()
if 'gunicorn' not in req:
    open(r, 'w', encoding='utf-8').write(req.rstrip() + '\ngunicorn==23.0.0\n')
open(os.path.join(B, 'Procfile'), 'w').write('web: gunicorn app:app --bind 0.0.0.0:$PORT\n')
print('ok')
