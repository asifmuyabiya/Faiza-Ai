package com.faiza.ai.ai.memory

import com.faiza.ai.ai.macros.Macro
import com.faiza.ai.ai.macros.MacroStore
import com.faiza.ai.ai.contacts.FavoriteContact
import com.faiza.ai.ai.contacts.FavoriteContacts
import com.faiza.ai.ai.routines.Routines
import com.faiza.ai.ai.routines.ScheduledTask
import com.faiza.ai.ai.routines.Watch
import com.faiza.ai.ai.skills.CustomSkill
import com.faiza.ai.ai.skills.SubAgent
import com.faiza.ai.ai.skills.UserSkillStore
import com.faiza.ai.util.PrefsManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser

/**
 * Backup & restore (video 18:26): memories, conversation history, profile and assistant settings,
 * custom routines and sub-agents. API keys and the license are deliberately NOT included.
 */
object BackupManager {

    private const val APP_ID = "faiza-ai"
    private const val VERSION = 1

    data class Profile(
        val userName: String? = null,
        val assistantName: String? = null,
        val favoriteMusicApp: String? = null,
        val favoriteSong: String? = null,
        val youtubeHandle: String? = null,
        val persona: String? = null,
        val language: String? = null,
        val girlfriendMode: Boolean? = null,
        val memoryEnabled: Boolean? = null,
        val incognito: Boolean? = null,
        val countryDialCode: String? = null,
        val preferredSim: Int? = null
    )

    data class Backup(
        val app: String = APP_ID,
        val version: Int = VERSION,
        val createdAt: Long = System.currentTimeMillis(),
        val profile: Profile = Profile(),
        val memory: MemoryData = MemoryData(),
        val customSkills: List<CustomSkill>? = null,
        val subAgents: List<SubAgent>? = null,
        val macros: List<Macro>? = null,
        /** Favourite & SOS contacts (MAYA backs these up too). */
        val contacts: List<FavoriteContact>? = null,
        val scheduledTasks: List<ScheduledTask>? = null,
        val watches: List<Watch>? = null,
        /** The money book (খাতা). */
        val khata: List<com.faiza.ai.ai.khata.Khata.Entry>? = null,
        /** Medicine courses (their reminders are in [scheduledTasks]). */
        val medicineJson: String? = null,
        val gameStatsJson: String? = null,
        val extras: Extras? = null
    )

    /** Settings added later: prayer, gaming, driving, calls. All optional so old backups still load. */
    data class Extras(
        val userAddress: String? = null,
        val busyReplyText: String? = null,
        val prayerMethod: String? = null,
        val prayerHanafi: Boolean? = null,
        val hijriAdjust: Int? = null,
        val azanReminders: Boolean? = null,
        val azanBeforeMin: Int? = null,
        val ramadanReminders: Boolean? = null,
        val prayerLat: Float? = null,
        val prayerLng: Float? = null,
        val gamingTalk: String? = null,
        val gamingJokes: Boolean? = null,
        val gamingAsk: Boolean? = null,
        val gamingAutoStart: Boolean? = null,
        val gamingEchoGuard: Boolean? = null,
        val homeAddress: String? = null,
        val workAddress: String? = null,
        val drivingSpeedLimit: Int? = null,
        val smsMoneyLog: Boolean? = null
    )

    data class Summary(val facts: Int, val lines: Int, val contacts: Int, val createdAt: Long)

