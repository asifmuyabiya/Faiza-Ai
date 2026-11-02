package com.asif.familyhisab.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Pure money maths — no Android, fully unit-tested. */
object Calc {

    data class Totals(val income: Poisha, val expense: Poisha) {
        val balance: Poisha get() = income - expense
    }

    fun inMonth(txns: List<Txn>, ym: YearMonth): List<Txn> {
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        return txns.filter { it.day in from..to }
    }

    fun inYear(txns: List<Txn>, year: Int): List<Txn> {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return txns.filter { it.day in from..to }
    }

    fun totals(txns: List<Txn>) = Totals(
        income = txns.filter { it.type == TxType.INCOME }.sumOf { it.amount },
        expense = txns.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
    )

    /** Expense per member id (FAMILY_ID included), biggest first. */
    fun expenseByMember(txns: List<Txn>): List<Pair<String, Poisha>> =
        txns.filter { it.type == TxType.EXPENSE }.groupBy { it.memberId }
            .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    fun byCategory(txns: List<Txn>, type: TxType = TxType.EXPENSE): List<Pair<String, Poisha>> =
        txns.filter { it.type == type }.groupBy { it.categoryId }
            .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    /** Expense for each day of the month, index 0 = day 1. */
    fun dailyExpense(txns: List<Txn>, ym: YearMonth): List<Poisha> {
        val arr = LongArray(ym.lengthOfMonth())
        val start = ym.atDay(1).toEpochDay()
        txns.filter { it.type == TxType.EXPENSE }.forEach {
            val idx = (it.day - start).toInt()
            if (idx in arr.indices) arr[idx] += it.amount
        }
        return arr.toList()
    }

    /** Totals for the [n] months ending at [end] (oldest first). */
    fun lastMonths(txns: List<Txn>, end: YearMonth, n: Int): List<Pair<YearMonth, Totals>> =
        (n - 1 downTo 0).map { back -> end.minusMonths(back.toLong()).let { it to totals(inMonth(txns, it)) } }

    fun spentOn(txns: List<Txn>, day: LocalDate): Poisha =
        txns.filter { it.type == TxType.EXPENSE && it.day == day.toEpochDay() }.sumOf { it.amount }

