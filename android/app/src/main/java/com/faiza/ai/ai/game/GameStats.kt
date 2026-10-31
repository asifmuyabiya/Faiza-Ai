package com.faiza.ai.ai.game

import android.content.Context
import android.content.Intent
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.str
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/**
 * Match report (owner: "আজ ৪ ম্যাচ, ৯ কিল, ১ বুইয়া" — a fun summary players share). Faiza notes each match from the
 * results screen she sees; at the end of the session she reads the day out and offers to share it.
 */
object GameStats {

    data class Match(val at: Long, val game: String, val kills: Int, val placement: Int?, val booyah: Boolean, val damage: Int? = null)

    data class Day(val matches: Int, val kills: Int, val booyahs: Int, val best: Int?, val bestKills: Int, val avgKills: Double)

    private val prefs get() = FaizaApp.instance.prefs
    private val gson = Gson()

    fun all(): List<Match> = runCatching {
        gson.fromJson<List<Match>>(prefs.gameStatsJson ?: "[]", object : TypeToken<List<Match>>() {}.type)
    }.getOrNull()?.filter { it.game != null } ?: emptyList()

    fun add(m: Match) {
        // A results screen can be seen twice: same game within 90 s with the same numbers = one match.
        val list = all()
        val last = list.lastOrNull()
        if (last != null && last.game == m.game && m.at - last.at < 90_000 && last.kills == m.kills && last.placement == m.placement) return
        prefs.gameStatsJson = gson.toJson((list + m).takeLast(500))
    }

    /** Pure (tested). */
    fun day(matches: List<Match>, date: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Day {
        val today = matches.filter { java.time.Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == date }
        val kills = today.sumOf { it.kills }
        return Day(
            today.size, kills, today.count { it.booyah },
            today.mapNotNull { it.placement }.minOrNull(), today.maxOfOrNull { it.kills } ?: 0,
            if (today.isEmpty()) 0.0 else kills.toDouble() / today.size
        )
    }

    fun text(d: Day, game: String): String = buildString {
        append("🎮 আজ $game: ${d.matches} ম্যাচ · ${d.kills} কিল")
        if (d.booyahs > 0) append(" · ${d.booyahs} বার BOOYAH 🏆")
        d.best?.let { append(" · সেরা #$it") }
        append(" · এক ম্যাচে সর্বোচ্চ ${d.bestKills} কিল")
    }

    fun shareText(d: Day, game: String) = text(d, game) + "\nকোচ: Faiza AI 🔥 #FaizaAI"

    fun share(ctx: Context, d: Day, game: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(d, game))
        ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** What she's told at the end of a game session (null = no match noted). */
    fun endNote(game: String): String? {
        val d = day(all(), LocalDate.now())
        if (d.matches == 0) return null
        return "TODAY'S REPORT: ${text(d, game)}. Say it like a fun sports commentator in 2 short sentences, add ONE " +
                "concrete tip for tomorrow, and ask if they want to share the report (gaming_mode action=share)."
    }

    fun skill() = Skill(
        "game_result", "🏆", "Match result", "",
        "Gaming: call ONCE per match when you SEE the results / death / BOOYAH screen: kills (number shown), placement " +
                "(rank #, e.g. 3), booyah=true if they won. Don't call during fights.",
        listOf(
            ToolParam("kills", ParamType.INTEGER, "kills shown on the results screen"),
            ToolParam("placement", ParamType.INTEGER, "rank #, if shown", required = false),
            ToolParam("booyah", ParamType.BOOLEAN, "won the match", required = false),
            ToolParam("damage", ParamType.INTEGER, "damage, if shown", required = false)
        )
    ) { _, args ->
        val game = GameMode.gameName.ifBlank { args.str("game") ?: "Game" }
        val booyah = args.bool("booyah") || args.int("placement") == 1
        add(Match(System.currentTimeMillis(), game, (args.int("kills") ?: 0).coerceIn(0, 99), args.int("placement")?.coerceIn(1, 100), booyah, args.int("damage")))
        val d = day(all(), LocalDate.now())
        "Noted. Today so far: ${text(d, game)}. React in one short line (celebrate a booyah big; after a loss comfort + one tip)."
    }
}