    class InvalidBackupException(message: String) : Exception(message)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun export(prefs: PrefsManager): String = gson.toJson(
        Backup(
            profile = Profile(
                userName = prefs.userName,
                assistantName = prefs.assistantNameSetting,
                favoriteMusicApp = prefs.favoriteMusicApp,
                favoriteSong = prefs.favoriteSong,
                youtubeHandle = prefs.youtubeHandle,
                persona = prefs.selectedPersona,
                language = prefs.language,
                girlfriendMode = prefs.isGirlfriendMode,
                memoryEnabled = prefs.isMemoryEnabled,
                incognito = prefs.isIncognito,
                countryDialCode = prefs.countryDialCode,
                preferredSim = prefs.preferredSim
            ),
            memory = MemoryStore.export(),
            customSkills = UserSkillStore.customSkills(prefs),
            subAgents = UserSkillStore.subAgents(prefs),
            macros = MacroStore.load { prefs.macrosJson },
            contacts = FavoriteContacts.load(prefs),
            scheduledTasks = Routines.loadTasks { prefs.routinesJson },
            watches = Routines.loadWatches { prefs.watchesJson },
            khata = runCatching { com.faiza.ai.ai.khata.Khata.all(com.faiza.ai.FaizaApp.instance) }.getOrNull(),
            medicineJson = prefs.medicineJson,
            gameStatsJson = prefs.gameStatsJson,
            extras = Extras(
                userAddress = prefs.userAddress, busyReplyText = prefs.busyReplyText,
                prayerMethod = prefs.prayerMethod, prayerHanafi = prefs.prayerHanafi, hijriAdjust = prefs.hijriAdjust,
                azanReminders = prefs.azanReminders, azanBeforeMin = prefs.azanBeforeMin, ramadanReminders = prefs.ramadanReminders,
                prayerLat = prefs.prayerLat, prayerLng = prefs.prayerLng,
                gamingTalk = prefs.gamingTalk, gamingJokes = prefs.gamingJokes, gamingAsk = prefs.gamingAsk,
                gamingAutoStart = prefs.gamingAutoStart, gamingEchoGuard = prefs.gamingEchoGuard,
                homeAddress = prefs.homeAddress, workAddress = prefs.workAddress, drivingSpeedLimit = prefs.drivingSpeedLimit,
                smsMoneyLog = prefs.smsMoneyLog
            )
        )
    )

    /** Checks the file without changing anything. */
    fun read(json: String): Backup {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: throw InvalidBackupException("This file isn't a Faiza AI backup.")
        if (root.get("app")?.asString != APP_ID) throw InvalidBackupException("This file isn't a Faiza AI backup.")
        val version = root.get("version")?.asInt ?: 0
        if (version > VERSION) throw InvalidBackupException("This backup is from a newer Faiza AI. Update the app first.")
        val parsed = runCatching { gson.fromJson(root, Backup::class.java) }.getOrNull()
            ?: throw InvalidBackupException("The backup file is damaged.")
        return sanitize(parsed)
    }

    /** Gson ignores Kotlin null-safety: rebuild with safe defaults for any missing section. */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    private fun sanitize(b: Backup): Backup {
        val memory = b.memory ?: MemoryData()
        val facts = (memory.facts ?: emptyList()).filter { it != null && it.text != null && it.text.isNotBlank() }
            .map { MemoryFact(id = it.id ?: java.util.UUID.randomUUID().toString(), text = it.text, createdAt = it.createdAt) }
        val log = (memory.log ?: emptyList()).filter { it != null && it.text != null && it.who != null && it.text.isNotBlank() }
        return b.copy(
            profile = b.profile ?: Profile(),
            memory = MemoryData(facts, log),
            customSkills = b.customSkills?.filter { it != null && it.name != null && it.trigger != null && it.instructions != null },
            subAgents = b.subAgents?.filter { it != null && it.name != null && it.instructions != null },
            macros = b.macros?.let { MacroStore.sanitize(it) },
            contacts = b.contacts?.filter { it != null && it.name != null && it.number != null && it.number.any(Char::isDigit) },
            khata = b.khata?.filter { it != null && it.type != null && it.amount > 0 && it.id != null }
        )
    }

    fun summary(b: Backup) = Summary(b.memory.facts.size, b.memory.log.size, b.contacts?.size ?: 0, b.createdAt)

