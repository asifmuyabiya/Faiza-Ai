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
