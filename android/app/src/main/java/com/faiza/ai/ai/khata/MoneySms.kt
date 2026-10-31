package com.faiza.ai.ai.khata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.faiza.ai.FaizaApp

/**
 * bKash / Nagad / Rocket / Upay SMS → the money book (owner: "লেনদেনের SMS পড়ে নিজেই খরচের তালিকা"). Read-only:
 * Faiza never sends, pays or moves money. Pure parser + a receiver for new SMS + an import of recent ones.
 */
object MoneySms {

    data class Parsed(val service: String, val kind: Kind, val amount: Double, val fee: Double, val other: String, val trx: String)

    enum class Kind { RECEIVED, SENT, PAYMENT, RECHARGE, BILL, CASH_OUT, CASH_IN }

    private val SENDERS = mapOf(
        "bkash" to "bKash", "nagad" to "Nagad", "16216" to "Rocket", "rocket" to "Rocket", "upay" to "Upay",
        "16167" to "bKash", "dbbl" to "Rocket", "tap" to "Tap", "mcash" to "mCash"
    )

    fun serviceOf(sender: String?, body: String): String? {
        val s = sender.orEmpty().lowercase().filter { it.isLetterOrDigit() }
        SENDERS.forEach { (k, v) -> if (s.contains(k)) return v }
        val b = body.lowercase()
        return when {
            b.contains("bkash") -> "bKash"
            b.contains("nagad") -> "Nagad"
            b.contains("rocket") -> "Rocket"
            else -> null
        }
    }

    private fun bnDigits(s: String) = s.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")

    private fun num(s: String?): Double? = s?.let { bnDigits(it).replace(",", "").trimEnd('.').toDoubleOrNull() }

    private val AMOUNT_LABEL = Regex("""(?i)amount\s*[:=]?\s*(?:tk|bdt|৳)\.?\s*([\d০-৯,]+(?:\.\d+)?)""")
    private val TK = Regex("""(?i)(?:tk|bdt|৳)\.?\s*([\d০-৯,]+(?:\.[\d০-৯]+)?)""")
    private val TAKA_BN = Regex("""([\d০-৯,]+(?:\.[\d০-৯]+)?)\s*টাকা""")
    private val FEE = Regex("""(?i)(?:fee|charge|চার্জ|ফি)\s*[:=]?\s*(?:tk|bdt|৳)?\.?\s*([\d০-৯,]+(?:\.[\d০-৯]+)?)""")
    private val TRX = Regex("""(?i)(?:trxid|txnid|trx\s*id|txn\s*id|transaction\s*id|ট্রানজেকশন\s*আইডি)\s*[:=]?\s*([A-Z0-9]{6,})""")
    private val NUMBER = Regex("""(?:\+?88)?01[3-9]\d{8}""")
    private val TO_FROM = Regex("""(?i)\b(?:to|from|receiver|sender|merchant)\s*[:=]?\s*([A-Za-z0-9 &._'-]{2,40}?)(?=[.,]|\s+(?:successful|ref|fee|balance|trx|txn|at\b)|$)""")

