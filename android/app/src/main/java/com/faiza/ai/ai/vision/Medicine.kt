package com.faiza.ai.ai.vision

import android.content.Context
import com.faiza.ai.FaizaApp
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.routines.Repeat
import com.faiza.ai.ai.routines.RoutineCenter
import com.faiza.ai.ai.routines.ScheduledTask
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.bool
import com.faiza.ai.ai.skills.int
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/**
 * 💊 Medicine reminders (owner: "প্রেসক্রিপশনের ছবি তুললে ওষুধের নাম আর সময় পড়ে রিমাইন্ডার"). Reads the
 * prescription, reads the list back for the user to confirm, then sets daily reminders (they stop when the course ends).
 */
object Medicine {

    data class Med(val name: String, val dose: String = "", val pattern: String = "1+0+1", val timing: String = "", val days: Int? = null)

    data class Course(val med: Med, val taskIds: List<String>, val endEpochDay: Long?)

    enum class Slot(val bn: String, val hour: Int, val minute: Int) {
        MORNING("সকাল", 8, 0), NOON("দুপুর", 14, 0), EVENING("বিকাল", 18, 0), NIGHT("রাত", 21, 30)
    }

    private val prefs get() = FaizaApp.instance.prefs
    private val gson = Gson()

    // ---------------- Pure parts (tested) ----------------

    /** "1+0+1" → morning & night; "1+1+1" → morning, noon, night; "0+0+1" → night; "1+1+1+1" → four times. */
    fun slots(pattern: String): List<Slot> {
        val bn = pattern.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")
        val parts = bn.split('+', '-', '–', ' ', '/').filter { it.isNotBlank() }.map { it.trim().toDoubleOrNull() ?: 0.0 }
        val order = when (parts.size) {
            4 -> listOf(Slot.MORNING, Slot.NOON, Slot.EVENING, Slot.NIGHT)
            3 -> listOf(Slot.MORNING, Slot.NOON, Slot.NIGHT)
            2 -> listOf(Slot.MORNING, Slot.NIGHT)
            1 -> listOf(Slot.NIGHT)
            else -> return listOf(Slot.MORNING)
        }
        return order.filterIndexed { i, _ -> parts[i] > 0 }.ifEmpty { listOf(Slot.MORNING) }
    }

    /** Before food → 30 minutes earlier. */
    fun timeFor(slot: Slot, timing: String): Pair<Int, Int> {
        val before = Regex("(?i)before|খাওয়ার আগে|খাবার আগে|আগে|empty stomach|খালি পেটে").containsMatchIn(timing)
        val total = slot.hour * 60 + slot.minute - if (before) 30 else 0
        return total / 60 to total % 60
    }

    /** The model's JSON (possibly wrapped in ``` or text) → medicines. */
    fun parse(reply: String): List<Med> {
        val json = reply.substringAfter("[", "").substringBeforeLast("]", "").let { if (it.isBlank()) return emptyList() else "[$it]" }
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                val name = o.get("name")?.asString?.trim().orEmpty()
                if (name.isEmpty()) null else Med(
                    name = name,
                    dose = o.get("dose")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    pattern = o.get("pattern")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { null } ?: "1+0+1",
                    timing = o.get("timing")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    days = o.get("days")?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }?.takeIf { it in 1..365 }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun describe(m: Med): String = "${m.name}" + (if (m.dose.isNotBlank()) " — ${m.dose}" else "") +
            " · ${m.pattern} (${slots(m.pattern).joinToString(", ") { it.bn }})" +
            (if (m.timing.isNotBlank()) " · ${m.timing}" else "") +
            (m.days?.let { " · $it দিন" } ?: " · চলবে")

    fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9\\u0980-\\u09FF]+"), "-").trim('-').take(30)

    // ---------------- Storage & reminders ----------------

    private fun courses(): List<Course> = runCatching {
        gson.fromJson<List<Course>>(prefs.medicineJson ?: "[]", object : TypeToken<List<Course>>() {}.type)
    }.getOrNull() ?: emptyList()

    private fun saveCourses(list: List<Course>) { prefs.medicineJson = gson.toJson(list) }

    private var pending: List<Med> = emptyList()

    fun start(ctx: Context, m: Med): Course {
        stop(ctx, m.name)
        val ids = slots(m.pattern).map { s ->
            val (h, min) = timeFor(s, m.timing)
            val id = "med-${slug(m.name)}-${s.name.lowercase()}"
            RoutineCenter.upsertTask(ctx, ScheduledTask(
                id = id, label = "💊 ${m.name} (${s.bn})",
                instruction = "Medicine reminder: it's time for ${m.name}" + (if (m.dose.isNotBlank()) ", ${m.dose}" else "") +
                        (if (m.timing.isNotBlank()) ", ${m.timing}" else "") + ". Remind the user warmly in one short " +
                        "sentence in their language and ask if they've taken it.",
                hour = h, minute = min, repeat = Repeat.DAILY
            ))
            id
        }
        val end = m.days?.let { LocalDate.now().toEpochDay() + it }
        val c = Course(m, ids, end)
        saveCourses(courses().filterNot { it.med.name.equals(m.name, true) } + c)
        return c
    }