    fun spentThisWeek(txns: List<Txn>, today: LocalDate): Poisha {
        // Bangladeshi week starts on Saturday.
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY)).toEpochDay()
        return txns.filter { it.type == TxType.EXPENSE && it.day in start..today.toEpochDay() }.sumOf { it.amount }
    }

    /** Average daily expense of a month — over days passed so far for the running month. */
    fun dailyAverage(txns: List<Txn>, ym: YearMonth, today: LocalDate): Poisha {
        val days = when {
            ym == YearMonth.from(today) -> today.dayOfMonth
            ym.isAfter(YearMonth.from(today)) -> 1
            else -> ym.lengthOfMonth()
        }
        return totals(inMonth(txns, ym)).expense / days.coerceAtLeast(1)
    }

    /** Days left in the month counting today (0 if the month is over, whole month if it hasn't started). */
    fun daysLeft(ym: YearMonth, today: LocalDate): Int = when {
        ym == YearMonth.from(today) -> ym.lengthOfMonth() - today.dayOfMonth + 1
        ym.isAfter(YearMonth.from(today)) -> ym.lengthOfMonth()
        else -> 0
    }

    /**
     * How much can still be spent per day and stay inside [budget]: what's left of the budget
     * shared over the remaining days. A heavy day lowers the later days' limit, a light day raises it.
     * Null when there's no budget or the month is over; 0 once the budget is used up.
     */
    fun dailyAllowance(budget: Poisha, spent: Poisha, ym: YearMonth, today: LocalDate): Poisha? {
        val days = daysLeft(ym, today)
        if (budget <= 0 || days <= 0) return null
        val perDay = (budget - spent).coerceAtLeast(0) / days
        return perDay - perDay % 100 // whole taka, rounded down so the limit is never overstated
    }

    /** Actual expense from day 1 up to [dayOfMonth] of [ym] — "last month by this date". */
    fun spentUntilDay(txns: List<Txn>, ym: YearMonth, dayOfMonth: Int): Poisha {
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth())).toEpochDay()
        return txns.filter { it.type == TxType.EXPENSE && it.day in from..to }.sumOf { it.amount }
    }

    /** Percent change from [old] to [new]; null when there's nothing to compare against. */
    fun change(old: Poisha, new: Poisha): Int? = if (old <= 0) null else (((new - old) * 100.0) / old).let { Math.round(it).toInt() }

    fun percent(part: Poisha, whole: Poisha): Float = if (whole <= 0) 0f else (part.toFloat() / whole).coerceIn(0f, 10f)

    data class ItemStat(val name: String, val unit: String, val totalSpent: Poisha, val times: Int, val lastUnitPrice: Poisha)

    /** Bazar items summed by name (case-insensitive), most spent first. */
    fun itemStats(txns: List<Txn>): List<ItemStat> =
        txns.sortedBy { it.day }.flatMap { t -> t.items.map { t.day to it } }
            .groupBy { it.second.name.trim().lowercase() }
            .map { (_, list) ->
                val last = list.last().second
                ItemStat(
                    name = last.name.trim(), unit = last.unit,
                    totalSpent = list.sumOf { it.second.price }, times = list.size,
                    lastUnitPrice = if (last.qty > 0) Math.round(last.price / last.qty) else last.price,
                )
            }.sortedByDescending { it.totalSpent }

    /** Frequent (type, category, member) combos — the "quick entry" chips on the home screen. */
    data class Quick(val type: TxType, val categoryId: String, val memberId: String, val lastAmount: Poisha, val count: Int)

    fun quickPicks(txns: List<Txn>, limit: Int = 6): List<Quick> =
        txns.sortedBy { it.createdAt }.groupBy { Triple(it.type, it.categoryId, it.memberId) }
            .map { (k, v) -> Quick(k.first, k.second, k.third, v.last().amount, v.size) }
            .sortedByDescending { it.count }.take(limit)

    data class DebtSummary(val toGet: Poisha, val toGive: Poisha)

    fun debtSummary(debts: List<Debt>) = DebtSummary(
        toGet = debts.filter { it.dir == DebtDir.GAVE }.sumOf { it.remaining },
        toGive = debts.filter { it.dir == DebtDir.TOOK }.sumOf { it.remaining },
    )

    /**
     * Evaluates what the keypad shows, e.g. "120+80.5+250" or "500-50". Returns poisha, or null if empty/invalid.
     * Only + and - — that's all bazar maths needs.
     */
    fun evalExpr(expr: String): Poisha? {
        val s = expr.replace(" ", "")
        if (s.isEmpty()) return null
        var total = 0L
        var sign = 1
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            val v = num.toString().toBigDecimalOrNull() ?: return false
            total += sign * v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
            num.clear()
            return true
        }
        for (c in s) {
            when (c) {
                '+', '-' -> { if (!flush()) return null; sign = if (c == '+') 1 else -1 }
                in '0'..'9', '.' -> num.append(c)
                else -> return null
            }
        }
        if (!flush()) return null
        return total
    }

    /** Merges imported data into current data; records with the same id are taken from [incoming]. */
    fun merge(current: AppData, incoming: AppData): AppData {
        fun <T> mergeBy(a: List<T>, b: List<T>, id: (T) -> String): List<T> {
            val map = LinkedHashMap<String, T>()
            a.forEach { map[id(it)] = it }
            b.forEach { map[id(it)] = it }
            return map.values.toList()
        }
        return current.copy(
            members = mergeBy(current.members, incoming.members) { it.id },
            categories = mergeBy(current.categories, incoming.categories) { it.id },
            txns = mergeBy(current.txns, incoming.txns) { it.id },
            debts = mergeBy(current.debts, incoming.debts) { it.id },
            shopping = mergeBy(current.shopping, incoming.shopping) { it.id },
            goals = mergeBy(current.goals, incoming.goals) { it.id },
            budget = if (incoming.budget == Budget()) current.budget else incoming.budget,
        )
    }
}
