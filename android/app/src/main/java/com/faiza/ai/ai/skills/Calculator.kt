package com.faiza.ai.ai.skills

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faiza.ai.FaizaApp
import com.faiza.ai.R
import com.faiza.ai.ai.provider.ParamType
import com.faiza.ai.ai.provider.ToolParam
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Exact arithmetic (owner: "she keeps clearing 300 in the calculator app"). Maths never goes through the Calculator
 * app's screen any more: this evaluates + − × ÷ % and brackets exactly (BigDecimal), understands Bangla digits and
 * words, and keeps a running tape for long dictated sums. Pure parts are unit-tested.
 */
object Calculator {

    class CalcError(msg: String) : Exception(msg)

    private val MC = MathContext(34, RoundingMode.HALF_EVEN)

    /** Spoken / written maths → plain "300+300*2" form. */
    fun normalize(input: String): String {
        var s = input.lowercase()
        "০১২৩৪৫৬৭৮৯".forEachIndexed { i, c -> s = s.replace(c, '0' + i) }
        "०१२३४५६७८९".forEachIndexed { i, c -> s = s.replace(c, '0' + i) }
        // English words only as whole words ("by" must not hit "baby").
        listOf(
            "multiplied by" to "*", "divided by" to "/", "percent" to "%", "plus" to "+", "add" to "+",
            "minus" to "-", "times" to "*", "into" to "*", "by" to "/", "x" to "*"
        ).forEach { (w, op) -> s = s.replace(Regex("(?<![a-z])" + Regex.escape(w) + "(?![a-z])"), " $op ") }
        listOf(
            "শতাংশ" to "%", "যোগ" to "+", "প্লাস" to "+", "जोड़" to "+", "प्लस" to "+",
            "বিয়োগ" to "-", "বিয়োগ" to "-", "মাইনাস" to "-", "घटा" to "-", "माइनस" to "-",
            "গুণ" to "*", "গুন" to "*", "गुणा" to "*", "ভাগ" to "/", "डिवाइड" to "/",
            "×" to "*", "÷" to "/", "–" to "-", "—" to "-", "−" to "-"
        ).forEach { (w, op) -> s = s.replace(w, " $op ") }
        // Thousands separators: "1,800" → "1800" (a comma between digit groups of 3).
        s = s.replace(Regex("(?<=\\d),(?=\\d{3}(\\D|$))"), "")
        s = s.replace(Regex("[^0-9.+\\-*/%()]"), "")
        return s
    }

    /** Evaluates [expr] exactly. Throws [CalcError] on bad input or division by zero. */
    fun eval(expr: String): BigDecimal {
        val s = normalize(expr)
        if (s.isEmpty()) throw CalcError("no numbers found")
        val p = Parser(s)
        val v = p.sum()
        if (p.i != s.length) throw CalcError("couldn't read \"${s.substring(p.i)}\"")
        return v
    }

    /** "3598", "12.5", "-40", "0.3333333333" — no trailing zeros, at most 10 decimals. */
    fun format(v: BigDecimal): String {
        val r = v.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (r.scale() < 0) r.setScale(0).toPlainString() else r.toPlainString()
    }

    /** "3,598" for speaking / showing. */
    fun pretty(v: BigDecimal): String {
        val plain = format(v)
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val int = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = int.reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + grouped + if (frac.isNotEmpty()) ".$frac" else ""
    }

