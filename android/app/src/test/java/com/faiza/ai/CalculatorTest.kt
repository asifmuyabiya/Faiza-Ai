package com.faiza.ai

import com.faiza.ai.ai.skills.Calculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {
    private fun calc(s: String) = Calculator.format(Calculator.eval(s))

    @Test
    fun ownersLongSum() {
        // 300 + 300 + 30 + 80 + 10 + 18 + 1800 + 100 + 20 + 30 + 800 + 140 + 60
        assertEquals("3688", calc("300+300+30+80+10+18+1800+100+20+30+800+140+60"))
        assertEquals("3,688", Calculator.pretty(Calculator.eval("300 + 300 + 30 + 80 + 10 + 18 + 1,800 + 100 + 20 + 30 + 800 + 140 + 60")))
    }

    @Test
    fun allOperationsAndPrecedence() {
        assertEquals("14", calc("2+3*4"))
        assertEquals("20", calc("(2+3)*4"))
        assertEquals("2.5", calc("10/4"))
        assertEquals("-5", calc("10-15"))
        assertEquals("50", calc("200*25%"))
        assertEquals("0.3333333333", calc("1/3"))
        assertEquals("0.3", calc("0.1+0.2")) // exact, no floating-point 0.30000000000000004
    }

    @Test
    fun wordsAndBanglaDigits() {
        assertEquals("630", calc("৩০০ যোগ ৩০০ যোগ ৩০"))
        assertEquals("250", calc("300 minus 50"))
        assertEquals("600", calc("300 গুণ 2"))
        assertEquals("150", calc("300 ভাগ 2"))
        assertEquals("12", calc("3 x 4"))
        assertEquals("1500", calc("1,500"))
    }

    @Test
    fun errorsAndTape() {
        assertTrue(runCatching { Calculator.eval("5/0") }.exceptionOrNull() is Calculator.CalcError)
        assertTrue(runCatching { Calculator.eval("hello") }.exceptionOrNull() is Calculator.CalcError)
        Calculator.tapeClear()
        Calculator.tapeAdd("300+300+30")
        Calculator.tapeAdd("+80+10")
        Calculator.tapeAdd("-20")
        assertEquals("700", Calculator.format(Calculator.total()))
        Calculator.tapeUndo()
        assertEquals("720", Calculator.format(Calculator.total()))
        Calculator.tapeClear()
        assertEquals("0", Calculator.format(Calculator.total()))
    }
}
