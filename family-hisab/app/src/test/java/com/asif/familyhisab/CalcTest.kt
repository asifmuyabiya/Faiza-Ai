package com.asif.familyhisab

import com.asif.familyhisab.data.AppData
import com.asif.familyhisab.data.Backup
import com.asif.familyhisab.data.BazarItem
import com.asif.familyhisab.data.Calc
import com.asif.familyhisab.data.Debt
import com.asif.familyhisab.data.DebtDir
import com.asif.familyhisab.data.DebtPayment
import com.asif.familyhisab.data.FAMILY_ID
import com.asif.familyhisab.data.TxType
import com.asif.familyhisab.data.Txn
import com.asif.familyhisab.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalcTest {

    private val nov = YearMonth.of(2026, 11)
    private fun day(d: Int, m: Int = 11) = LocalDate.of(2026, m, d).toEpochDay()

    private val txns = listOf(
        Txn(id = "1", amount = 120000, categoryId = "bazar", memberId = FAMILY_ID, day = day(1)),
        Txn(id = "2", amount = 150000, categoryId = "cloth", memberId = "ibad", day = day(3)),
        Txn(id = "3", amount = 5000000, categoryId = "salary", memberId = "asif", day = day(1), type = TxType.INCOME),
        Txn(id = "4", amount = 30000, categoryId = "medical", memberId = "abbu", day = day(3)),
        Txn(id = "5", amount = 99900, categoryId = "bazar", memberId = FAMILY_ID, day = day(30, 10)),
    )

    @Test fun monthTotals() {
        val t = Calc.totals(Calc.inMonth(txns, nov))
        assertEquals(300000L, t.expense)
        assertEquals(5000000L, t.income)
        assertEquals(4700000L, t.balance)
    }

    @Test fun perMemberSplitsFamilyAndPersonal() {
        val m = Calc.expenseByMember(Calc.inMonth(txns, nov)).toMap()
        assertEquals(150000L, m["ibad"])
        assertEquals(120000L, m[FAMILY_ID])
        assertEquals(30000L, m["abbu"])
        assertNull(m["asif"]) // income is not counted as spending
    }

    @Test fun dailySeries() {
        val daily = Calc.dailyExpense(Calc.inMonth(txns, nov), nov)
        assertEquals(30, daily.size)
        assertEquals(120000L, daily[0])
        assertEquals(180000L, daily[2])
    }

    @Test fun keypadExpressions() {
        assertEquals(45000L, Calc.evalExpr("120+80+250"))
        assertEquals(45050L, Calc.evalExpr("500-50+0.5"))
        assertEquals(1250L, Calc.evalExpr("12.5"))
        assertNull(Calc.evalExpr(""))
        assertNull(Calc.evalExpr("1..2"))
    }

    @Test fun perDaySpreadsOverDaysPassed() {
        val one = listOf(Txn(amount = 100000, categoryId = "bazar", day = day(1)))
        val p = com.asif.familyhisab.data.Period.month(nov)
        assertEquals(100000L, Calc.perDay(one, p, LocalDate.of(2026, 11, 1))) // 1000 on day 1
        assertEquals(50000L, Calc.perDay(one, p, LocalDate.of(2026, 11, 2)))  // nothing on day 2 → 500
        assertEquals(33300L, Calc.perDay(one, p, LocalDate.of(2026, 11, 3)))  // → 333
        assertEquals(3300L, Calc.perDay(one, p, LocalDate.of(2027, 1, 1)))    // month over → ÷30
        assertEquals(0L, Calc.perDay(one, p, LocalDate.of(2026, 10, 1)))      // not started
    }

    @Test fun periodsShiftAndCount() {
        val P = com.asif.familyhisab.data.Period
        val m = P.month(nov)
        assertEquals(30, m.days)
        assertEquals(LocalDate.of(2026, 10, 1), m.previous()!!.from)
        val w = P.week(LocalDate.of(2026, 11, 3)) // Tuesday → week starts Saturday 31 Oct
        assertEquals(LocalDate.of(2026, 10, 31), w.from)
        assertEquals(7, w.days)
        val r = P.lastDays(7, LocalDate.of(2026, 11, 3))
        assertEquals(LocalDate.of(2026, 10, 21), r.shift(-1).from)
        assertEquals(r, P.decode(r.encode()))
        assertEquals(2, Calc.inPeriod(txns, P.range(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))).size)
    }

    @Test fun trendIsDailyForShortAndMonthlyForLong() {
        val P = com.asif.familyhisab.data.Period
        assertEquals(30, Calc.trend(txns, P.month(nov)).size)
        val y = Calc.trend(txns, P.year(2026))
        assertEquals(12, y.size)
        assertEquals(99900L, y[9].expense)
    }

    @Test fun loansMoveCashButAreNotIncomeOrExpense() {
        val P = com.asif.familyhisab.data.Period
        val took = Debt(person = "Rahim", dir = DebtDir.TOOK, amount = 1000000, day = day(5))
        assertEquals(1000000L, Calc.debtFlow(listOf(took), P.month(nov)))           // +10,000 in hand
        val repaid = took.copy(payments = listOf(DebtPayment(1000000, day(15))))
        assertEquals(0L, Calc.debtFlow(listOf(repaid), P.month(nov)))               // back to zero
        val lent = Debt(person = "Karim", dir = DebtDir.GAVE, amount = 500000, day = day(2), payments = listOf(DebtPayment(200000, day(9))))
        assertEquals(-300000L, Calc.debtFlow(listOf(lent), P.month(nov)))
        assertEquals(1, Calc.overdue(listOf(took.copy(dueDay = day(10))), LocalDate.of(2026, 11, 11)).size)
    }

    @Test fun changePercent() {
        assertEquals(50, Calc.change(1000, 1500))
        assertEquals(-25, Calc.change(1000, 750))
        assertNull(Calc.change(0, 500))
    }

    @Test fun debts() {
        val d = Debt(person = "Karim", dir = DebtDir.GAVE, amount = 50000, day = 0, payments = listOf(DebtPayment(20000, 0)))
        assertEquals(30000L, d.remaining)
        val s = Calc.debtSummary(listOf(d, Debt(person = "Dokan", dir = DebtDir.TOOK, amount = 10000, day = 0)))
        assertEquals(30000L, s.toGet)
        assertEquals(10000L, s.toGive)
    }

    @Test fun itemStatsUseLatestUnitPrice() {
        val t = listOf(
            Txn(amount = 1, categoryId = "bazar", day = 1, items = listOf(BazarItem("Chal", 5.0, "কেজি", 35000))),
            Txn(amount = 1, categoryId = "bazar", day = 2, items = listOf(BazarItem("chal", 2.0, "কেজি", 15000))),
        )
        val s = Calc.itemStats(t).single()
        assertEquals(50000L, s.totalSpent)
        assertEquals(7500L, s.lastUnitPrice)
        assertEquals(2, s.times)
    }

    @Test fun backupRoundTripAndMerge() {
        val d = AppData(txns = txns)
        val back = Backup.fromJson(Backup.toJson(d))
        assertEquals(d, back)
        val merged = Calc.merge(AppData(txns = txns.take(2)), AppData(txns = txns.drop(1)))
        assertEquals(5, merged.txns.size)
    }

    @Test fun csvHasBomAndRows() {
        val csv = Backup.toCsv(AppData(txns = txns), txns)
        assertTrue(csv.startsWith("﻿"))
        assertEquals(1 + txns.size, csv.trim().lines().size)
        assertTrue(csv.contains("1200"))
    }

    @Test fun takaFormatting() {
        assertEquals("৳১,২৩,৪৫৬", Fmt(true).money(12345600))
        assertEquals("৳12,500.50", Fmt(false).money(1250050))
        assertEquals("−৳500", Fmt(false).money(-50000))
        assertTrue(!Fmt(true).money(100).contains("$"))
    }
}