    private class Parser(val s: String) {
        var i = 0
        fun sum(): BigDecimal {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = product()
                v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
            }
            return v
        }
        fun product(): BigDecimal {
            var v = unary()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = unary()
                v = if (op == '*') v.multiply(r, MC) else {
                    if (r.signum() == 0) throw CalcError("can't divide by zero")
                    v.divide(r, MC)
                }
            }
            return v
        }
        fun unary(): BigDecimal {
            if (i < s.length && s[i] == '-') { i++; return unary().negate() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v = v.divide(BigDecimal(100), MC) }
            return v
        }
        fun atom(): BigDecimal {
            if (i < s.length && s[i] == '(') {
                i++
                val v = sum()
                if (i < s.length && s[i] == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw CalcError("expected a number at \"${s.substring(start).take(8)}\"")
            return s.substring(start, i).toBigDecimalOrNull() ?: throw CalcError("bad number \"${s.substring(start, i)}\"")
        }
    }

    // ===== Running tape (long dictated sums across several sentences) =====

    private val tape = mutableListOf<Pair<String, BigDecimal>>()

    fun tapeAdd(expr: String): BigDecimal {
        val v = eval(if (normalize(expr).firstOrNull() in setOf('+', '-', '*', '/')) "0$expr" else expr)
        tape += normalize(expr) to v
        return total()
    }
    fun total(): BigDecimal = tape.fold(BigDecimal.ZERO) { a, (_, v) -> a.add(v, MC) }
    fun tapeClear() = tape.clear()
    fun tapeUndo(): Boolean = tape.removeLastOrNull() != null
    fun tapeText(): String = tape.joinToString(" + ") { pretty(it.second) }.ifEmpty { "(empty)" }

    private fun show(ctx: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(ctx, FaizaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_chip)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7991, n) }
    }

    fun skills(): List<Skill> = listOf(
        Skill(
            "calculate", "🧮", "Calculator", "\"৩০০ যোগ ৩০০ যোগ ৩০ যোগ ৮০ কত?\"",
            "Exact maths: + - * / % and brackets, any length. ALWAYS use this for any calculation — never open the " +
                    "Calculator app. Write the whole sum as digits and operators, e.g. \"300+300+30+80+10\"; convert number " +
                    "words first (তিনশো → 300, আঠারোশো → 1800, দেড় হাজার → 1500, সাড়ে তিনশো → 350).",
            listOf(ToolParam("expression", ParamType.STRING, "The full calculation, e.g. 300+300+30+80*2-50/5"))
        ) { ctx, args ->
            try {
                val expr = args.require("expression")
                val v = eval(expr)
                val terms = normalize(expr).split('+', '-', '*', '/').count { it.isNotBlank() }
                show(ctx, "🧮 = ${pretty(v)}", "${normalize(expr)} = ${pretty(v)}")
                "RESULT: ${normalize(expr)} = ${pretty(v)}  ($terms numbers). Say the answer clearly; the sum is also shown in a notification."
            } catch (e: CalcError) {
                "ERROR: ${e.message} — ask the user to repeat that part."
            }
        },
        Skill(
            "calc_tape", "🧾", "Running total", "\"যোগ করতে থাকো… মোট কত?\"",
            "A running total for long sums the user dictates over several sentences. action=add with the new numbers " +
                    "(\"300+300+30\" or \"-50\"), action=total for the sum so far, action=undo removes the last entry, " +
                    "action=clear starts again.",
            listOf(
                ToolParam("action", ParamType.STRING, "add, total, undo or clear", enum = listOf("add", "total", "undo", "clear")),
                ToolParam("expression", ParamType.STRING, "For add: the new numbers", required = false)
            )
        ) { ctx, args ->
            try {
                when (args.require("action")) {
                    "add" -> {
                        val t = tapeAdd(args.require("expression"))
                        show(ctx, "🧾 Running total: ${pretty(t)}", tapeText())
                        "OK: added. Running total = ${pretty(t)} (${tape.size} entries). Say the subtotal briefly or just \"ok\"."
                    }
                    "undo" -> if (tapeUndo()) "OK: removed the last entry. Total = ${pretty(total())}" else "The tape is empty."
                    "clear" -> { tapeClear(); "OK: cleared — starting from 0." }
                    else -> {
                        show(ctx, "🧾 Total: ${pretty(total())}", tapeText() + " = " + pretty(total()))
                        "TOTAL: ${pretty(total())} from ${tape.size} entries (${tapeText()})."
                    }
                }
            } catch (e: CalcError) {
                "ERROR: ${e.message} — ask the user to repeat that part."
            }
        }
    )
}
