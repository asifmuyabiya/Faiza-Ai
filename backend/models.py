from flask_sqlalchemy import SQLAlchemy
from datetime import datetime, timedelta, timezone
import uuid
import secrets
import string

db = SQLAlchemy()


def generate_license_key(prefix="FAIZA", plan="PRO"):
    """Generate a unique license key like MAYA-PRO-A3X7-K9M2-QW5E"""
    chars = string.ascii_uppercase + string.digits
    segments = []
    for _ in range(3):
        segment = ''.join(secrets.choice(chars) for _ in range(4))
        segments.append(segment)

    plan_code = plan.upper()[:3]
    return f"{prefix}-{plan_code}-{'-'.join(segments)}"


class User(db.Model):
    """Admin users who manage licenses"""
    __tablename__ = 'users'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email = db.Column(db.String(120), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    is_admin = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        return {
            'id': self.id,
            'email': self.email,
            'is_admin': self.is_admin,
            'created_at': self.created_at.isoformat()
        }


class Customer(db.Model):
    """Customers who buy licenses"""
    __tablename__ = 'customers'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    name = db.Column(db.String(100), nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    phone = db.Column(db.String(20), nullable=True)
    device_id = db.Column(db.String(255), nullable=True)  # Android device ID
    device_name = db.Column(db.String(100), nullable=True)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))

    licenses = db.relationship('License', backref='customer', lazy=True)

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'email': self.email,
            'phone': self.phone,
            'device_id': self.device_id,
            'device_name': self.device_name,
            'created_at': self.created_at.isoformat(),
            'licenses': [lic.to_dict() for lic in self.licenses]
        }


class License(db.Model):
    """License keys for the app"""
    __tablename__ = 'licenses'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    license_key = db.Column(db.String(50), unique=True, nullable=False)
    plan = db.Column(db.String(20), nullable=False, default='basic')  # basic, pro, lifetime
    status = db.Column(db.String(20), nullable=False, default='active')  # active, expired, revoked, unused

    # Customer binding
    customer_id = db.Column(db.String(36), db.ForeignKey('customers.id'), nullable=True)
    device_id = db.Column(db.String(255), nullable=True)  # Bound to specific device

    # Time
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
    activated_at = db.Column(db.DateTime, nullable=True)
    expires_at = db.Column(db.DateTime, nullable=True)

    # Payment
    payment_method = db.Column(db.String(50), nullable=True)  # bkash, nagad, card, etc.
    payment_amount = db.Column(db.Float, nullable=True)
    transaction_id = db.Column(db.String(100), nullable=True)

    # Usage tracking
    last_verified_at = db.Column(db.DateTime, nullable=True)
    verification_count = db.Column(db.Integer, default=0)

    def is_valid(self):
        """Check if the license is currently valid"""
        if self.status != 'active':
            return False
        if self.expires_at and datetime.now(timezone.utc) > self.expires_at:
            self.status = 'expired'
            return False
        return True

    def activate(self, device_id, customer_id=None):
        """Activate license for a specific device"""
        now = datetime.now(timezone.utc)
        self.activated_at = now
        self.device_id = device_id
        self.status = 'active'

        if customer_id:
            self.customer_id = customer_id

        # Set expiry based on plan
        from config import Config
        plan_config = Config.PLANS.get(self.plan, {})
        duration_days = plan_config.get('duration_days', 30)
        self.expires_at = now + timedelta(days=duration_days)

    def change_device(self, new_device_id):
        """Allow device change (1 license = 1 device at a time)"""
        self.device_id = new_device_id
        self.last_verified_at = datetime.now(timezone.utc)

    def to_dict(self):
        return {
            'id': self.id,
            'license_key': self.license_key,
            'plan': self.plan,
            'status': self.status,
            'device_id': self.device_id,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'activated_at': self.activated_at.isoformat() if self.activated_at else None,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
            'is_valid': self.is_valid(),
            'payment_method': self.payment_method,
            'payment_amount': self.payment_amount,
            'verification_count': self.verification_count,
        }


class TrialUsage(db.Model):
    """Track free trial usage per device"""
    __tablename__ = 'trial_usage'

    id = db.Column(db.String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    device_id = db.Column(db.String(255), unique=True, nullable=False)
    minutes_used = db.Column(db.Float, default=0)
    first_used_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
    last_used_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc))
    trial_expired = db.Column(db.Boolean, default=False)

    def has_trial_remaining(self):
        from config import Config
        return self.minutes_used < Config.FREE_TRIAL_MINUTES and not self.trial_expired

    def to_dict(self):
        from config import Config
        remaining = max(0, Config.FREE_TRIAL_MINUTES - self.minutes_used)
        return {
            'device_id': self.device_id,
            'minutes_used': round(self.minutes_used, 2),
            'minutes_remaining': round(remaining, 2),
            'trial_expired': self.trial_expired,
            'first_used_at': self.first_used_at.isoformat(),
        }


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