    fun stop(ctx: Context, name: String): Boolean {
        val all = courses()
        val hit = all.filter { it.med.name.equals(name, true) || it.med.name.contains(name, true) }
        hit.forEach { c -> c.taskIds.forEach { RoutineCenter.deleteTask(ctx, it) } }
        if (hit.isNotEmpty()) saveCourses(all - hit.toSet())
        return hit.isNotEmpty()
    }

    /** Called before a reminder runs: a finished course removes its reminders instead. */
    fun expired(ctx: Context, taskId: String): Boolean {
        if (!taskId.startsWith("med-")) return false
        val c = courses().firstOrNull { taskId in it.taskIds } ?: return false
        val end = c.endEpochDay ?: return false
        if (LocalDate.now().toEpochDay() < end) return false
        stop(ctx, c.med.name)
        return true
    }

    fun list(): String = courses().joinToString("\n") { c ->
        describe(c.med) + (c.endEpochDay?.let { " · শেষ ${LocalDate.ofEpochDay(it)}" } ?: "")
    }.ifEmpty { "No medicine reminders." }

    private const val READER =
        "You read doctors' prescriptions from Bangladesh (English/Bangla, handwritten or printed). List ONLY the " +
                "medicines. For each: name (brand + strength as written, e.g. \"Napa 500mg\", \"Seclo 20\"), dose " +
                "(e.g. \"1 tablet\", \"2 চামচ\"), pattern as morning+noon+night (\"1+0+1\"; 4 parts if four times; " +
                "\"1+1+1\" for every 8 hours), timing (\"খাওয়ার আগে\"/\"খাওয়ার পরে\"/\"before food\"/\"after food\" or empty), " +
                "days (number, null if continue / not written). Reply with ONLY a JSON array: " +
                "[{\"name\":\"\",\"dose\":\"\",\"pattern\":\"\",\"timing\":\"\",\"days\":null}]. If a word is unreadable, " +
                "write your best reading and add \"?\" to the name. Don't invent medicines."

    fun skill() = Skill(
        "medicine", "💊", "Medicine reminders", "\"প্রেসক্রিপশনটা দেখে ওষুধের রিমাইন্ডার দাও\" · \"নাপা বন্ধ করো\"",
        "Medicine reminders. action=scan (photo of the prescription → reads the medicines; then READ THE LIST BACK and " +
                "ask the user to confirm, and ONLY after a clear yes call action=save) | save (confirmed=true: sets the " +
                "reminders for the list just scanned) | add (one medicine by voice: name, pattern like 1+0+1, timing, " +
                "days) | list | stop (name). Always remind the user to follow their doctor; you only set reminders.",
        listOf(
            ToolParam("action", ParamType.STRING, "scan | save | add | list | stop"),
            ToolParam("source", ParamType.STRING, "for scan: camera | gallery | screen", required = false),
            ToolParam("confirmed", ParamType.BOOLEAN, "for save: the user said yes", required = false),
            ToolParam("name", ParamType.STRING, "medicine name (add / stop)", required = false),
            ToolParam("pattern", ParamType.STRING, "e.g. 1+0+1 (add)", required = false),
            ToolParam("timing", ParamType.STRING, "before food / after food (add)", required = false),
            ToolParam("days", ParamType.INTEGER, "how many days (add); empty = continue", required = false)
        )
    ) { ctx, args ->
        when (args.require("action").lowercase()) {
            "scan" -> {
                val shot = PhotoAsk.get(ctx, args.str("source"))
                if (shot is PhotoAsk.Shot.Fail) return@Skill "ERROR: ${shot.why}"
                val meds = parse(PhotoAsk.ask(READER, "Read the medicines in this prescription.", (shot as PhotoAsk.Shot.Ok).jpegB64))
                if (meds.isEmpty()) return@Skill "Couldn't read any medicine — ask for a clearer, well-lit photo of the prescription."
                pending = meds
                "READ ${meds.size} medicine(s):\n" + meds.mapIndexed { i, m -> "${i + 1}. ${describe(m)}" }.joinToString("\n") +
                        "\nRead this list back to the user, ask if it's right (and to fix any wrong name/time), then call save with confirmed=true."
            }
            "save" -> {
                if (!args.bool("confirmed")) return@Skill "NOT SAVED: ask the user to confirm the list first."
                if (pending.isEmpty()) return@Skill "Nothing to save — scan the prescription first."
                val made = pending.map { start(ctx, it) }
                pending = emptyList()
                "SAVED reminders: " + made.joinToString("; ") { c -> c.med.name + " " + slots(c.med.pattern).joinToString("/") { s ->
                    val (h, m) = timeFor(s, c.med.timing); "%d:%02d".format(h, m) } } + ". They're in Routines too."
            }
            "add" -> {
                val m = Med(args.require("name"), "", args.str("pattern") ?: "1+0+1", args.str("timing").orEmpty(), args.int("days"))
                start(ctx, m)
                "SAVED: ${describe(m)}"
            }
            "stop" -> if (stop(ctx, args.require("name"))) "Stopped the reminders for ${args.str("name")}." else "No reminder called \"${args.str("name")}\"."
            else -> list()
        }
    }
}
