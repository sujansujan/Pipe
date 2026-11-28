package com.sujan.pipe.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

data class CalcResult(
    val expression: String,
    val plainValue: String,
    val formattedValue: String,
    val isValid: Boolean,
    val errorMessage: String? = null,
)

data class CalcTapeEntry(
    val expression: String,
    val plainValue: String,
    val formattedValue: String,
)

/**
 * Pure Kotlin offline arbitrary-precision expression evaluator for Pipe's built-in T9 Calculator.
 * - Uses [BigDecimal] (`DECIMAL128`) so `0.1 + 0.2 == 0.3` with zero floating-point drift.
 * - Supports `+`, `-`/`−`, `*`/`×`, `/`/`÷`, `^`, parentheses `( )`, unary `+/-`, implicit multiplication
 *   (`2(3+4)` = `14`), and pocket-calculator percentage (`200 + 10%` = `220`, `8400 * 15%` = `1260`).
 */
object PipeCalculator {

    private val MC = MathContext(16, RoundingMode.HALF_UP)
    private val HUNDRED = BigDecimal("100")

    fun evaluate(rawExpression: String): CalcResult {
        val cleaned = normalizeExpression(rawExpression)
        if (cleaned.isEmpty()) {
            return CalcResult(rawExpression, "", "", isValid = false)
        }
        val evalTarget = prepareForLiveEvaluation(cleaned)
        if (evalTarget.isEmpty()) {
            return CalcResult(rawExpression, "", "", isValid = false)
        }
        return try {
            val parser = ExprParser(evalTarget)
            val value = parser.parseExpression()
            parser.skipSpaces()
            if (!parser.isAtEnd()) {
                return CalcResult(rawExpression, "", "", isValid = false, errorMessage = "syntax error")
            }
            val plain = formatPlain(value)
            val formatted = formatDisplay(value)
            CalcResult(
                expression = rawExpression,
                plainValue = plain,
                formattedValue = formatted,
                isValid = true,
            )
        } catch (_: ArithmeticException) {
            CalcResult(rawExpression, "", "", isValid = false, errorMessage = "cannot divide by 0")
        } catch (_: Exception) {
            CalcResult(rawExpression, "", "", isValid = false, errorMessage = "invalid expression")
        }
    }

