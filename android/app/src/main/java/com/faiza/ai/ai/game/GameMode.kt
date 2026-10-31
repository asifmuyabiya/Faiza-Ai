package com.faiza.ai.ai.game

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.faiza.ai.service.FloatingOrbService
import com.faiza.ai.ui.GamingActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Gaming mode (owner's idea): while a game is on screen Faiza watches it live and keeps talking — enemy calls, routes,
 * cover, loot, driving directions — plus Bengali banter. Starts by voice, by switch, or after she asks when a game opens;
 * switches itself off when the user leaves the game. The live loop itself runs in [FloatingOrbService].
 */
object GameMode {

    private val prefs get() = FaizaApp.instance.prefs

    /** Commentary running right now. */
    val active = MutableStateFlow(false)

    /** Switched on by hand / voice before a game is open: starts as soon as one appears. */
    @Volatile var armed = false

    /** The game's voice chat has the microphone: she can't hear the user right now, but keeps talking. */
    @Volatile var micTaken = false

    /** "চুপ করো" during a game: keep watching, stop the running commentary. */
    @Volatile var quiet = false

    @Volatile var gamePkg = ""
        private set
    @Volatile var gameName = ""
        private set

    /** Games she already asked about since the phone/app started (so she doesn't nag). */
    val asked = mutableSetOf<String>()

    fun isGame(ctx: Context, pkg: String): Boolean {
        if (pkg.isBlank() || pkg == ctx.packageName) return false
        if (GameKnowledge.KNOWN.containsKey(pkg)) return true
        val info = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        @Suppress("DEPRECATION")
        return info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    fun nameOf(ctx: Context, pkg: String): String = GameKnowledge.KNOWN[pkg]
        ?: runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("the game")

    fun begin(ctx: Context, pkg: String) {
        gamePkg = pkg
        gameName = nameOf(ctx, pkg)
        armed = false
        quiet = false
        active.value = true
    }

    fun end() {
        active.value = false
        armed = false
        quiet = false
    }

    val talk: GameKnowledge.Talk get() = GameKnowledge.Talk.from(prefs.gamingTalk)

    /** Chosen picture quality, or picked from the phone's memory (mid-range phones get the smooth one). */
    val quality: GameKnowledge.Quality
        get() = GameKnowledge.Quality.from(prefs.gamingQuality) ?: autoQuality

    private val autoQuality: GameKnowledge.Quality by lazy {
        val am = FaizaApp.instance.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        if (am.isLowRamDevice || mi.totalMem < 7L * 1024 * 1024 * 1024) GameKnowledge.Quality.SMOOTH else GameKnowledge.Quality.SHARP
    }

    fun autoQualityLabel(): String = autoQuality.label

    /** The only tools a player needs; fewer tools = faster answers. */
    val GAMING_TOOLS = setOf("gaming_mode", "game_result", "ff_info", "incoming_call", "window_music", "calculate")

    /** Short persona for game sessions (the full assistant prompt is long and slows every call). */
    fun compactPersona(name: String, language: String): String =
        "You are $name, the user's AI best friend living in their phone — warm, witty, quick, full of energy. " +
                "You were created by ${com.faiza.ai.ai.HeartLines.CREATOR} with great skill and love. " +
                "Reply in $language unless the user speaks another language. Keep every reply very short."

    fun address(): String = prefs.userAddress.trim().ifEmpty { "বস" }

    /** Added to the live system prompt while the mode is on. */
    fun promptAddon(): String? = if (!active.value) null else
        GameKnowledge.prompt(
            gameName, GameKnowledge.isFreeFire(gamePkg), talk, prefs.gamingJokes, address(),
            driving = GameKnowledge.isDriving(gameName, gamePkg)
        )

    fun startGreeting(): String =
        "[GAME START] Gaming mode is now ON for $gameName. In ONE short excited sentence tell the user you're watching " +
                "and ready (e.g. \"গেমিং মোড অন, ${address()}! আমি স্ক্রিনে চোখ রাখছি, চলেন শুরু করি!\"), then start coaching."

    fun offerLine(name: String): String =
        "[GAME DETECTED] The user just opened the game \"$name\". Ask in ONE short sentence whether to turn on gaming " +
                "mode, e.g. \"${address()}, $name খুললেন — গেমিং মোড অন করে দেব? আমি পাশে থেকে গাইড করব।\" If they say " +
                "yes, call gaming_mode with action=on. If they say no, just say okay and stay quiet."

    const val TICK = "[GAME TICK]"

    fun skills(): List<Skill> = listOf(
        Skill(
            "gaming_mode", "🎮", "Gaming mode", "\"গেমিং মোড অন করো\" · \"একটু গাইড করো\" · \"চুপ করো\"",
            "Gaming mode: Faiza watches the game on screen live and coaches non-stop (enemies, directions, cover, loot, " +
                    "driving, banter). action=on (start now, or as soon as a game opens) | off | guide (one quick look at " +
                    "the game and one tip, without turning the mode on) | quiet (stop commentary but keep watching) | " +
                    "resume | talk (level=chatty|normal|urgent) | jokes (value=on|off) | status | settings (open the screen).",
            listOf(
                ToolParam("action", ParamType.STRING, "on | off | guide | quiet | resume | talk | jokes | fix_echo | allow_interrupt | report | share | status | settings"),
                ToolParam("level", ParamType.STRING, "for talk: chatty | normal | urgent", required = false),
                ToolParam("value", ParamType.STRING, "for jokes: on | off", required = false)
            )
        ) { ctx, args ->
            val svc = FloatingOrbService.instance
            when (args.require("action").lowercase()) {
                "on", "start" -> {
                    if (!prefs.gamingEnabled) prefs.gamingEnabled = true
                    svc ?: return@Skill "ERROR: Faiza's floating service isn't running."
                    svc.requestGaming()
                }
                "off", "stop" -> { svc?.stopGaming(sayBye = false); armed = false; "Gaming mode is off. Say one short goodbye line." }
                "guide" -> svc?.gameGuideOnce() ?: "ERROR: service not running"
                "quiet" -> { quiet = true; "Quiet: answer game ticks with \".\" until the user says to talk again. Say \"ঠিক আছে\" only." }
                "resume" -> { quiet = false; "Commentary back on — keep coaching." }
                "talk" -> {
                    val t = GameKnowledge.Talk.from(args.str("level"))
                    prefs.gamingTalk = t.name
                    if (active.value) svc?.restartGamingSession()
                    "Talk level: ${t.label}."
                }
                "jokes" -> {
                    prefs.gamingJokes = !args.str("value").orEmpty().lowercase().let { it == "off" || it == "no" || it == "false" }
                    if (active.value) svc?.restartGamingSession()
                    "Jokes ${if (prefs.gamingJokes) "on" else "off"}."
                }
                "fix_echo", "echo", "stuck" -> {
                    prefs.gamingEchoGuard = true
                    if (active.value) svc?.restartGamingSession()
                    "Fixed: while you talk, the mic is now closed, so game sound and your own voice can't stop you. " +
                            "Tell the user in one short line (they can still talk to you between your lines)."
                }
                "allow_interrupt" -> {
                    prefs.gamingEchoGuard = false
                    if (active.value) svc?.restartGamingSession()
                    "The user can interrupt you mid-sentence now (best with headphones). Say it in one short line."
                }
                "report" -> {
                    val d = GameStats.day(GameStats.all(), java.time.LocalDate.now())
                    if (d.matches == 0) "No matches noted today yet." else GameStats.text(d, gameName.ifBlank { "Game" }) +
                            ". Say it like a fun commentator and add one tip."
                }
                "share" -> {
                    val d = GameStats.day(GameStats.all(), java.time.LocalDate.now())
                    if (d.matches == 0) "Nothing to share yet." else {
                        GameStats.share(ctx, d, gameName.ifBlank { "Game" }); "The share menu is open."
                    }
                }
                "status" -> when {
                    active.value -> "ON for $gameName · ${talk.label} · jokes ${if (prefs.gamingJokes) "on" else "off"}" +
                            (if (quiet) " · quiet" else "") + (if (micTaken) " · the game's voice chat has the mic (you can't hear the user now)" else "")
                    armed -> "Armed: starts when a game opens."
                    else -> "Off."
                }
                "settings" -> {
                    ctx.startActivity(Intent(ctx, GamingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Gaming settings are open."
                }
                else -> "ERROR: unknown action"
            }
        }
    )
}
