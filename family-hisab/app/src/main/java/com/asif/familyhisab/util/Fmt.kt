package com.asif.familyhisab.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.asif.familyhisab.data.Poisha
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Number and date formatting, in Bangla or English digits. Provided to the UI via [LocalFmt]. */
data class Fmt(val bangla: Boolean = true) {

    fun digits(s: String): String = if (!bangla) s else buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') BN_DIGITS[c - '0'] else c)
    }

    /** Bangladeshi grouping: 1,23,45,678 */
    private fun group(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** "৳ ১২,৩৪৫" — paisa shown only when there are some. */
    fun money(p: Poisha, sign: Boolean = false, symbol: Boolean = true): String {
        val a = abs(p)
        val whole = group(a / 100)
        val frac = a % 100
        val body = if (frac == 0L) whole else "$whole.${frac.toString().padStart(2, '0')}"
        val pre = when { p < 0 -> "−"; sign && p > 0 -> "+"; else -> "" }
        return digits(pre + (if (symbol) "৳" else "") + body)
    }

    /** Short form for chart labels: ১.২ লাখ, ১৫হা */
    fun short(p: Poisha): String {
        val t = p / 100.0
        val s = when {
            t >= 100_000 -> "%.1f লা".format(t / 100_000).replace(".0 ", " ")
            t >= 1_000 -> "%.1fহা".format(t / 1_000).replace(".0হা", "হা")
            else -> "%.0f".format(t)
        }
        return digits(s)
    }

    fun num(n: Number): String = digits(n.toString())

    fun month(ym: YearMonth): String = "${MONTHS[ym.monthValue - 1]} ${digits(ym.year.toString())}"
    fun monthShort(ym: YearMonth): String = MONTHS_SHORT[ym.monthValue - 1]

    fun date(d: LocalDate): String = "${digits(d.dayOfMonth.toString())} ${MONTHS[d.monthValue - 1]}, ${digits(d.year.toString())}"

    fun dayHeader(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "আজ"
        today.minusDays(1) -> "গতকাল"
        else -> "${WEEKDAYS[d.dayOfWeek.value - 1]}, ${digits(d.dayOfMonth.toString())} ${MONTHS[d.monthValue - 1]}"
    }

    fun weekday(d: LocalDate) = WEEKDAYS[d.dayOfWeek.value - 1]

    companion object {
        private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
        val MONTHS_SHORT = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রি", "মে", "জুন", "জুলা", "আগ", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
        val WEEKDAYS = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")

        fun greeting(hour: Int): String = when (hour) {
            in 4..11 -> "শুভ সকাল"
            in 12..15 -> "শুভ দুপুর"
            in 16..18 -> "শুভ বিকাল"
            else -> "শুভ সন্ধ্যা"
        }
    }
}

val LocalFmt = staticCompositionLocalOf { Fmt() }
