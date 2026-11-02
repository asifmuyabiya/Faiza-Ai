package com.asif.familyhisab.data

import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Export / import. JSON = full backup (restorable). CSV = opens in Excel / Google Sheets. */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun toJson(d: AppData): String = json.encodeToString(AppData.serializer(), d)

    fun fromJson(text: String): AppData = json.decodeFromString(AppData.serializer(), text.trim().removePrefix("﻿"))

    private fun money(p: Poisha): String = if (p % 100 == 0L) (p / 100).toString() else "%.2f".format(p / 100.0)

    private fun cell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** One row per entry. Starts with a UTF-8 BOM so Excel shows Bangla correctly. */
    fun toCsv(d: AppData, txns: List<Txn>): String = buildString {
        append('﻿')
        append("তারিখ,ধরন,ক্যাটাগরি,কার জন্য,টাকা,মাধ্যম,নোট,বাজারের আইটেম\n")
        txns.sortedWith(compareBy({ it.day }, { it.createdAt })).forEach { t ->
            val items = t.items.joinToString("; ") { "${it.name} ${trimQty(it.qty)} ${it.unit} = ${money(it.price)}" }
            append(listOf(
                LocalDate.ofEpochDay(t.day).toString(),
                if (t.type == TxType.EXPENSE) "খরচ" else "আয়",
                d.category(t.categoryId)?.name ?: t.categoryId,
                d.member(t.memberId)?.name ?: t.memberId,
                money(t.amount),
                t.method.label,
                t.note,
                items,
            ).joinToString(",") { cell(it) })
            append('\n')
        }
    }

    fun trimQty(q: Double): String = if (q == Math.floor(q)) q.toLong().toString() else q.toString()
}