    fun normalizeExpression(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '×', '·' -> sb.append('*')
                '÷' -> sb.append('/')
                '−', '–', '—' -> sb.append('-')
                ',' -> Unit // ignore thousand separators if pasted
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * Converts raw ASCII math operators to clean display glyphs (`×`, `÷`, `−`) for the T9 display.
     */
    fun prettifyExpression(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '*' -> sb.append('×')
                '/' -> sb.append('÷')
                '-' -> sb.append('−')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // --- Smart T9 keypad input mutators ---

    fun appendDigit(expr: String, digit: Char): String {
        if (digit !in '0'..'9') return expr
        val last = expr.lastOrNull()
        // Prevent redundant leading zero in a bare integer like "00" -> "0", unless after '.'
        if (expr == "0" && digit == '0') return "0"
        if (expr == "0" && digit in '1'..'9') return digit.toString()
        if (last == '0' && expr.length >= 2) {
            val prev = expr[expr.length - 2]
            if (isOperatorChar(prev) || prev == '(') {
                return if (digit == '0') expr else expr.dropLast(1) + digit
            }
        }
        return expr + digit
    }

    fun appendDecimal(expr: String): String {
        if (expr.isEmpty()) return "0."
        val last = expr.last()
        if (last == '.') return expr
        if (isOperatorChar(last) || last == '(') return "${expr}0."
        if (last == ')' || last == '%') return "${expr}×0."
        // Scan backward through current number token to block double decimal
        var i = expr.lastIndex
        while (i >= 0 && (expr[i] in '0'..'9' || expr[i] == '.')) {
            if (expr[i] == '.') return expr
            i--
        }
        return "$expr."
    }

    fun appendOperator(expr: String, op: Char): String {
        val displayOp = when (op) {
            '*' -> '×'
            '/' -> '÷'
            '-' -> '−'
            else -> op
        }
        if (expr.isEmpty()) {
            return if (displayOp == '−') "−" else ""
        }
        val last = expr.last()
        if (last == '(') {
            return if (displayOp == '−') "$expr−" else expr
        }
        if (isOperatorChar(last)) {
            // Allow negative sign after × or ÷ (e.g. 5 × −2)
            if (displayOp == '−' && (last == '×' || last == '*' || last == '÷' || last == '/' || last == '^')) {
                return "$expr−"
            }
            // If we already have `×−` and user taps `+`, strip both trailing operators
            var trimmed = expr
            while (trimmed.isNotEmpty() && isOperatorChar(trimmed.last())) {
                trimmed = trimmed.dropLast(1)
            }
            return if (trimmed.isEmpty()) {
                if (displayOp == '−') "−" else ""
            } else {
                "$trimmed$displayOp"
            }
        }
        if (last == '.') {
            return "${expr.dropLast(1)}$displayOp"
        }
        return "$expr$displayOp"
    }

    fun appendPercent(expr: String): String {
        if (expr.isEmpty()) return ""
        val last = expr.last()
        if (last in '0'..'9' || last == ')') {
            return "$expr%"
        }
        return expr
    }

    fun appendSmartParen(expr: String): String {
        var openCount = 0
        for (ch in expr) {
            if (ch == '(') openCount++
            else if (ch == ')') openCount--
        }
        val last = expr.lastOrNull()
        return if (openCount > 0 && last != null && (last in '0'..'9' || last == ')' || last == '%')) {
            "$expr)"
        } else {
            "$expr("
        }
    }

    fun backspace(expr: String): String {
        return if (expr.isNotEmpty()) expr.dropLast(1) else ""
    }

    private fun isOperatorChar(ch: Char): Boolean {
        return ch == '+' || ch == '-' || ch == '−' || ch == '*' || ch == '×' || ch == '/' || ch == '÷' || ch == '^'
    }

    private fun prepareForLiveEvaluation(cleaned: String): String {
        var s = cleaned.trimEnd()
        // Drop incomplete trailing binary operators so live preview stays useful while typing
        while (s.isNotEmpty() && (s.last() == '+' || s.last() == '-' || s.last() == '*' || s.last() == '/' || s.last() == '^')) {
            s = s.dropLast(1).trimEnd()
        }
        if (s.isEmpty()) return ""
        // Auto-balance trailing unclosed parentheses for live preview
        var openCount = 0
        for (ch in s) {
            if (ch == '(') openCount++
            else if (ch == ')') openCount--
        }
        if (openCount > 0) {
            // Strip empty trailing "()" like "5+("
            while (s.endsWith("(")) {
                s = s.dropLast(1).trimEnd()
                openCount--
                while (s.isNotEmpty() && (s.last() == '+' || s.last() == '-' || s.last() == '*' || s.last() == '/' || s.last() == '^')) {
                    s = s.dropLast(1).trimEnd()
                }
            }
            if (s.isEmpty()) return ""
            if (openCount > 0) {
                s += ")".repeat(openCount)
            }
        }
        return s
    }

    private fun normalizeZeroAndScale(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        // Round to 12 significant digits to clear recurring binary/division noise (e.g. 0.333333333333)
        val rounded = value.round(MathContext(12, RoundingMode.HALF_UP))
        return rounded.stripTrailingZeros()
    }

    private fun formatPlain(raw: BigDecimal): String {
        val v = normalizeZeroAndScale(raw)
        if (v.compareTo(BigDecimal.ZERO) == 0) return "0"
        val absDouble = abs(v.toDouble())
        return if (absDouble >= 1e15 || (absDouble > 0.0 && absDouble < 1e-9)) {
            v.toString()
        } else {
            v.toPlainString()
        }
    }

    private fun formatDisplay(raw: BigDecimal): String {
        val v = normalizeZeroAndScale(raw)
        if (v.compareTo(BigDecimal.ZERO) == 0) return "0"
        val absDouble = abs(v.toDouble())
        if (absDouble >= 1e15 || (absDouble > 0.0 && absDouble < 1e-9)) {
            val sci = DecimalFormat("0.######E0", DecimalFormatSymbols(Locale.US))
            return sci.format(v)
        }
        val plain = v.toPlainString()
        val isNeg = plain.startsWith("-")
        val unsigned = if (isNeg) plain.substring(1) else plain
        val dotIdx = unsigned.indexOf('.')
        val intPart = if (dotIdx >= 0) unsigned.substring(0, dotIdx) else unsigned
        val fracPart = if (dotIdx >= 0) unsigned.substring(dotIdx) else ""
        val groupedInt = buildString {
            val len = intPart.length
            for (i in intPart.indices) {
                append(intPart[i])
                val rem = len - 1 - i
                if (rem > 0 && rem % 3 == 0) append(',')
            }
        }
        return (if (isNeg) "-" else "") + groupedInt + fracPart
    }

    private data class TermResult(
        val value: BigDecimal,
        val isStandalonePercent: Boolean,
    )

    private class ExprParser(private val src: String) {
        private var pos = 0

        fun isAtEnd(): Boolean = pos >= src.length

        fun skipSpaces() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipSpaces()
            return if (pos < src.length) src[pos] else null
        }

        fun parseExpression(): BigDecimal {
            var left = parseTerm().value
            while (true) {
                val op = peek()
                if (op != '+' && op != '-') break
                pos++
                val rightTerm = parseTerm()
                val rightVal = if (rightTerm.isStandalonePercent) {
                    // Pocket-calculator behavior: 200 + 10% = 200 + (200 * 0.10) = 220
                    left.multiply(rightTerm.value, MC)
                } else {
                    rightTerm.value
                }
                left = if (op == '+') left.add(rightVal, MC) else left.subtract(rightVal, MC)
            }
            return left
        }

        private fun parseTerm(): TermResult {
            val first = parsePower()
            var value = first.value
            var standalonePercent = first.isStandalonePercent

            while (true) {
                val next = peek()
                when {
                    next == '*' || next == '/' -> {
                        pos++
                        val right = parsePower().value
                        value = if (next == '*') {
                            value.multiply(right, MC)
                        } else {
                            if (right.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("Division by zero")
                            value.divide(right, MC)
                        }
                        standalonePercent = false
                    }
                    // Implicit multiplication: `2(3+4)`, `(2+3)(4+5)`, `(2+3)4`, `50%(200)`
                    next == '(' -> {
                        val right = parsePower().value
                        value = value.multiply(right, MC)
                        standalonePercent = false
                    }
                    else -> break
                }
            }
            return TermResult(value, standalonePercent)
        }

        private fun parsePower(): TermResult {
            val base = parseUnary()
            if (peek() == '^') {
                pos++
                val exp = parsePower().value
                val baseD = base.value.toDouble()
                val expD = exp.toDouble()
                val powRes = baseD.pow(expD)
                if (powRes.isNaN() || powRes.isInfinite()) throw ArithmeticException("Overflow")
                return TermResult(BigDecimal(powRes.toString(), MC), isStandalonePercent = false)
            }
            return base
        }

        private fun parseUnary(): TermResult {
            val ch = peek()
            if (ch == '+') {
                pos++
                return parseUnary()
            }
            if (ch == '-') {
                pos++
                val inner = parseUnary()
                return TermResult(inner.value.negate(), inner.isStandalonePercent)
            }
            return parsePostfix()
        }

        private fun parsePostfix(): TermResult {
            var value = parsePrimary()
            var sawPercent = false
            while (peek() == '%') {
                pos++
                value = value.divide(HUNDRED, MC)
                sawPercent = true
            }
            // Handle implicit multiplication right after `)` like `(2+3)4`
            val after = peek()
            if (after != null && (after in '0'..'9' || after == '.')) {
                val factor = parsePrimary()
                value = value.multiply(factor, MC)
                sawPercent = false
            }
            return TermResult(value, sawPercent)
        }

        private fun parsePrimary(): BigDecimal {
            val ch = peek() ?: throw IllegalArgumentException("Unexpected end")
            if (ch == '(') {
                pos++
                val inner = parseExpression()
                if (peek() != ')') throw IllegalArgumentException("Missing )")
                pos++
                return inner
            }
            if (ch in '0'..'9' || ch == '.') {
                val start = pos
                var sawDot = false
                while (pos < src.length) {
                    val c = src[pos]
                    if (c in '0'..'9') {
                        pos++
                    } else if (c == '.' && !sawDot) {
                        sawDot = true
                        pos++
                    } else {
                        break
                    }
                }
                val token = src.substring(start, pos)
                if (token == ".") throw IllegalArgumentException("Invalid number")
                return BigDecimal(token)
            }
            throw IllegalArgumentException("Unexpected char: $ch")
        }
    }
}
