import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    SECRET_KEY = os.getenv('SECRET_KEY', 'maya-ai-super-secret-key-change-in-production')
    SQLALCHEMY_DATABASE_URI = os.getenv('DATABASE_URL', 'sqlite:///maya_licenses.db')
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # In-app update check (video 7:15). When you publish a new APK, raise LATEST_VERSION_CODE
    # (the app's versionCode) and set LATEST_APK_URL to where it can be downloaded.
    LATEST_VERSION_CODE = int(os.getenv('LATEST_VERSION_CODE', '1'))
    LATEST_VERSION_NAME = os.getenv('LATEST_VERSION_NAME', '3.0.0')
    LATEST_APK_URL = os.getenv('LATEST_APK_URL', '')
    LATEST_NOTES = os.getenv('LATEST_NOTES', '')
    # Versions below this are told the update is required.
    MIN_SUPPORTED_VERSION_CODE = int(os.getenv('MIN_SUPPORTED_VERSION_CODE', '1'))

    # License Settings
    LICENSE_PREFIX = "FAIZA"
    FREE_TRIAL_MINUTES = 10

    # Plans
    PLANS = {
        'basic': {
            'name': 'Basic',
            'price_bdt': 299,
            'price_usd': 2.99,
            'duration_days': 30,
            'features': [
                'Voice Commands',
                'Phone Control (Call, SMS)',
                'App Launch',
                'Basic AI Chat',
                '1 Persona (Maya)',
            ]
        },
        'pro': {
            'name': 'Pro',
            'price_bdt': 499,
            'price_usd': 4.99,
            'duration_days': 30,
            'features': [
                'Everything in Basic',
                '3 Personas (Maya, Friday, Venom)',
                'Screen Scan + Camera Vision',
                'Smart Automation & Macros',
                'Skills System (Custom MD)',
                'Sub-Agents (Multi-API)',
                'Voice Guardian',
                'Echo Guard',
                'SOS Mode',
                'WhatsApp Integration',
                'Memory Backup & Restore',
                'Custom Floating Orb Designs',
                'Priority Support',
            ]
        },
        'lifetime': {
            'name': 'Lifetime',
            'price_bdt': 1999,
            'price_usd': 19.99,
            'duration_days': 36500,  # ~100 years
            'features': [
                'Everything in Pro',
                'Lifetime Updates',
                'Early Access to New Features',
                'Custom Persona Creation',
                'Unlimited Sub-Agents',
                'VIP WhatsApp Support',
            ]
        }
    }
