package com.faiza.ai.ai.khata

import android.content.Context
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import com.faiza.ai.ai.skills.Skill
import com.faiza.ai.ai.skills.require
import com.faiza.ai.ai.skills.str
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * The voice money book (owner: "করিমের কাছে ৫০০ টাকা বাকি", "আজ বাজারে ৮৫০ খরচ" — মাস শেষে মোট হিসাব).
 * Expenses, income, and who owes whom. Kept in a file on the phone; Faiza never moves money.
 */
object Khata {

    enum class Type(val bn: String) {
        EXPENSE("খরচ"), INCOME("আয়"),
        GAVE("বাকি দিলাম (সে আমাকে দেবে)"), GOT_BACK("বাকি ফেরত পেলাম"),
        TOOK("ধার নিলাম (আমি দেব)"), PAID_BACK("ধার শোধ করলাম");

        companion object {
            fun from(s: String?): Type? = values().firstOrNull { it.name.equals(s?.trim(), true) } ?: when (s?.trim()?.lowercase()) {
                "spend", "spent", "cost", "খরচ" -> EXPENSE
                "earn", "earned", "salary", "আয়" -> INCOME
                "lend", "lent", "credit", "baki", "বাকি" -> GAVE
                "received", "repaid_to_me", "got" -> GOT_BACK
                "borrow", "borrowed", "loan", "ধার" -> TOOK
                "repay", "repaid", "paid" -> PAID_BACK
                else -> null
            }
        }
    }

    data class Entry(
        val id: String = UUID.randomUUID().toString(),
        val at: Long = System.currentTimeMillis(),
        val type: Type,
        val amount: Double,
        val person: String = "",
        val note: String = "",
        val source: String = "voice" // voice | hand | sms
    )

    data class Balance(val person: String, val owesMe: Double, val iOwe: Double) {
        val net get() = owesMe - iOwe
    }

    // ---------------- Pure maths (tested) ----------------

    fun balances(entries: List<Entry>): List<Balance> = entries.filter { it.person.isNotBlank() }
        .groupBy { it.person.trim().lowercase() }
        .map { (_, list) ->
            val name = list.last().person.trim()
            val owes = list.sumOf { when (it.type) { Type.GAVE -> it.amount; Type.GOT_BACK -> -it.amount; else -> 0.0 } }
            val iOwe = list.sumOf { when (it.type) { Type.TOOK -> it.amount; Type.PAID_BACK -> -it.amount; else -> 0.0 } }
            Balance(name, owes, iOwe)
        }
        .filter { kotlin.math.abs(it.owesMe) > 0.004 || kotlin.math.abs(it.iOwe) > 0.004 }
        .sortedByDescending { kotlin.math.abs(it.net) }

    data class Period(val from: Long, val to: Long, val label: String)

    fun period(which: String?, now: Long = System.currentTimeMillis()): Period {
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return when (which?.lowercase()) {
            "today", "আজ" -> Period(c.timeInMillis, now + 1, "আজ")
            "yesterday", "গতকাল" -> { val end = c.timeInMillis; c.add(Calendar.DAY_OF_MONTH, -1); Period(c.timeInMillis, end, "গতকাল") }
            "week", "সপ্তাহ" -> { c.add(Calendar.DAY_OF_MONTH, -6); Period(c.timeInMillis, now + 1, "গত ৭ দিন") }
            "last_month", "গত মাস" -> {
                c.set(Calendar.DAY_OF_MONTH, 1); val end = c.timeInMillis; c.add(Calendar.MONTH, -1); Period(c.timeInMillis, end, "গত মাস")
            }
            "all", "সব" -> Period(0, now + 1, "সব সময়")
            else -> { c.set(Calendar.DAY_OF_MONTH, 1); Period(c.timeInMillis, now + 1, "এই মাস") }
        }
    }

    data class Summary(val spent: Double, val earned: Double, val byNote: List<Pair<String, Double>>, val count: Int)

