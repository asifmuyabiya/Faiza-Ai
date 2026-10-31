# 🤖 Faiza AI — Intelligent Phone Assistant

An Android AI assistant that controls your entire phone with voice commands. Similar to MAYA 3.0 but with extra features.

## 📁 Project Structure

```
maya-ai-assistant/
├── android/                  # Android App (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/faiza/ai/
│   │   │   ├── FaizaApp.kt              # Application class
│   │   │   ├── ai/
│   │   │   │   └── GeminiAIProcessor.kt  # Gemini AI integration
│   │   │   ├── license/
│   │   │   │   └── LicenseManager.kt     # License verification
│   │   │   ├── service/
│   │   │   │   ├── FloatingOrbService.kt       # Floating orb + voice
│   │   │   │   ├── FaizaAccessibilityService.kt # Phone control
│   │   │   │   ├── FaizaNotificationListener.kt # Notification reading
│   │   │   │   ├── VoiceRecognitionService.kt   # Wake word detection
│   │   │   │   └── BootReceiver.kt              # Auto-start on boot
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.kt        # Splash screen
│   │   │   │   ├── LicenseActivity.kt     # License activation
│   │   │   │   ├── DashboardActivity.kt   # Main dashboard
│   │   │   │   └── SettingsActivity.kt    # Settings
│   │   │   └── util/
│   │   │       ├── PrefsManager.kt        # SharedPreferences
│   │   │       └── PermissionHelper.kt    # Permission wizard
│   │   └── res/                           # Layouts, drawables, themes
│   └── build.gradle
├── backend/                  # Python Flask Server
│   ├── app.py                # Main server + API routes
│   ├── config.py             # Configuration + Plans
│   ├── models.py             # Database models
│   └── requirements.txt
└── website/                  # Landing Page (saved for later)
    └── index.html
```

## 🔑 Features

| Feature | Basic | Pro | Lifetime |
|---------|-------|-----|----------|
| Voice Commands | ✅ | ✅ | ✅ |
| Phone Control | ✅ | ✅ | ✅ |
| 1 Persona (Faiza) | ✅ | ✅ | ✅ |
| 3 Personas | ❌ | ✅ | ✅ |
| Voice Guardian | ❌ | ✅ | ✅ |
| Echo Guard | ❌ | ✅ | ✅ |
| Skills System | ❌ | ✅ | ✅ |
| Macros | ❌ | ✅ | ✅ |
| SOS Mode | ❌ | ✅ | ✅ |
| Screen Scan | ❌ | ✅ | ✅ |
| Lifetime Updates | ❌ | ❌ | ✅ |

## 🚀 How to Run

### Backend Server
```bash
cd backend
pip install -r requirements.txt
python app.py
```
Server runs at http://localhost:5000

### Android App
1. Open `android/` folder in Android Studio
2. Sync Gradle
3. Run on device/emulator

## 💰 License Key Format
```
FAIZA-PRO-XXXX-XXXX-XXXX
FAIZA-BAS-XXXX-XXXX-XXXX
FAIZA-LIF-XXXX-XXXX-XXXX
```

## 📊 Progress
- [x] Backend License System
- [x] License Key Generation & Verification
- [x] Free Trial System (10 min)
- [x] Android App Structure
- [x] Splash Screen + License Activation UI
- [x] Dashboard UI
- [x] Floating Orb Service
- [x] Gemini AI Integration
- [x] Accessibility Service (phone control)
- [x] Notification Listener
- [x] Boot Auto-start
- [x] Settings Activity (full UI)
- [x] Screen Capture Service (vision)
- [x] API Key Setup Flow (3-step guided)
- [x] API Cost Optimization (cache + token tracking)
- [x] 3 Personas (Faiza / Friday / Venom) — voice, greeting, AI style
- [x] Language: English / বাংলা / हिन्दी (speech, voice, AI replies)
- [x] Proactive Mode (message announcements)
- [x] Call Announcement (caller name)
- [x] App builds successfully (`gradle assembleDebug`)
- [x] AI Providers: Gemini (free, gemini-3.5-flash-lite), OpenAI, Groq, OpenRouter, Custom/local (Pro)
- [x] Skills: 17 built-in phone actions via AI tool-calling (apps, calls, SMS, WhatsApp, alarms, torch, screen…)
- [x] Custom Skills (user routines) + Sub-Agents (Translator, Writer, Tutor…) — Pro
- [ ] Live Voice Mode (gemini-3.8-live, natural voice)
- [ ] Voice Guardian
- [ ] Echo Guard
- [ ] Skills System
- [ ] Macros Recording
- [ ] SOS Mode
- [ ] Screen Capture + Vision
- [ ] Memory Backup/Restore
- [ ] Admin Dashboard (web)
- [ ] Payment Gateway Integration
