import os
main = r'C:\Users\pc\Desktop\Faiza\maya-ai-assistant\android\app\src\main\java\com\faiza\ai'


def edit(p, a, b):
    p = os.path.join(main, *p.split('/'))
    s = open(p, encoding='utf-8').read()
    assert s.count(a) == 1, (p, a[:70], s.count(a))
    open(p, 'w', encoding='utf-8').write(s.replace(a, b))


edit('util/PrefsManager.kt', '''    /** The first-start setup wizard was finished or skipped. */''', '''    /** Gaming match results (JSON). */
    var gameStatsJson: String?
        get() = prefs.getString("game_stats", null)
        set(value) = prefs.edit().putString("game_stats", value).apply()

    /** The first-start setup wizard was finished or skipped. */''')

p = 'ai/game/GameMode.kt'
edit(p, '''    val GAMING_TOOLS = setOf("gaming_mode", "ff_info", "incoming_call", "window_music", "calculate")''',
     '''    val GAMING_TOOLS = setOf("gaming_mode", "game_result", "ff_info", "incoming_call", "window_music", "calculate")''')
edit(p, '''                ToolParam("action", ParamType.STRING, "on | off | guide | quiet | resume | talk | jokes | fix_echo | allow_interrupt | status | settings"),''',
     '''                ToolParam("action", ParamType.STRING, "on | off | guide | quiet | resume | talk | jokes | fix_echo | allow_interrupt | report | share | status | settings"),''')
edit(p, '''                "status" -> when {''', '''                "report" -> {
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
                "status" -> when {''')

p = 'ai/game/GameKnowledge.kt'
edit(p, '''        appendLine("Menus / lobby / loading / results: short loadout tips, match summary, hype — don't spam.")''',
     '''        appendLine("Menus / lobby / loading / results: short loadout tips, match summary, hype — don't spam.")
        appendLine("RESULTS / DEATH / BOOYAH SCREEN: call game_result ONCE for that match with the kills and rank shown — never during play.")''')

p = 'ai/FaizaAgent.kt'
edit(p, '''        if ("ff_info" !in disabled) skills += com.faiza.ai.ai.game.FreeFireData.skill()''',
     '''        if ("ff_info" !in disabled) skills += com.faiza.ai.ai.game.FreeFireData.skill()
        if ("game_result" !in disabled) skills += com.faiza.ai.ai.game.GameStats.skill()''')

# Leaving the game: the day's report.
p = 'service/FloatingOrbService.kt'
edit(p, '''            if (sayBye) live?.sendText("[GAME END] The user left the game. In ONE short line say gaming mode is off and well played.")
            delay(if (sayBye) 4500 else 3500)''', '''            val report = com.faiza.ai.ai.game.GameStats.endNote(gm.gameName.ifBlank { "the game" })
            if (sayBye) live?.sendText("[GAME END] The user left the game. Gaming mode is off. " +
                    (report ?: "In ONE short line say well played."))
            delay(if (sayBye) (if (report != null) 12_000 else 4500) else 3500)''')
print('ok')
