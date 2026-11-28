package com.sujan.pipe

import com.sujan.pipe.domain.PaletteItem
import com.sujan.pipe.domain.PipeCalculator
import com.sujan.pipe.domain.PromptIntentMode
import com.sujan.pipe.domain.QueryRouter
import com.sujan.pipe.model.DefaultBookmarks
import com.sujan.pipe.model.DefaultEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeCalculatorTest {

    @Test
    fun `evaluates basic arithmetic, precedence, parentheses, and exact decimals without float drift`() {
        val dec = PipeCalculator.evaluate("0.1 + 0.2")
        assertTrue(dec.isValid)
        assertEquals("0.3", dec.plainValue)
        assertEquals("0.3", dec.formattedValue)

        val prec = PipeCalculator.evaluate("2 + 3 * 4")
        assertEquals("14", prec.plainValue)

        val parens = PipeCalculator.evaluate("(2 + 3) * (4 + 5)")
        assertEquals("45", parens.plainValue)

        val implicit = PipeCalculator.evaluate("2(3+4)")
        assertEquals("14", implicit.plainValue)

        val unicodeOps = PipeCalculator.evaluate("2,450 × 1.13 − 10 ÷ 2")
        assertTrue(unicodeOps.isValid)
        assertEquals("2763.5", unicodeOps.plainValue)
        assertEquals("2,763.5", unicodeOps.formattedValue)
    }

    @Test
    fun `handles pocket-calculator percentages and division by zero cleanly`() {
        assertEquals("220", PipeCalculator.evaluate("200 + 10%").plainValue)
        assertEquals("170", PipeCalculator.evaluate("200 - 15%").plainValue)
        assertEquals("1260", PipeCalculator.evaluate("8400 * 15%").plainValue)
        assertEquals("0.5", PipeCalculator.evaluate("50%").plainValue)

        val divZero = PipeCalculator.evaluate("10 / 0")
        assertFalse(divZero.isValid)
        assertEquals("cannot divide by 0", divZero.errorMessage)
    }

    @Test
    fun `live preview tolerates incomplete trailing operators and unclosed parens`() {
        val trailingOp = PipeCalculator.evaluate("250 + ")
        assertTrue(trailingOp.isValid)
        assertEquals("250", trailingOp.plainValue)

        val openParen = PipeCalculator.evaluate("(10 + 5")
        assertTrue(openParen.isValid)
        assertEquals("15", openParen.plainValue)
    }

    @Test
    fun `T9 keypad mutators handle decimals, operators, and smart parentheses`() {
        var expr = ""
        expr = PipeCalculator.appendDigit(expr, '2')
        expr = PipeCalculator.appendDecimal(expr)
        expr = PipeCalculator.appendDecimal(expr) // ignored duplicate decimal
        expr = PipeCalculator.appendDigit(expr, '5')
        assertEquals("2.5", expr)

        expr = PipeCalculator.appendOperator(expr, '+')
        expr = PipeCalculator.appendOperator(expr, '*') // replaces + with ×
        assertEquals("2.5×", expr)

        expr = PipeCalculator.appendSmartParen(expr)
        assertEquals("2.5×(", expr)
        expr = PipeCalculator.appendDigit(expr, '4')
        expr = PipeCalculator.appendSmartParen(expr)
        assertEquals("2.5×(4)", expr)
        assertEquals("10", PipeCalculator.evaluate(expr).plainValue)
    }

    @Test
    fun `QueryRouter keeps calculator completely separate from unified ALL search`() {
        val engines = DefaultEngines.LIST
        val bookmarks = DefaultBookmarks.LIST

        // Typing math in normal search MUST NOT activate calculator
        val normalCmd = QueryRouter.parsePromptCommand("250*4", bookmarks, engines)
        assertEquals(PromptIntentMode.ALL, normalCmd.mode)
        val normalResults = QueryRouter.buildPaletteResults(
            rawInput = "250*4",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
        )
        assertFalse(normalResults.any { it is PaletteItem.CalculatorAction })

        // Explicit `=` or `calc` activates CALCULATOR_ONLY
        val calcCmd = QueryRouter.parsePromptCommand("calc 200+10%", bookmarks, engines)
        assertEquals(PromptIntentMode.CALCULATOR_ONLY, calcCmd.mode)
        val calcResults = QueryRouter.buildPaletteResults(
            rawInput = "=200+10%",
            engines = engines,
            bookmarks = bookmarks,
            contacts = emptyList(),
            storageFiles = emptyList(),
            scopedFolderFiles = emptyList(),
            history = emptyList(),
            usageMap = emptyMap(),
        )
        assertTrue(calcResults.first() is PaletteItem.CalculatorAction)
        val act = calcResults.first() as PaletteItem.CalculatorAction
        assertTrue(act.isValid)
        assertEquals("220", act.plainValue)
    }
}
