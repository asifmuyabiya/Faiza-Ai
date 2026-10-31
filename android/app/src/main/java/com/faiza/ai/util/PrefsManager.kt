package com.faiza.ai.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.Persona
import com.faiza.ai.ai.provider.AIProviders
import com.faiza.ai.ai.provider.ProviderType

/**
 * SharedPreferences manager for storing app settings and license data
 */
class PrefsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "faiza_ai_prefs", Context.MODE_PRIVATE
    )

    companion object {
        // License
        const val KEY_LICENSE_KEY = "license_key"
        const val KEY_LICENSE_PLAN = "license_plan"
        const val KEY_LICENSE_VALID = "license_valid"
        const val KEY_LICENSE_EXPIRY = "license_expiry"

        // Trial
        const val KEY_TRIAL_MINUTES_USED = "trial_minutes_used"
        const val KEY_TRIAL_EXPIRED = "trial_expired"

        // Settings
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        const val KEY_SELECTED_PERSONA = "selected_persona"
        const val KEY_VOICE_GUARDIAN_ENABLED = "voice_guardian_enabled"
        const val KEY_ECHO_GUARD_ENABLED = "echo_guard_enabled"
        const val KEY_SOS_ENABLED = "sos_enabled"
        const val KEY_PROACTIVE_MODE = "proactive_mode"
        const val KEY_CALL_ANNOUNCEMENT = "call_announcement"
        const val KEY_FLOATING_ORB_DESIGN = "floating_orb_design"
        const val KEY_LANGUAGE = "language"

        // AI Settings
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_CUSTOM_API_KEY = "custom_api_key"
        const val KEY_CUSTOM_API_URL = "custom_api_url"
        const val KEY_API_KEY_PREFIX = "api_key_"
        const val KEY_MODEL_PREFIX = "ai_model_"

        // Live voice
        const val KEY_LIVE_ENABLED = "live_enabled"
        const val KEY_LIVE_MODEL = "live_model"
        const val KEY_LIVE_VOICE_PREFIX = "live_voice_"
        const val DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"

        // Skills
        const val KEY_DISABLED_SKILLS = "disabled_skills"
        const val KEY_CUSTOM_SKILLS = "custom_skills"
        const val KEY_SUB_AGENTS = "sub_agents"

        // User
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_ONBOARDING_DONE = "onboarding_done"

        // Personal & assistant (video 10:55, 13:09)
        const val KEY_ASSISTANT_NAME = "assistant_name"
        const val KEY_FAVORITE_MUSIC_APP = "favorite_music_app"
        const val KEY_FAVORITE_SONG = "favorite_song"
        const val KEY_YOUTUBE_HANDLE = "youtube_handle"
        const val KEY_YOUTUBE_API_KEY = "youtube_api_key"
        const val KEY_GIRLFRIEND_MODE = "girlfriend_mode"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_INCOGNITO = "incognito_mode"

        // Proactive chat (video 15:26) and Voice Guardian (video 19:08)
        const val KEY_PROACTIVE_CHAT = "proactive_chat"
        const val KEY_VG_MODE = "voice_guardian_mode"
        const val KEY_VG_AWAY = "voice_guardian_away"
        const val KEY_VG_THRESHOLD = "voice_guardian_threshold"
        const val KEY_VG_PROFILES = "voice_guardian_profiles"
        const val VG_MODE_EVERYONE = "everyone"
        const val VG_MODE_OWNER = "owner"
        const val VG_MODE_FAMILY = "family"

        // Contacts, calling & SOS (video 24:07, 25:01, 32:43)
        const val KEY_COUNTRY_DIAL = "country_dial_code"
        const val KEY_FAVORITE_CONTACTS = "favorite_contacts"
        const val KEY_MACROS = "macros"
        const val KEY_MAPS_API_KEY = "maps_api_key"
        const val KEY_ROUTINES = "routines"
        const val KEY_QUIET_MODE = "quiet_mode"
        const val KEY_SPEAKING_STYLE = "speaking_style"
        const val KEY_NARRATE = "narrate_actions"
        const val KEY_STARTUP_LINE = "cinematic_startup"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        const val KEY_AVAILABLE_UPDATE = "available_update"
        const val KEY_WATCHES = "watches"
        const val KEY_TAVILY_API_KEY = "tavily_api_key"
        const val KEY_POLLINATIONS_TOKEN = "pollinations_token"
        const val KEY_PREFERRED_SIM = "preferred_sim"
        const val KEY_SOS_MESSAGE = "sos_message"

        // Orb look & behaviour (video 25:56–29:11)
        const val KEY_ORB_THEME = "orb_theme"
        const val KEY_ORB_SIZE = "orb_size_percent"
        const val KEY_ORB_ENABLED = "orb_enabled"
        const val KEY_ORB_HIDE_IN_APPS = "orb_hide_in_apps"
        const val KEY_ORB_EDGE_GLOW = "orb_edge_glow"
        const val KEY_REALISTIC_TYPING = "realistic_typing"
        const val KEY_SCREEN_RECORDING = "screen_recording_mode"
        const val KEY_START_ON_BOOT = "start_on_boot"

        // SOS Contacts
        const val KEY_SOS_CONTACTS = "sos_contacts"
    }

    // ====== Device ID ======
    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown-device"
    }

    // ====== License ======
    var licenseKey: String?
        get() = prefs.getString(KEY_LICENSE_KEY, null)
        set(value) = prefs.edit().putString(KEY_LICENSE_KEY, value).apply()

    var licensePlan: String?
        get() = prefs.getString(KEY_LICENSE_PLAN, null)
        set(value) = prefs.edit().putString(KEY_LICENSE_PLAN, value).apply()

    var isLicenseValid: Boolean
        get() = prefs.getBoolean(KEY_LICENSE_VALID, false)
        set(value) = prefs.edit().putBoolean(KEY_LICENSE_VALID, value).apply()

    var licenseExpiry: String?
        get() = prefs.getString(KEY_LICENSE_EXPIRY, null)
        set(value) = prefs.edit().putString(KEY_LICENSE_EXPIRY, value).apply()

    // ====== Trial ======
    var trialMinutesUsed: Float
        get() = prefs.getFloat(KEY_TRIAL_MINUTES_USED, 0f)
        set(value) = prefs.edit().putFloat(KEY_TRIAL_MINUTES_USED, value).apply()

    var isTrialExpired: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_EXPIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_EXPIRED, value).apply()

    fun hasValidAccess(): Boolean {
        return hasFullAccess || (!isTrialExpired && trialMinutesUsed < 10f)
    }

    /** Licensed, or everything unlocked by the testing switch. Use this for feature gates. */
    val hasFullAccess: Boolean
        get() = FaizaApp.UNLOCK_ALL_FOR_TESTING || isLicenseValid

    // ====== Settings ======
    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var selectedPersona: String
        get() = prefs.getString(KEY_SELECTED_PERSONA, "faiza") ?: "faiza"
        set(value) = prefs.edit().putString(KEY_SELECTED_PERSONA, value).apply()

    var isVoiceGuardianEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_GUARDIAN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_GUARDIAN_ENABLED, value).apply()

    var isEchoGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_ECHO_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ECHO_GUARD_ENABLED, value).apply()

    var isSosEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOS_ENABLED, value).apply()

    var isProactiveMode: Boolean
        get() = prefs.getBoolean(KEY_PROACTIVE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_PROACTIVE_MODE, value).apply()

    var isCallAnnouncementEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_ANNOUNCEMENT, true)
        set(value) = prefs.edit().putBoolean(KEY_CALL_ANNOUNCEMENT, value).apply()

    var floatingOrbDesign: Int
        get() = prefs.getInt(KEY_FLOATING_ORB_DESIGN, 0)
        set(value) = prefs.edit().putInt(KEY_FLOATING_ORB_DESIGN, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    // ====== AI ======
    var geminiApiKey: String?
        get() = prefs.getString(KEY_GEMINI_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, "gemini") ?: "gemini"
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()

    var customApiUrl: String?
        get() = prefs.getString(KEY_CUSTOM_API_URL, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_API_URL, value).apply()

    fun apiKeyFor(type: ProviderType): String? =
        if (type == ProviderType.GEMINI) geminiApiKey
        else prefs.getString(KEY_API_KEY_PREFIX + type.id, null)

    fun setApiKey(type: ProviderType, key: String) {
        if (type == ProviderType.GEMINI) geminiApiKey = key
        else prefs.edit().putString(KEY_API_KEY_PREFIX + type.id, key).apply()
    }

    fun modelFor(type: ProviderType): String =
        prefs.getString(KEY_MODEL_PREFIX + type.id, null) ?: type.defaultModel

    fun setModel(type: ProviderType, model: String) =
        prefs.edit().putString(KEY_MODEL_PREFIX + type.id, model).apply()

    val activeProvider: ProviderType get() = ProviderType.from(aiProvider)

    fun isAiConfigured(): Boolean = AIProviders.fromPrefs(this) != null

    // ====== Live voice (Gemini Live API) ======
    var isLiveVoiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_ENABLED, value).apply()

    var liveModel: String
        get() = prefs.getString(KEY_LIVE_MODEL, null)?.ifBlank { null } ?: DEFAULT_LIVE_MODEL
        set(value) = prefs.edit().putString(KEY_LIVE_MODEL, value).apply()

    fun liveVoiceFor(personaId: String, default: String): String =
        prefs.getString(KEY_LIVE_VOICE_PREFIX + personaId, null) ?: default

    fun setLiveVoice(personaId: String, voice: String) =
        prefs.edit().putString(KEY_LIVE_VOICE_PREFIX + personaId, voice).apply()

    /** Live mode needs a Gemini key, whichever provider handles text. */
    fun canUseLive(): Boolean = isLiveVoiceEnabled && !geminiApiKey.isNullOrBlank()

    // ====== Skills ======
    var disabledSkills: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED_SKILLS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DISABLED_SKILLS, value).apply()

    var customSkillsJson: String?
        get() = prefs.getString(KEY_CUSTOM_SKILLS, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_SKILLS, value).apply()

    var subAgentsJson: String?
        get() = prefs.getString(KEY_SUB_AGENTS, null)
        set(value) = prefs.edit().putString(KEY_SUB_AGENTS, value).apply()

    // ====== User ======
    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    // ====== Personal & assistant ======
    /** Custom assistant name ("Maya", "Jarvis"…); empty = the persona's own name. */
    var assistantNameSetting: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ASSISTANT_NAME, value.trim()).apply()

    fun assistantName(persona: Persona = Persona.from(selectedPersona)): String =
        assistantNameSetting.ifBlank { persona.displayName }

    /** "youtube", "youtube_music" or "spotify". */
    var favoriteMusicApp: String
        get() = prefs.getString(KEY_FAVORITE_MUSIC_APP, "youtube") ?: "youtube"
        set(value) = prefs.edit().putString(KEY_FAVORITE_MUSIC_APP, value).apply()

    var favoriteSong: String
        get() = prefs.getString(KEY_FAVORITE_SONG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FAVORITE_SONG, value.trim()).apply()

    var youtubeHandle: String
        get() = prefs.getString(KEY_YOUTUBE_HANDLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YOUTUBE_HANDLE, value.trim()).apply()

    var youtubeApiKey: String
        get() = prefs.getString(KEY_YOUTUBE_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_YOUTUBE_API_KEY, value.trim()).apply()

    /** Pet names, playful teasing and light flirting (Faiza persona only). */
    var isGirlfriendMode: Boolean
        get() = prefs.getBoolean(KEY_GIRLFRIEND_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_GIRLFRIEND_MODE, value).apply()

    var isMemoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()

    /** She starts conversations by herself when you've been quiet (only while she's awake). */
    var isProactiveChat: Boolean
        get() = prefs.getBoolean(KEY_PROACTIVE_CHAT, false)
        set(value) = prefs.edit().putBoolean(KEY_PROACTIVE_CHAT, value).apply()

    /** Who may talk to her: everyone / owner / owner + family. */
    var voiceGuardianMode: String
        get() = prefs.getString(KEY_VG_MODE, VG_MODE_OWNER) ?: VG_MODE_OWNER
        set(value) = prefs.edit().putString(KEY_VG_MODE, value).apply()

    /** Guard mode: a stranger gets one warning; the second time the phone locks. */
    var isVoiceGuardianAway: Boolean
        get() = prefs.getBoolean(KEY_VG_AWAY, false)
        set(value) = prefs.edit().putBoolean(KEY_VG_AWAY, value).apply()

    /** Minimum voice match (%) to count as an allowed voice. */
    var voiceGuardianThreshold: Int
        get() = prefs.getInt(KEY_VG_THRESHOLD, 55)
        set(value) = prefs.edit().putInt(KEY_VG_THRESHOLD, value.coerceIn(20, 95)).apply()

    var voiceProfilesJson: String?
        get() = prefs.getString(KEY_VG_PROFILES, null)
        set(value) = prefs.edit().putString(KEY_VG_PROFILES, value).apply()

    /** Dial code without "+" (e.g. "880"); empty = detect from the SIM. */
    var countryDialCode: String
        get() = prefs.getString(KEY_COUNTRY_DIAL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COUNTRY_DIAL, value.filter { it.isDigit() }).apply()

    // Optional extra services (video 29:43). Never included in backups.
    var mapsApiKey: String
        get() = prefs.getString(KEY_MAPS_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MAPS_API_KEY, value.trim()).apply()

    var tavilyApiKey: String
        get() = prefs.getString(KEY_TAVILY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TAVILY_API_KEY, value.trim()).apply()

    var braveApiKey: String
        get() = prefs.getString("brave_api_key", "") ?: ""
        set(value) = prefs.edit().putString("brave_api_key", value.trim()).apply()

    var serpApiKey: String
        get() = prefs.getString("serp_api_key", "") ?: ""
        set(value) = prefs.edit().putString("serp_api_key", value.trim()).apply()

    /** When a Gemini model hits its free limit, quietly try another one (MAYA sub-agent brain). */
    var isAutoSwitchModels: Boolean
        get() = prefs.getBoolean("auto_switch_models", true)
        set(value) = prefs.edit().putBoolean("auto_switch_models", value).apply()

    var pollinationsToken: String
        get() = prefs.getString(KEY_POLLINATIONS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_POLLINATIONS_TOKEN, value.trim()).apply()

    // Quiet mode: see util/QuietMode.
    var isQuietMode: Boolean
        get() = prefs.getBoolean(KEY_QUIET_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_MODE, value).apply()

    /** When manual quiet mode ends by itself (0 = only when switched off). */
    var quietUntil: Long
        get() = prefs.getLong("quiet_until", 0L)
        set(value) = prefs.edit().putLong("quiet_until", value).apply()

    var isQuietFollowPhone: Boolean
        get() = prefs.getBoolean("quiet_follow_phone", true)
        set(value) = prefs.edit().putBoolean("quiet_follow_phone", value).apply()

    var isQuietHeadphonesOk: Boolean
        get() = prefs.getBoolean("quiet_headphones", true)
        set(value) = prefs.edit().putBoolean("quiet_headphones", value).apply()

    /** Small extra boost for her voice on the loudspeaker. */
    var isLouderVoice: Boolean
        get() = prefs.getBoolean("louder_voice", true)
        set(value) = prefs.edit().putBoolean("louder_voice", value).apply()

    /** How Faiza sounds (see SpeakingStyle). */
    var speakingStyle: String
        get() = prefs.getString(KEY_SPEAKING_STYLE, "normal") ?: "normal"
        set(value) = prefs.edit().putString(KEY_SPEAKING_STYLE, value).apply()

    /** Say each step while doing a task ("Searching Rahim on WhatsApp… found him…"). */
    var isNarrateActions: Boolean
        get() = prefs.getBoolean(KEY_NARRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_NARRATE, value).apply()

    /** A JARVIS-style line when she starts ("Systems online, sir."). */
    var isCinematicStartup: Boolean
        get() = prefs.getBoolean(KEY_STARTUP_LINE, false)
        set(value) = prefs.edit().putBoolean(KEY_STARTUP_LINE, value).apply()

    // Home screen (MAYA): the 🔔 inbox and the Mood card.
    var activityLogJson: String?
        get() = prefs.getString("activity_log", null)
        set(value) = prefs.edit().putString("activity_log", value).apply()

    var activityLogSeenAt: Long
        get() = prefs.getLong("activity_log_seen", 0L)
        set(value) = prefs.edit().putLong("activity_log_seen", value).apply()

    /** The mood Faiza noticed ("happy", "tired"…) and when. */
    var userMood: String
        get() = prefs.getString("user_mood", "") ?: ""
        set(value) = prefs.edit().putString("user_mood", value).putLong("user_mood_at", System.currentTimeMillis()).apply()

    val userMoodAt: Long get() = prefs.getLong("user_mood_at", 0L)

    /** In-app update check (video 7:15). */
    var lastUpdateCheckAt: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var availableUpdateJson: String?
        get() = prefs.getString(KEY_AVAILABLE_UPDATE, null)
        set(value) = prefs.edit().putString(KEY_AVAILABLE_UPDATE, value).apply()

    /** Scheduled tasks and watches (video 40:31), JSON lists. */
    var routinesJson: String?
        get() = prefs.getString(KEY_ROUTINES, null)
        set(value) = prefs.edit().putString(KEY_ROUTINES, value).apply()

    var watchesJson: String?
        get() = prefs.getString(KEY_WATCHES, null)
        set(value) = prefs.edit().putString(KEY_WATCHES, value).apply()

    var isBuiltInMacrosSeeded: Boolean
        get() = prefs.getBoolean("builtin_macros_seeded", false)
        set(value) = prefs.edit().putBoolean("builtin_macros_seeded", value).apply()

    /** Recorded macros (video 33:47), JSON list. */
    var macrosJson: String?
        get() = prefs.getString(KEY_MACROS, null)
        set(value) = prefs.edit().putString(KEY_MACROS, value).apply()

    var favoriteContactsJson: String?
        get() = prefs.getString(KEY_FAVORITE_CONTACTS, null)
        set(value) = prefs.edit().putString(KEY_FAVORITE_CONTACTS, value).apply()

    /** SIM for calls: 0 = ask the first time, 1 or 2 = always that SIM. */
    var preferredSim: Int
        get() = prefs.getInt(KEY_PREFERRED_SIM, 0)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SIM, value.coerceIn(0, 2)).apply()

    var orbTheme: Int
        get() = prefs.getInt(KEY_ORB_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_ORB_THEME, value).apply()

    /** Orb size as a percentage of the normal size (60–150). */
    var orbSizePercent: Int
        get() = prefs.getInt(KEY_ORB_SIZE, 100).coerceIn(60, 150)
        set(value) = prefs.edit().putInt(KEY_ORB_SIZE, value.coerceIn(60, 150)).apply()

    /** Show the floating orb at all. */
    var isOrbEnabled: Boolean
        get() = prefs.getBoolean(KEY_ORB_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ORB_ENABLED, value).apply()

    /** The orb sits on the home screen and disappears inside other apps. */
    var isOrbHiddenInApps: Boolean
        get() = prefs.getBoolean(KEY_ORB_HIDE_IN_APPS, false)
        set(value) = prefs.edit().putBoolean(KEY_ORB_HIDE_IN_APPS, value).apply()

    var isOrbEdgeGlow: Boolean
        get() = prefs.getBoolean(KEY_ORB_EDGE_GLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_ORB_EDGE_GLOW, value).apply()

    /** Type one character at a time, like a person. */
    /** "slow", "normal" or "fast" (MAYA Realistic typing speed). */
    var typingSpeed: String
        get() = prefs.getString("typing_speed", "normal") ?: "normal"
        set(value) = prefs.edit().putString("typing_speed", value).apply()

    /** Apps (package names, comma-separated) where she types letter by letter; empty = every app. */
    var typingApps: String
        get() = prefs.getString("typing_apps", "") ?: ""
        set(value) = prefs.edit().putString("typing_apps", value).apply()

    var isRealisticTyping: Boolean
        get() = prefs.getBoolean(KEY_REALISTIC_TYPING, true)
        set(value) = prefs.edit().putBoolean(KEY_REALISTIC_TYPING, value).apply()

    /** Faiza talks through the loudspeaker and never listens while talking, so a screen recording gets both voices. */
    var isScreenRecordingMode: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_RECORDING, false)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_RECORDING, value).apply()

    var isStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, true)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    /** Optional personal line added to the SOS text. */
    var sosMessage: String
        get() = prefs.getString(KEY_SOS_MESSAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SOS_MESSAGE, value.trim().take(200)).apply()

    /** Never remember passwords, OTPs, PINs or card numbers. On by default. */
    var isIncognito: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO, true)
        set(value) = prefs.edit().putBoolean(KEY_INCOGNITO, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    // ====== SOS Contacts (comma-separated) ======
    var sosContacts: List<String>
        get() {
            val raw = prefs.getString(KEY_SOS_CONTACTS, "") ?: ""
            return if (raw.isEmpty()) emptyList() else raw.split(",")
        }
        set(value) = prefs.edit().putString(KEY_SOS_CONTACTS, value.joinToString(",")).apply()

    // SharedPreferences holds listeners weakly — callers must keep a strong reference.
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    /** Edge glow in all colours (RGB) instead of the theme colour. */
    var isEdgeGlowRgb: Boolean
        get() = prefs.getBoolean("edge_glow_rgb", true)
        set(value) = prefs.edit().putBoolean("edge_glow_rgb", value).apply()

    /** Faiza's own "tell me in N seconds" timers. */
    var faizaTimersJson: String?
        get() = str("faiza_timers")
        set(value) = putStr("faiza_timers", value)

    /** Speak up when any app asks "Delete…?". */
    var isDeleteGuard: Boolean
        get() = prefs.getBoolean("delete_guard", true)
        set(value) = prefs.edit().putBoolean("delete_guard", value).apply()

    /** Ask "shall I send?" before messages (off: she narrates and sends what the user dictated). */
    var isConfirmBeforeSend: Boolean
        get() = prefs.getBoolean("confirm_before_send", false)
        set(value) = prefs.edit().putBoolean("confirm_before_send", value).apply()

    // ====== Audio ======
    /** Her voice on the call loudspeaker (separate from media). */
    var isCallSpeaker: Boolean
        get() = prefs.getBoolean("call_speaker", true)
        set(value) = prefs.edit().putBoolean("call_speaker", value).apply()
    /** Her voice on the loudspeaker (like a WhatsApp video call), never the ear speaker. */
    var isLoudspeaker: Boolean
        get() = prefs.getBoolean("loudspeaker", true)
        set(value) = prefs.edit().putBoolean("loudspeaker", value).apply()
    /** 0 off, 1 soft voices, 2 whispers (MicBoost). */
    var micBoostLevel: Int
        get() = prefs.getInt("mic_boost", 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt("mic_boost", value.coerceIn(0, 2)).apply()

    /** Touch Guard: photograph + warn + siren when someone touches the phone while you're away. */
    var isTouchGuard: Boolean
        get() = prefs.getBoolean("touch_guard", false)
        set(value) = prefs.edit().putBoolean("touch_guard", value).apply()

    // ====== Owner lock (secret code) ======
    var isOwnerLock: Boolean
        get() = prefs.getBoolean("owner_lock", false)
        set(value) = prefs.edit().putBoolean("owner_lock", value).apply()
    var ownerLockCode: String?
        get() = str("owner_lock_code")
        set(value) = putStr("owner_lock_code", value)
    /** "unlock" (after each phone unlock), "daily" or "always" (every conversation). */
    var ownerLockAsk: String
        get() = str("owner_lock_ask") ?: "unlock"
        set(value) = putStr("owner_lock_ask", value)
    var ownerLockSms: Boolean
        get() = prefs.getBoolean("owner_lock_sms", true)
        set(value) = prefs.edit().putBoolean("owner_lock_sms", value).apply()
    var ownerVerifiedAt: Long
        get() = prefs.getLong("owner_verified_at", 0L)
        set(value) = prefs.edit().putLong("owner_verified_at", value).apply()
    var lastPhoneUnlockAt: Long
        get() = prefs.getLong("last_phone_unlock", 0L)
        set(value) = prefs.edit().putLong("last_phone_unlock", value).apply()
    var intrudersJson: String?
        get() = str("intruders")
        set(value) = putStr("intruders", value)

    // ====== Event triggers ======
    private fun flag(key: String, def: Boolean) = prefs.getBoolean(key, def)
    private fun setFlag(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    var isEventTriggers: Boolean
        get() = flag("ev_master", true); set(v) = setFlag("ev_master", v)
    /** Speak event lines with the phone's own voice even when no live session (standby). */
    var isSpeakWhileAsleep: Boolean
        get() = flag("ev_asleep", true); set(v) = setFlag("ev_asleep", v)
    /** Ignore silent / DND for event lines (calls still stay silent). */
    var isSpeakOnSilent: Boolean
        get() = flag("ev_on_silent", false); set(v) = setFlag("ev_on_silent", v)
    var trigChargerIn: Boolean get() = flag("ev_charger_in", true); set(v) = setFlag("ev_charger_in", v)
    var trigChargerOut: Boolean get() = flag("ev_charger_out", true); set(v) = setFlag("ev_charger_out", v)
    var trigBatteryFull: Boolean get() = flag("ev_batt_full", true); set(v) = setFlag("ev_batt_full", v)
    var trigBatteryLow: Boolean get() = flag("ev_batt_low", true); set(v) = setFlag("ev_batt_low", v)
    var trigBatteryCritical: Boolean get() = flag("ev_batt_crit", true); set(v) = setFlag("ev_batt_crit", v)
    var trigHeadphonesIn: Boolean get() = flag("ev_hp_in", true); set(v) = setFlag("ev_hp_in", v)
    var trigHeadphonesOut: Boolean get() = flag("ev_hp_out", true); set(v) = setFlag("ev_hp_out", v)
    var trigBtOn: Boolean get() = flag("ev_bt_on", true); set(v) = setFlag("ev_bt_on", v)
    var trigBtOff: Boolean get() = flag("ev_bt_off", true); set(v) = setFlag("ev_bt_off", v)
    var trigWifiOn: Boolean get() = flag("ev_wifi_on", false); set(v) = setFlag("ev_wifi_on", v)
    var trigWifiOff: Boolean get() = flag("ev_wifi_off", false); set(v) = setFlag("ev_wifi_off", v)
    var trigAirplaneOn: Boolean get() = flag("ev_air_on", true); set(v) = setFlag("ev_air_on", v)
    var trigAirplaneOff: Boolean get() = flag("ev_air_off", true); set(v) = setFlag("ev_air_off", v)
    var trigPhoneSilent: Boolean get() = flag("ev_silent", false); set(v) = setFlag("ev_silent", v)
    var trigRingerBack: Boolean get() = flag("ev_ringer_back", false); set(v) = setFlag("ev_ringer_back", v)
    var trigAppInstalled: Boolean get() = flag("ev_app_in", true); set(v) = setFlag("ev_app_in", v)
    var trigAppUninstalled: Boolean get() = flag("ev_app_out", true); set(v) = setFlag("ev_app_out", v)

    /** Minutes of silence before a live session sleeps (0 = never). MAYA Never/5/10/20/45. */
    var liveSleepMinutes: Int
        get() = prefs.getInt("live_sleep_min", 3)
        set(value) = prefs.edit().putInt("live_sleep_min", value).apply()

    /** "Stop when I talk": she stops the moment you speak over her. Off = she never stops mid-sentence. */
    var isBargeIn: Boolean
        get() = prefs.getBoolean("barge_in", true)
        set(value) = prefs.edit().putBoolean("barge_in", value).apply()

    /** Listen for "Hey Faiza" only while the screen is on (saves a lot of battery). */
    var isWakeScreenOnly: Boolean
        get() = prefs.getBoolean("wake_screen_only", false)
        set(value) = prefs.edit().putBoolean("wake_screen_only", value).apply()

    // ====== Research agent ======
    /** Its own Gemini key (empty = use the main Gemini key). */
    var researchApiKey: String
        get() = prefs.getString("research_key", "") ?: ""
        set(value) = prefs.edit().putString("research_key", value.trim()).apply()
    /** Its own model (empty = newest available, picked automatically). */
    var researchModel: String
        get() = prefs.getString("research_model", "") ?: ""
        set(value) = prefs.edit().putString("research_model", value.trim()).apply()
    /** Gaming match results (JSON). */
    var gameStatsJson: String?
        get() = prefs.getString("game_stats", null)
        set(value) = prefs.edit().putString("game_stats", value).apply()

    /** The first-start setup wizard was finished or skipped. */
    var setupWizardDone: Boolean
        get() = prefs.getBoolean("setup_wizard_done", false)
        set(value) = prefs.edit().putBoolean("setup_wizard_done", value).apply()
    /** Permissions already asked once (a second "no" means only app settings can change it). */
    var askedPermissions: Set<String>
        get() = prefs.getStringSet("asked_permissions", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("asked_permissions", value).apply()

    /** Screen off: listen for "Hey Faiza" only while charging or 30%+ battery. */
    var isWakeSmartBattery: Boolean
        get() = prefs.getBoolean("wake_smart_battery", true)
        set(value) = prefs.edit().putBoolean("wake_smart_battery", value).apply()

    /** Send only the everyday tools in full (faster, more accurate answers). */
    var isFastTools: Boolean
        get() = prefs.getBoolean("fast_tools", true)
        set(value) = prefs.edit().putBoolean("fast_tools", value).apply()

    // Driving
    var homeAddress: String
        get() = prefs.getString("home_address", "") ?: ""
        set(value) = prefs.edit().putString("home_address", value).apply()
    var workAddress: String
        get() = prefs.getString("work_address", "") ?: ""
        set(value) = prefs.edit().putString("work_address", value).apply()
    /** Spoken warning above this speed (km/h); 0 = off. */
    var drivingSpeedLimit: Int
        get() = prefs.getInt("driving_speed_limit", 80)
        set(value) = prefs.edit().putInt("driving_speed_limit", value).apply()

    /** Medicine courses (JSON). */
    var medicineJson: String?
        get() = prefs.getString("medicine_json", null)
        set(value) = prefs.edit().putString("medicine_json", value).apply()

    /** Log bKash / Nagad / Rocket transaction SMS into the money book. */
    var smsMoneyLog: Boolean
        get() = prefs.getBoolean("sms_money_log", false)
        set(value) = prefs.edit().putBoolean("sms_money_log", value).apply()

    // Prayer
    var prayerLat: Float
        get() = prefs.getFloat("prayer_lat", 0f)
        set(value) = prefs.edit().putFloat("prayer_lat", value).apply()
    var prayerLng: Float
        get() = prefs.getFloat("prayer_lng", 0f)
        set(value) = prefs.edit().putFloat("prayer_lng", value).apply()
    var prayerMethod: String
        get() = prefs.getString("prayer_method", "KARACHI") ?: "KARACHI"
        set(value) = prefs.edit().putString("prayer_method", value).apply()
    var prayerHanafi: Boolean
        get() = prefs.getBoolean("prayer_hanafi", true)
        set(value) = prefs.edit().putBoolean("prayer_hanafi", value).apply()
    var hijriAdjust: Int
        get() = prefs.getInt("hijri_adjust", 0)
        set(value) = prefs.edit().putInt("hijri_adjust", value).apply()
    var azanReminders: Boolean
        get() = prefs.getBoolean("azan_reminders", false)
        set(value) = prefs.edit().putBoolean("azan_reminders", value).apply()
    var azanBeforeMin: Int
        get() = prefs.getInt("azan_before_min", 0)
        set(value) = prefs.edit().putInt("azan_before_min", value).apply()
    var ramadanReminders: Boolean
        get() = prefs.getBoolean("ramadan_reminders", true)
        set(value) = prefs.edit().putBoolean("ramadan_reminders", value).apply()
    var azanSoundUri: String?
        get() = prefs.getString("azan_sound_uri", null)
        set(value) = prefs.edit().putString("azan_sound_uri", value).apply()

    /** Who sent which WhatsApp voice message (from notifications), JSON. */
    var voiceNoteLog: String?
        get() = prefs.getString("voice_note_log", null)
        set(value) = prefs.edit().putString("voice_note_log", value).apply()

    /** Double-press the earphone button to talk to Faiza. */
    var isHeadsetTrigger: Boolean
        get() = prefs.getBoolean("headset_trigger", true)
        set(value) = prefs.edit().putBoolean("headset_trigger", value).apply()

    /** What she texts a caller when the user says "পরে কল দিচ্ছি বলে দাও" (empty = default). */
    var busyReplyText: String
        get() = prefs.getString("busy_reply_text", "") ?: ""
        set(value) = prefs.edit().putString("busy_reply_text", value).apply()

    // Gaming mode
    var gamingEnabled: Boolean
        get() = prefs.getBoolean("gaming_enabled", true)
        set(value) = prefs.edit().putBoolean("gaming_enabled", value).apply()
    /** Ask "গেমিং মোড অন করব?" when a game opens. */
    var gamingAsk: Boolean
        get() = prefs.getBoolean("gaming_ask", true)
        set(value) = prefs.edit().putBoolean("gaming_ask", value).apply()
    /** Start by itself in every game, without asking. */
    var gamingAutoStart: Boolean
        get() = prefs.getBoolean("gaming_auto_start", false)
        set(value) = prefs.edit().putBoolean("gaming_auto_start", value).apply()
    /** Turn off when the user leaves the game. */
    var gamingAutoOff: Boolean
        get() = prefs.getBoolean("gaming_auto_off", true)
        set(value) = prefs.edit().putBoolean("gaming_auto_off", value).apply()
    var gamingTalk: String
        get() = prefs.getString("gaming_talk", "CHATTY") ?: "CHATTY"
        set(value) = prefs.edit().putString("gaming_talk", value).apply()
    /** Gaming: don't listen while she talks, so game sound / her own voice can't cut her off. */
    var gamingEchoGuard: Boolean
        get() = prefs.getBoolean("gaming_echo_guard", true)
        set(value) = prefs.edit().putBoolean("gaming_echo_guard", value).apply()
    /** "" = automatic, SHARP or SMOOTH. */
    var gamingQuality: String
        get() = prefs.getString("gaming_quality", "") ?: ""
        set(value) = prefs.edit().putString("gaming_quality", value).apply()
    var gamingJokes: Boolean
        get() = prefs.getBoolean("gaming_jokes", true)
        set(value) = prefs.edit().putBoolean("gaming_jokes", value).apply()

    // Code Studio
    var codeClaudeKey: String
        get() = prefs.getString("code_claude_key", "") ?: ""
        set(value) = prefs.edit().putString("code_claude_key", value.trim()).apply()
    var codeClaudeModel: String
        get() = prefs.getString("code_claude_model", "") ?: ""
        set(value) = prefs.edit().putString("code_claude_model", value.trim()).apply()
    /** Code Studio's own Gemini key (empty = Faiza's main key). */
    var codeGeminiKey: String
        get() = prefs.getString("code_gemini_key", "") ?: ""
        set(value) = prefs.edit().putString("code_gemini_key", value.trim()).apply()
    /** "day|model": the newest Gemini model for Code Studio, checked once a day. */
    var codeGeminiModelAuto: String
        get() = prefs.getString("code_gemini_model_auto", "") ?: ""
        set(value) = prefs.edit().putString("code_gemini_model_auto", value).apply()
    var codeGeminiModel: String
        get() = prefs.getString("code_gemini_model", "") ?: ""
        set(value) = prefs.edit().putString("code_gemini_model", value.trim()).apply()
    /** Most run → review → fix rounds per job. */
    var codeMaxRounds: Int
        get() = prefs.getInt("code_max_rounds", 6)
        set(value) = prefs.edit().putInt("code_max_rounds", value).apply()

    /** Code Studio types its code live in a floating 💻 window over any app. */
    var codeLiveWindow: Boolean
        get() = prefs.getBoolean("code_live_window", true)
        set(value) = prefs.edit().putBoolean("code_live_window", value).apply()
    /** "day|model" — the automatic pick, refreshed daily. */
    var researchModelAuto: String
        get() = prefs.getString("research_model_auto", "") ?: ""
        set(value) = prefs.edit().putString("research_model_auto", value).apply()
    var researchDepth: String
        get() = prefs.getString("research_depth", "DEEP") ?: "DEEP"
        set(value) = prefs.edit().putString("research_depth", value).apply()

    /** Weather heads-ups: morning summary, rain coming, very hot. */
    var isWeatherAlerts: Boolean
        get() = prefs.getBoolean("weather_alerts", true)
        set(value) = prefs.edit().putBoolean("weather_alerts", value).apply()
    /** Weather alerts already said (dayKey-type), so each is said once. */
    var weatherSaidKeys: String
        get() = prefs.getString("weather_said", "") ?: ""
        set(value) = prefs.edit().putString("weather_said", value).apply()
    /** Meal-time check-ins (four daily routines). */
    var isMealReminders: Boolean
        get() = prefs.getBoolean("meal_reminders", false)
        set(value) = prefs.edit().putBoolean("meal_reminders", value).apply()

    /** The user's own rules for her, one per line ("Faiza Rules"). */
    var userRules: String
        get() = prefs.getString("user_rules", "") ?: ""
        set(value) = prefs.edit().putString("user_rules", value).apply()

    /** The multi-task plan in progress (JSON), or null. */
    var taskPlanJson: String?
        get() = prefs.getString("task_plan", null)
        set(value) = prefs.edit().putString("task_plan", value).apply()

    // ====== Avatar (living picture instead of the orb) ======
    var isAvatarMode: Boolean
        get() = prefs.getBoolean("avatar_mode", false)
        set(value) = prefs.edit().putBoolean("avatar_mode", value).apply()
    /** "lx,ly,rx,ry,mx,my" in 0..1 of the picture. */
    var avatarFace: String?
        get() = prefs.getString("avatar_face", null)
        set(value) = prefs.edit().putString("avatar_face", value).apply()

    // ====== How she addresses the user ======
    /** "male", "female" or "" (not said). */
    var userGender: String
        get() = prefs.getString("user_gender", "") ?: ""
        set(value) = prefs.edit().putString("user_gender", value).apply()
    /** What she calls the user: "Boss", "Jaan", "Jaanu", "Sir", "Ma'am"… Empty = by their name. */
    var userAddress: String
        get() = prefs.getString("user_address", "") ?: ""
        set(value) = prefs.edit().putString("user_address", value.trim()).apply()

    // ====== Closeness (Secret settings) ======
    /** 0 Friendly, 1 Romantic, 2 Intense (18+). Also drives isGirlfriendMode for old code. */
    var closenessLevel: Int
        get() = prefs.getInt("closeness_level", if (prefs.getBoolean(KEY_GIRLFRIEND_MODE, false)) 1 else 0).coerceIn(0, 2)
        set(value) {
            prefs.edit().putInt("closeness_level", value.coerceIn(0, 2))
                .putBoolean(KEY_GIRLFRIEND_MODE, value >= 1).apply()
        }
    /** Birth year the user confirmed for Intense (0 = not given). */
    var adultBirthYear: Int
        get() = prefs.getInt("adult_birth_year", 0)
        set(value) = prefs.edit().putInt("adult_birth_year", value).apply()

    // ====== Life modes ======
    private fun str(key: String) = prefs.getString(key, null)
    private fun putStr(key: String, v: String?) = prefs.edit().putString(key, v).apply()

    var modeManualId: String?
        get() = str("mode_manual_id")
        set(value) = putStr("mode_manual_id", value)
    var modeManualUntil: Long
        get() = prefs.getLong("mode_manual_until", 0L)
        set(value) = prefs.edit().putLong("mode_manual_until", value).apply()
    /** The mode that was on at the last check (to notice it ending). */
    var modeLastActive: String?
        get() = str("mode_last_active")
        set(value) = putStr("mode_last_active", value)
    var modeStartedAt: Long
        get() = prefs.getLong("mode_started_at", 0L)
        set(value) = prefs.edit().putLong("mode_started_at", value).apply()
    var modeSettingsJson: String?
        get() = str("mode_settings")
        set(value) = putStr("mode_settings", value)
    var customModesJson: String?
        get() = str("custom_modes")
        set(value) = putStr("custom_modes", value)
    var modeOverridesJson: String?
        get() = str("mode_overrides")
        set(value) = putStr("mode_overrides", value)
    var modeCallsJson: String?
        get() = str("mode_calls")
        set(value) = putStr("mode_calls", value)
    var prayerTimesJson: String?
        get() = str("prayer_times")
        set(value) = putStr("prayer_times", value)
    var isCarConnected: Boolean
        get() = prefs.getBoolean("car_connected", false)
        set(value) = prefs.edit().putBoolean("car_connected", value).apply()

    // ====== Clear all ======
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