    /** Null when it isn't a money SMS (OTP, promo, balance check…). */
    fun parse(sender: String?, body: String): Parsed? {
        val service = serviceOf(sender, body) ?: return null
        val b = body.lowercase()
        // Codes, offers and failures are not money movements.
        if (listOf("otp", "verification code", "pin", "failed", "unsuccessful", "offer", "cashback offer", "যাচাই", "ব্যর্থ")
                .any { b.contains(it) } && !b.contains("trxid") && !b.contains("txnid")) return null
        val kind = when {
            b.contains("cash in") || b.contains("ক্যাশ ইন") -> Kind.CASH_IN
            b.contains("cash out") || b.contains("ক্যাশ আউট") -> Kind.CASH_OUT
            b.contains("recharge") || b.contains("রিচার্জ") -> Kind.RECHARGE
            b.contains("bill") || b.contains("বিল") -> Kind.BILL
            b.contains("payment") || b.contains("পেমেন্ট") || b.contains("merchant") -> Kind.PAYMENT
            b.contains("received") || b.contains("money received") || b.contains("পেয়েছেন") || b.contains("পেয়েছেন") ||
                    b.contains("জমা হয়েছে") || b.contains("credited") -> Kind.RECEIVED
            b.contains("send money") || b.contains("sent") || b.contains("transferred") || b.contains("পাঠিয়েছেন") ||
                    b.contains("পাঠিয়েছেন") || b.contains("সেন্ড মানি") || b.contains("debited") -> Kind.SENT
            else -> return null
        }
        val amount = num(AMOUNT_LABEL.find(body)?.groupValues?.get(1))
            ?: num(TK.find(body)?.groupValues?.get(1))
            ?: num(TAKA_BN.find(body)?.groupValues?.get(1))
            ?: return null
        if (amount <= 0) return null
        val fee = num(FEE.find(body)?.groupValues?.get(1)) ?: 0.0
        val other = NUMBER.find(body)?.value
            ?: TO_FROM.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("tk", true) }
            ?: ""
        val trx = TRX.find(body)?.groupValues?.get(1).orEmpty()
        return Parsed(service, kind, amount, fee, other, trx)
    }

    /** What goes into the book (cash in/out move your own money: only the fee counts). */
    fun entries(p: Parsed, at: Long): List<Khata.Entry> {
        val who = if (p.other.isNotBlank()) " · ${p.other}" else ""
        val ref = if (p.trx.isNotBlank()) " · ${p.trx}" else ""
        val main = when (p.kind) {
            Kind.RECEIVED -> Khata.Entry(at = at, type = Khata.Type.INCOME, amount = p.amount, note = "${p.service} received$who$ref", source = "sms")
            Kind.SENT -> Khata.Entry(at = at, type = Khata.Type.EXPENSE, amount = p.amount, note = "${p.service} send money$who$ref", source = "sms")
            Kind.PAYMENT -> Khata.Entry(at = at, type = Khata.Type.EXPENSE, amount = p.amount, note = "${p.service} payment$who$ref", source = "sms")
            Kind.RECHARGE -> Khata.Entry(at = at, type = Khata.Type.EXPENSE, amount = p.amount, note = "রিচার্জ (${p.service})$who$ref", source = "sms")
            Kind.BILL -> Khata.Entry(at = at, type = Khata.Type.EXPENSE, amount = p.amount, note = "বিল (${p.service})$who$ref", source = "sms")
            Kind.CASH_OUT, Kind.CASH_IN -> null
        }
        val fee = if (p.fee > 0) Khata.Entry(at = at + 1, type = Khata.Type.EXPENSE, amount = p.fee, note = "${p.service} charge$ref", source = "sms") else null
        return listOfNotNull(main, fee)
    }

    fun record(ctx: Context, sender: String?, body: String, at: Long): Int {
        val p = parse(sender, body) ?: return 0
        val book = Khata.all(ctx)
        if (p.trx.isNotBlank() && book.any { it.source == "sms" && it.note.contains(p.trx) }) return 0
        var n = 0
        entries(p, at).forEach { if (Khata.addIfNew(ctx, it)) n++ }
        return n
    }

    /** Reads the last [days] days of SMS (needs READ_SMS). Returns how many entries were added. */
    fun importRecent(ctx: Context, days: Int = 30): Int {
        val since = System.currentTimeMillis() - days * 86_400_000L
        var added = 0
        runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                while (c.moveToNext()) added += record(ctx, c.getString(0), c.getString(1).orEmpty(), c.getLong(2))
            }
        }
        return added
    }
}

/** New SMS arrived: if it's a bKash/Nagad/Rocket transaction and the owner turned this on, log it. */
class MoneySmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!FaizaApp.instance.prefs.smsMoneyLog) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        val sender = parts.firstOrNull()?.originatingAddress
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val at = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        runCatching { MoneySms.record(ctx, sender, body, at) }
    }
}
