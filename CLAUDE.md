# Faiza AI — notes for Claude

The owner talks in Bangla and tests every change on their own phone. They want a signed **update APK** after each
change that installs **over the existing app** (no uninstall, data kept).

## Building the update APK (cloud session)

1. Network: the environment needs `dl.google.com` (Full network access). Install the SDK:
   `cmdline-tools` → `platforms;android-34`, `build-tools;34.0.0`, `platform-tools` into `/opt/android-sdk`;
   `android/local.properties` → `sdk.dir=/opt/android-sdk` (run `git update-index --assume-unchanged android/local.properties` first — the repo copy has the owner's Windows path).
2. Maven Central often answers 429: build with `gradle -I tools/mirror.gradle` (from `android/`), which adds the mirror
   `https://maven-central.storage-download.googleapis.com/maven2/` to `pluginManagement`, `buildscript` and project repositories (`gradle -I mirror.gradle …`).
3. Signing: the owner sends `faiza-release.jks` and `keystore.properties` in chat each session. Put them at
   `android/keystore/faiza-release.jks` and `android/keystore.properties` — both are gitignored, **never commit them**.
   Certificate SHA-256 must be `c6abfdd294965505969459cdac342e0e2a5bd3e51ad2c1bdb02d6633ff49bd0c`
   (check with `apksigner verify --print-certs`). Any other key → "package conflicts with an existing package".
4. Raise `FAIZA_VERSION_CODE` in `android/gradle.properties` by 1 for every APK you hand over.
5. Set `LOCALAPPDATA` to a scratch folder (build output goes there), run `:app:testDebugUnitTest :app:assembleRelease`,
   send `FaizaBuild/FaizaAI/app/outputs/apk/release/app-release.apk` to the owner.
6. No license server is deployed: `FAIZA_SERVER_URL` stays `CHANGE-ME` on purpose.

## Talking over Faiza (barge-in) — confirmed working by the owner in v5, do not undo

- Problem: on the loudspeaker she heard her own voice and stopped herself, or ignored the owner's "থামো".
- Fix: like a phone call on speaker. With "Stop when I talk" on, the loudspeaker uses **real call mode**:
  `GeminiLiveSession.enterCallMode()` — `MODE_IN_COMMUNICATION`, built-in speaker as communication device,
  playback `USAGE_VOICE_COMMUNICATION`, mic `VOICE_COMMUNICATION` + `AcousticEchoCanceler`. The phone's echo
  canceller then removes her voice and keeps the owner's (even softer) voice.
- **Never move her loudspeaker voice back to the MEDIA stream while barge-in is on** — the phone's echo canceller
  only works on the call path, and the self-interruptions come back.
- Stop words (`EchoText.isStopCommand` in `ai/live/BargeInDetector.kt`): exact words only (থামো, থামেন, চুপ, স্টপ,
  stop, wait…), twice in a row or in the owner's own words; never substring matches (her echo turned "থামবে" into a stop).
- A stop not followed by the owner's words resumes her answer (`localBargeIn` / `resumeAfterEcho`), so she never
  gets stuck silent. After a real stop word she does not auto-resume for 8 s.

## Code Studio live window (v6)

`ai/code/CodeAgent.kt` emits written files on `CodeAgent.typing`; `ui/windows/CodeWindow.kt` (💻, `WindowKind.CODE`)
floats over any app, types the code out (`CodeTyping.charsPerTick`) and scrolls up like a terminal. Opens
automatically when a Code Studio job starts (`prefs.codeLiveWindow`, default on).

## Reading the screen & typing (v7)

- `FaizaAccessibilityService.appRoot()` leaves out the orb / mini windows by **window type** (overlays are system
  windows), **not** by Faiza's package. Her own screens (Code Studio prompt box, Chat, Settings) must stay readable and
  typeable — the old package filter made her say "this isn't a text box".
- Text fields = `TextFieldRules.isTextField` (editable flag, EditText-like class, or accepts SET_TEXT). `read_screen`
  marks them `✏️ TEXT BOX` with "empty — hint: …" / "contains: …"; `type_text` takes `number` or `field` to pick a box
  and falls back to select-all + paste when SET_TEXT is refused.

## Accessibility ON but not running (v8)

Android can keep the Accessibility switch ON after stopping the service (app update, battery saving, crash). Tools
must use `A11yStatus.awaitService()` / `A11yStatus.errorText()` (`service/A11yStatus.kt`), never
`FaizaAccessibilityService.instance` alone with a "turn it on" message: in the ON-but-stopped state the fix is to switch
Faiza AI OFF and ON again in Settings → Accessibility. The Permissions screen shows granted only when it really runs.
`onAccessibilityEvent` / `onKeyEvent` are wrapped in try/catch so a crash can't stop the service.
