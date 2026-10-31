package com.faiza.ai

import com.faiza.ai.ai.khata.Khata
import com.faiza.ai.ai.khata.MoneySms
import com.faiza.ai.ai.khata.MoneySms.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneySmsTest {

    @Test fun bkashReceived() {
        val p = MoneySms.parse("bKash", "You have received Tk 1,500.00 from 01712345678. Ref rent. Fee Tk 0.00. Balance Tk 3,210.50. TrxID BJ71ABC123 at 17/10/2026 10:02")!!
        assertEquals("bKash", p.service); assertEquals(Kind.RECEIVED, p.kind)
        assertEquals(1500.0, p.amount, 0.001); assertEquals(0.0, p.fee, 0.001)
        assertEquals("01712345678", p.other); assertEquals("BJ71ABC123", p.trx)
        val e = MoneySms.entries(p, 1L)
        assertEquals(1, e.size); assertEquals(Khata.Type.INCOME, e[0].type)
    }

    @Test fun bkashSendMoneyWithFee() {
        val p = MoneySms.parse("bKash", "Send Money Tk 500.00 to 01812345678 successful. Ref gift. Fee Tk 5.00. Balance Tk 2,705.50. TrxID BJ71XYZ789 at 17/10/2026 11:00")!!
        assertEquals(Kind.SENT, p.kind); assertEquals(500.0, p.amount, 0.001); assertEquals(5.0, p.fee, 0.001)
        val e = MoneySms.entries(p, 1L)
        assertEquals(2, e.size); assertEquals(505.0, e.sumOf { it.amount }, 0.001)
    }

    @Test fun cashOutOnlyFeeCounts() {
        val p = MoneySms.parse("bKash", "Cash Out Tk 1,000.00 to 01912345678 successful. Fee Tk 18.50. Balance Tk 1,687.00. TrxID BJ71QQQ111")!!
        assertEquals(Kind.CASH_OUT, p.kind)
        val e = MoneySms.entries(p, 1L)
        assertEquals(1, e.size); assertEquals(18.5, e[0].amount, 0.001)
    }

    @Test fun rechargeAndPayment() {
        assertEquals(Kind.RECHARGE, MoneySms.parse("bKash", "Mobile Recharge Tk 50.00 to 01712345678 successful. TrxID BJ7REC0001")!!.kind)
        val pay = MoneySms.parse("bKash", "Payment Tk 250.00 to Daraz successful. Balance Tk 100. TrxID BJ7PAY0001")!!
        assertEquals(Kind.PAYMENT, pay.kind); assertEquals("Daraz", pay.other)
    }

    @Test fun nagadFormats() {
        val r = MoneySms.parse("NAGAD", "Money Received. Amount: Tk 750.00 Sender: 01612345678 Ref: N/A TxnID: 7AB2CD9 Balance: Tk 900.00")!!
        assertEquals("Nagad", r.service); assertEquals(Kind.RECEIVED, r.kind); assertEquals(750.0, r.amount, 0.001)
        assertEquals("7AB2CD9", r.trx)
        val s = MoneySms.parse("NAGAD", "Send Money Successful. Amount: Tk 300 Receiver: 01512345678 Charge: Tk 4.00 TxnID: 7XY1ZZ2 Balance: Tk 596")!!
        assertEquals(Kind.SENT, s.kind); assertEquals(300.0, s.amount, 0.001); assertEquals(4.0, s.fee, 0.001)
    }

    @Test fun banglaSms() {
        val p = MoneySms.parse("bKash", "আপনি ০১৭১২৩৪৫৬৭৮ থেকে ৳২,০০০.০০ টাকা পেয়েছেন। TrxID BJ7BN00001")!!
        assertEquals(Kind.RECEIVED, p.kind); assertEquals(2000.0, p.amount, 0.001)
    }

    @Test fun notMoney() {
        assertNull(MoneySms.parse("bKash", "Your bKash verification code is 123456. Do not share it."))
        assertNull(MoneySms.parse("GP", "Get 10GB at Tk 99! Dial *121#"))
        assertNull(MoneySms.parse("bKash", "Enjoy cashback offer up to Tk 50 on Daraz!"))
    }
}
