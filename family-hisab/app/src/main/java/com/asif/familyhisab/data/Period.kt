package com.asif.familyhisab.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PKind { DAY, WEEK, MONTH, YEAR, RANGE, ALL }

/**
 * A date filter: আজ, গতকাল, এই সপ্তাহ, গত ৭ দিন, এই মাস, গত মাস, এই বছর, সব সময় or any custom range.
 * [shift] moves it back/forward by its own size (‹ › buttons).
 */
data class Period(val kind: PKind, val from: LocalDate, val to: LocalDate) {

    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    val fromDay: Long get() = from.toEpochDay()
    val toDay: Long get() = to.toEpochDay()
    val isMonth: Boolean get() = kind == PKind.MONTH
    val yearMonth: YearMonth get() = YearMonth.from(to)

    fun contains(day: Long) = day in fromDay..toDay

    fun shift(n: Int): Period = when (kind) {
        PKind.DAY -> day(from.plusDays(n.toLong()))
        PKind.WEEK -> week(from.plusWeeks(n.toLong()))
        PKind.MONTH -> month(YearMonth.from(from).plusMonths(n.toLong()))
        PKind.YEAR -> year(from.year + n)
        PKind.RANGE -> Period(PKind.RANGE, from.plusDays(n.toLong() * days), to.plusDays(n.toLong() * days))
        PKind.ALL -> this
    }

    /** The same-sized period right before this one, for "আগের চেয়ে বেশি/কম". Null for সব সময়. */
    fun previous(): Period? = if (kind == PKind.ALL) null else shift(-1)

    /** Days of this period that have actually passed (up to today) — the divisor for দিনপ্রতি. */
    fun elapsedDays(today: LocalDate): Int = when {
        today.isBefore(from) -> 0
        today.isAfter(to) -> days
        else -> (ChronoUnit.DAYS.between(from, today) + 1).toInt()
    }

    fun encode() = "$kind|$from|$to"

    companion object {
        fun decode(s: String): Period? = runCatching {
            val (k, a, b) = s.split("|")
            Period(PKind.valueOf(k), LocalDate.parse(a), LocalDate.parse(b))
        }.getOrNull()

        fun day(d: LocalDate) = Period(PKind.DAY, d, d)

        /** Bangladeshi week: Saturday → Friday. */
        fun week(d: LocalDate): Period {
            val start = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
            return Period(PKind.WEEK, start, start.plusDays(6))
        }

        fun month(ym: YearMonth) = Period(PKind.MONTH, ym.atDay(1), ym.atEndOfMonth())
        fun year(y: Int) = Period(PKind.YEAR, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
        fun lastDays(n: Int, today: LocalDate) = Period(PKind.RANGE, today.minusDays(n - 1L), today)
        fun range(a: LocalDate, b: LocalDate) = if (a == b) day(a) else Period(PKind.RANGE, minOf(a, b), maxOf(a, b))
        fun all(earliest: LocalDate?, today: LocalDate) = Period(PKind.ALL, minOf(earliest ?: today, today), today)
        fun thisMonth() = month(YearMonth.now())
    }
}