    fun summary(entries: List<Entry>, p: Period): Summary {
        val inP = entries.filter { it.at in p.from until p.to }
        val spent = inP.filter { it.type == Type.EXPENSE }
        return Summary(
            spent.sumOf { it.amount },
            inP.filter { it.type == Type.INCOME }.sumOf { it.amount },
            spent.groupBy { category(it.note) }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second },
            inP.size
        )
    }

    /** "বাজারে মাছ" → "বাজার"; first meaningful word, so similar notes add up. */
    fun category(note: String): String {
        val n = note.trim().lowercase()
        if (n.isEmpty()) return "অন্যান্য"
        CATEGORIES.forEach { (cat, words) -> if (words.any { n.contains(it) }) return cat }
        return note.trim().split(Regex("\\s+")).first().replace(Regex("(ে|য়|এ|তে|র)$"), "").ifEmpty { "অন্যান্য" }
    }

    private val CATEGORIES = listOf(
        "বাজার" to listOf("বাজার", "bazar", "market", "মাছ", "সবজি", "চাল", "grocery"),
        "যাতায়াত" to listOf("রিকশা", "রিক্সা", "বাস", "সিএনজি", "cng", "uber", "pathao", "ভাড়া", "transport", "fare"),
        "খাবার" to listOf("খাবার", "রেস্টুরেন্ট", "নাস্তা", "food", "restaurant", "চা"),
        "মোবাইল/রিচার্জ" to listOf("রিচার্জ", "recharge", "mobile", "ইন্টারনেট", "internet", "data"),
        "বিল" to listOf("বিল", "bill", "বিদ্যুৎ", "গ্যাস", "পানি", "electric", "wifi"),
        "চিকিৎসা" to listOf("ওষুধ", "ডাক্তার", "medicine", "doctor", "hospital"),
        "কেনাকাটা" to listOf("কাপড়", "জামা", "shopping", "জুতা")
    )

    fun taka(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        val whole = r.toLong()
        val s = String.format(java.util.Locale.US, "%,d", whole)
        val frac = Math.round((r - whole) * 100)
        return "৳" + s + if (frac != 0L) ".%02d".format(frac) else ""
    }

    // ---------------- Storage ----------------

    private val gson = Gson()
    private fun file(ctx: Context) = File(ctx.filesDir, "khata.json")

    @Synchronized fun all(ctx: Context): List<Entry> = runCatching {
        gson.fromJson<List<Entry>>(file(ctx).readText(), object : TypeToken<List<Entry>>() {}.type)
    }.getOrNull()?.filter { it.type != null } ?: emptyList()

    @Synchronized private fun save(ctx: Context, list: List<Entry>) = file(ctx).writeText(gson.toJson(list))

    fun add(ctx: Context, e: Entry) = save(ctx, all(ctx) + e)
    /** Restore from a backup. */
    fun replaceAll(ctx: Context, list: List<Entry>) = save(ctx, list)
    fun delete(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.id == id })
    fun undo(ctx: Context): Entry? { val l = all(ctx); val last = l.lastOrNull() ?: return null; save(ctx, l.dropLast(1)); return last }

    /** Duplicate SMS (same amount, same person, within a minute) is skipped. */
    fun addIfNew(ctx: Context, e: Entry): Boolean {
        val l = all(ctx)
        if (l.any { it.source == e.source && it.amount == e.amount && it.person == e.person && kotlin.math.abs(it.at - e.at) < 60_000 }) return false
        save(ctx, l + e); return true
    }

    fun csv(ctx: Context): String {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        return "date,type,amount,person,note,source\n" + all(ctx).joinToString("\n") {
            listOf(f.format(java.util.Date(it.at)), it.type.name, it.amount.toString(), it.person, it.note, it.source)
                .joinToString(",") { v -> "\"" + v.replace("\"", "\"\"") + "\"" }
        }
    }

    // ---------------- Voice tool ----------------

    fun report(ctx: Context, which: String?, person: String?): String {
        val entries = all(ctx)
        if (!person.isNullOrBlank()) {
            val b = balances(entries).firstOrNull { it.person.equals(person.trim(), true) || it.person.contains(person.trim(), true) }
                ?: return "No open balance with \"$person\"."
            return "${b.person}: owes the user ${taka(b.owesMe)}; the user owes them ${taka(b.iOwe)}. Net: " +
                    (if (b.net >= 0) "${b.person} will give ${taka(b.net)}" else "the user must give ${taka(-b.net)}")
        }
        val p = period(which)
        val s = summary(entries, p)
        val bal = balances(entries)
        return buildString {
            appendLine("${p.label}: spent ${taka(s.spent)}, income ${taka(s.earned)} (${s.count} entries).")
            if (s.byNote.isNotEmpty()) appendLine("Spending by kind: " + s.byNote.take(6).joinToString(", ") { "${it.first} ${taka(it.second)}" })
            val owed = bal.filter { it.net > 0 }
            val owe = bal.filter { it.net < 0 }
            if (owed.isNotEmpty()) appendLine("People who owe the user: " + owed.take(8).joinToString(", ") { "${it.person} ${taka(it.net)}" } +
                    " — total ${taka(owed.sumOf { it.net })}")
            if (owe.isNotEmpty()) appendLine("The user owes: " + owe.take(8).joinToString(", ") { "${it.person} ${taka(-it.net)}" })
        }.trim()
    }

    fun skill() = Skill(
        "khata", "📒", "Money book (খাতা)", "\"করিম ৫০০ টাকা বাকি নিল\" · \"আজ বাজারে ৮৫০ খরচ\" · \"এই মাসে কত খরচ?\"",
        "The user's money book (expenses, income, who owes whom). action=add | report | undo | open.\n" +
                "add: type = EXPENSE (spent: \"বাজারে ৮৫০ খরচ\"), INCOME (\"বেতন পেলাম ২০ হাজার\"), GAVE (gave on credit / " +
                "they owe the user: \"করিম ৫০০ টাকা বাকি নিল\", \"করিমের কাছে ৫০০ পাই\"), GOT_BACK (\"করিম ২০০ দিয়ে গেল\"), " +
                "TOOK (user borrowed: \"রহিমের থেকে ১০০০ ধার নিলাম\"), PAID_BACK (\"রহিমকে ৫০০ শোধ দিলাম\"). amount as a " +
                "number (convert Bangla words: দেড় হাজার=1500). person for GAVE/GOT_BACK/TOOK/PAID_BACK. note = what for.\n" +
                "report: which = today | yesterday | week | month | last_month | all; or person = one person's balance.\n" +
                "After add, confirm in one short line with the new balance if a person is involved.",
        listOf(
            ToolParam("action", ParamType.STRING, "add | report | undo | open"),
            ToolParam("type", ParamType.STRING, "EXPENSE | INCOME | GAVE | GOT_BACK | TOOK | PAID_BACK", required = false),
            ToolParam("amount", ParamType.NUMBER, "amount in taka", required = false),
            ToolParam("person", ParamType.STRING, "who (for lending/borrowing)", required = false),
            ToolParam("note", ParamType.STRING, "what it was for", required = false),
            ToolParam("which", ParamType.STRING, "period for report", required = false)
        )
    ) { ctx, args ->
        when (args.require("action").lowercase()) {
            "add" -> {
                val type = Type.from(args.str("type")) ?: return@Skill "ERROR: which type? EXPENSE, INCOME, GAVE, GOT_BACK, TOOK or PAID_BACK"
                val amount = args.str("amount")?.let { com.faiza.ai.ai.skills.Calculator.normalize(it).toDoubleOrNull() }
                    ?: return@Skill "ERROR: how much?"
                if (amount <= 0) return@Skill "ERROR: the amount must be more than 0"
                val person = args.str("person").orEmpty()
                if (type in setOf(Type.GAVE, Type.GOT_BACK, Type.TOOK, Type.PAID_BACK) && person.isBlank())
                    return@Skill "ERROR: who? (lending/borrowing needs a person)"
                add(ctx, Entry(type = type, amount = amount, person = person, note = args.str("note").orEmpty()))
                "SAVED: ${type.bn} ${taka(amount)}" + (if (person.isNotBlank()) " — ${report(ctx, null, person)}" else "") +
                        (if (type == Type.EXPENSE) " · ${report(ctx, "today", null).lineSequence().first()}" else "")
            }
            "undo" -> undo(ctx)?.let { "Removed the last entry: ${it.type.bn} ${taka(it.amount)} ${it.person}" } ?: "The book is empty."
            "open" -> {
                ctx.startActivity(android.content.Intent(ctx, com.faiza.ai.ui.KhataActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                "The money book is open."
            }
            else -> report(ctx, args.str("which"), args.str("person"))
        }
    }
}