    /** What a backup made now would contain (for the Backup screen). */
    fun currentCounts(prefs: PrefsManager) = Summary(MemoryStore.export().facts.size, MemoryStore.logSize(), FavoriteContacts.load(prefs).size, System.currentTimeMillis())

    /** Replaces memories and settings with the backup's contents. */
    fun restore(b: Backup, prefs: PrefsManager) {
        val p = b.profile
        p.userName?.let { prefs.userName = it }
        p.assistantName?.let { prefs.assistantNameSetting = it }
        p.favoriteMusicApp?.takeIf { it in setOf("youtube", "youtube_music", "spotify") }?.let { prefs.favoriteMusicApp = it }
        p.favoriteSong?.let { prefs.favoriteSong = it }
        p.youtubeHandle?.let { prefs.youtubeHandle = it }
        p.persona?.let { prefs.selectedPersona = it }
        p.language?.let { prefs.language = it }
        p.girlfriendMode?.let { prefs.isGirlfriendMode = it }
        p.memoryEnabled?.let { prefs.isMemoryEnabled = it }
        p.incognito?.let { prefs.isIncognito = it }

        MemoryStore.replaceAll(b.memory)
        b.customSkills?.let { UserSkillStore.saveCustomSkills(prefs, it) }
        b.subAgents?.let { UserSkillStore.saveSubAgents(prefs, it) }
        b.macros?.let { list -> MacroStore.save({ prefs.macrosJson = it }, list) }
        p.countryDialCode?.let { prefs.countryDialCode = it }
        p.preferredSim?.takeIf { it in 0..2 }?.let { prefs.preferredSim = it }
        b.contacts?.let { FavoriteContacts.save(prefs, it) }
        b.scheduledTasks?.let { list -> Routines.saveTasks({ prefs.routinesJson = it }, list) }
        b.watches?.let { list -> Routines.saveWatches({ prefs.watchesJson = it }, list) }
        b.khata?.let { runCatching { com.faiza.ai.ai.khata.Khata.replaceAll(com.faiza.ai.FaizaApp.instance, it) } }
        b.medicineJson?.let { prefs.medicineJson = it }
        b.gameStatsJson?.let { prefs.gameStatsJson = it }
        b.extras?.let { x ->
            x.userAddress?.let { prefs.userAddress = it }
            x.busyReplyText?.let { prefs.busyReplyText = it }
            x.prayerMethod?.let { prefs.prayerMethod = it }
            x.prayerHanafi?.let { prefs.prayerHanafi = it }
            x.hijriAdjust?.takeIf { it in -2..2 }?.let { prefs.hijriAdjust = it }
            x.azanReminders?.let { prefs.azanReminders = it }
            x.azanBeforeMin?.takeIf { it in 0..30 }?.let { prefs.azanBeforeMin = it }
            x.ramadanReminders?.let { prefs.ramadanReminders = it }
            x.prayerLat?.let { prefs.prayerLat = it }
            x.prayerLng?.let { prefs.prayerLng = it }
            x.gamingTalk?.let { prefs.gamingTalk = it }
            x.gamingJokes?.let { prefs.gamingJokes = it }
            x.gamingAsk?.let { prefs.gamingAsk = it }
            x.gamingAutoStart?.let { prefs.gamingAutoStart = it }
            x.gamingEchoGuard?.let { prefs.gamingEchoGuard = it }
            x.homeAddress?.let { prefs.homeAddress = it }
            x.workAddress?.let { prefs.workAddress = it }
            x.drivingSpeedLimit?.takeIf { it in 0..200 }?.let { prefs.drivingSpeedLimit = it }
            x.smsMoneyLog?.let { prefs.smsMoneyLog = it }
        }
        runCatching { com.faiza.ai.ai.prayer.PrayerCenter.schedule(com.faiza.ai.FaizaApp.instance) }
        // Restored alarms must be set again.
        runCatching { com.faiza.ai.ai.routines.RoutineCenter.rescheduleAll(com.faiza.ai.FaizaApp.instance) }
    }
}
