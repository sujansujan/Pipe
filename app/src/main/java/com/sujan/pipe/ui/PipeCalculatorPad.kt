package com.sujan.pipe.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sujan.pipe.domain.CalcTapeEntry
import com.sujan.pipe.domain.PipeCalculator
import com.sujan.pipe.ui.ascii.AsciiButton
import com.sujan.pipe.ui.ascii.AsciiRule
import com.sujan.pipe.ui.ascii.AsciiText
import com.sujan.pipe.ui.ascii.AsciiType
import com.sujan.pipe.ui.ascii.LocalAscii

/**
 * Built-in Pipe T9 Calculator Keypad (`0–9`, `+ − × ÷ % ()`, `⌫`, `C`, `=`, `copy`, plus `[abc]` to return to QWERTY).
 * Shared between the main app (`PipeApp.kt`) and the floating overlay (`PipeOverlayActivity.kt`).
 */
@Composable
fun PipeCalculatorT9Panel(
    expression: String,
    tape: List<CalcTapeEntry>,
    onExpressionChange: (String) -> Unit,
    onCommitResult: (CalcTapeEntry) -> Unit,
    onCopyResult: (String) -> Unit,
    onExitToQwerty: () -> Unit,
    modifier: Modifier = Modifier,
    isOverlayStyle: Boolean = false,
    keySurfaceColor: Color? = null,
    keyBorderColor: Color? = null,
    cornerRadiusDp: Int = 0,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val eval = remember(expression) { PipeCalculator.evaluate(expression) }
    val prettyExpr = remember(expression) { PipeCalculator.prettifyExpression(expression) }
    val exprScroll = rememberScrollState()
    val tapeScroll = rememberScrollState()

    LaunchedEffect(prettyExpr) {
        exprScroll.scrollTo(exprScroll.maxValue)
    }

    val panelRadius = if (isOverlayStyle) cornerRadiusDp.coerceIn(4, 14).dp else 0.dp
    val keyRadius = if (isOverlayStyle) (cornerRadiusDp / 2).coerceIn(4, 10).dp else 0.dp
    val baseKeyBg = keySurfaceColor ?: c.surface
    val baseBorder = keyBorderColor ?: c.border

    fun buzz() {
        runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(panelRadius))
            .background(if (isOverlayStyle) Color.Transparent else c.bg)
            .padding(horizontal = if (isOverlayStyle) 2.dp else 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Top bar: [abc] back to QWERTY + title + [copy]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AsciiButton(
                    label = "abc",
                    onClick = {
                        buzz()
                        onExitToQwerty()
                    },
                    spokenLabel = "switch back to qwerty keyboard",
                )
                AsciiText(
                    text = "[=:calc t9]",
                    style = AsciiType.small,
                    color = c.accent,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (eval.isValid) {
                    AsciiButton(
                        label = "copy ${eval.formattedValue.take(12)}",
                        primary = true,
                        onClick = {
                            buzz()
                            onCopyResult(eval.plainValue)
                        },
                        spokenLabel = "copy result ${eval.plainValue}",
                    )
                }
            }
        }

        // Optional recent tape chips (tap any past result to insert it)
        if (tape.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tapeScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tape.take(6).forEach { entry ->
                    val chipShape = RoundedCornerShape(keyRadius)
                    Row(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(baseKeyBg, chipShape)
                            .border(1.dp, baseBorder, chipShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    buzz()
                                    val next = if (expression.isEmpty()) {
                                        entry.plainValue
                                    } else {
                                        expression + entry.plainValue
                                    }
                                    onExpressionChange(next)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = "${PipeCalculator.prettifyExpression(entry.expression)} = ${entry.formattedValue}",
                            style = AsciiType.small,
                            color = c.dim,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Display box: scrollable expression + live answer preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(keyRadius))
                .background(baseKeyBg, RoundedCornerShape(keyRadius))
                .border(1.dp, baseBorder, RoundedCornerShape(keyRadius))
                .clickable(
                    enabled = eval.isValid,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (eval.isValid) {
                            buzz()
                            onCopyResult(eval.plainValue)
                        }
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(exprScroll),
                horizontalArrangement = Arrangement.End,
            ) {
                AsciiText(
                    text = prettyExpr.ifEmpty { "0" },
                    style = AsciiType.title.copy(fontSize = 20.sp, lineHeight = 26.sp),
                    color = if (prettyExpr.isEmpty()) c.dim else c.fg,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(4.dp))
            val resultLine = when {
                expression.isEmpty() -> "tap keys below • tap result to copy"
                eval.isValid -> "= ${eval.formattedValue}"
                eval.errorMessage != null -> "! ${eval.errorMessage}"
                else -> "..."
            }
            val resultColor = when {
                expression.isEmpty() -> c.dim
                eval.isValid -> c.good
                eval.errorMessage != null -> c.error
                else -> c.dim
            }
            AsciiText(
                text = resultLine,
                style = if (eval.isValid) {
                    AsciiType.title.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                } else {
                    AsciiType.small
                },
                color = resultColor,
                maxLines = 1,
                softWrap = false,
            )
        }

        if (!isOverlayStyle) {
            AsciiRule(glyph = "-", color = baseBorder)
        }

        // 5x4 T9 Calculator Keypad Grid
        val keyHeight = if (isOverlayStyle) 48.dp else 54.dp
        val keyGap = 6.dp

        // Row 1: C | ( ) | % | ÷
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            CalcKeyButton(
                label = "C",
                fg = c.error,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange("") },
            )
            CalcKeyButton(
                label = "( )",
                fg = c.accent2,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendSmartParen(expression)) },
            )
            CalcKeyButton(
                label = "%",
                fg = c.accent2,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendPercent(expression)) },
            )
            CalcKeyButton(
                label = "÷",
                fg = c.accent,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendOperator(expression, '÷')) },
            )
        }

        // Row 2: 7 | 8 | 9 | ×
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            listOf('7', '8', '9').forEach { d ->
                CalcKeyButton(
                    label = d.toString(),
                    fg = c.fg,
                    bg = baseKeyBg,
                    border = baseBorder,
                    radius = keyRadius,
                    minHeight = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { buzz(); onExpressionChange(PipeCalculator.appendDigit(expression, d)) },
                )
            }
            CalcKeyButton(
                label = "×",
                fg = c.accent,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendOperator(expression, '×')) },
            )
        }

        // Row 3: 4 | 5 | 6 | −
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            listOf('4', '5', '6').forEach { d ->
                CalcKeyButton(
                    label = d.toString(),
                    fg = c.fg,
                    bg = baseKeyBg,
                    border = baseBorder,
                    radius = keyRadius,
                    minHeight = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { buzz(); onExpressionChange(PipeCalculator.appendDigit(expression, d)) },
                )
            }
            CalcKeyButton(
                label = "−",
                fg = c.accent,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendOperator(expression, '−')) },
            )
        }

        // Row 4: 1 | 2 | 3 | +
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            listOf('1', '2', '3').forEach { d ->
                CalcKeyButton(
                    label = d.toString(),
                    fg = c.fg,
                    bg = baseKeyBg,
                    border = baseBorder,
                    radius = keyRadius,
                    minHeight = keyHeight,
                    modifier = Modifier.weight(1f),
                    onClick = { buzz(); onExpressionChange(PipeCalculator.appendDigit(expression, d)) },
                )
            }
            CalcKeyButton(
                label = "+",
                fg = c.accent,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendOperator(expression, '+')) },
            )
        }

        // Row 5: ⌫ (long-press clear) | 0 | . | =
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            CalcKeyButton(
                label = "⌫",
                fg = c.warn,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.backspace(expression)) },
                onLongClick = { buzz(); onExpressionChange("") },
            )
            CalcKeyButton(
                label = "0",
                fg = c.fg,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendDigit(expression, '0')) },
            )
            CalcKeyButton(
                label = ".",
                fg = c.fg,
                bg = baseKeyBg,
                border = baseBorder,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = { buzz(); onExpressionChange(PipeCalculator.appendDecimal(expression)) },
            )
            CalcKeyButton(
                label = "=",
                fg = c.bg,
                bg = c.accent,
                border = c.accent,
                radius = keyRadius,
                minHeight = keyHeight,
                modifier = Modifier.weight(1f),
                onClick = {
                    buzz()
                    if (eval.isValid) {
                        val entry = CalcTapeEntry(
                            expression = expression,
                            plainValue = eval.plainValue,
                            formattedValue = eval.formattedValue,
                        )
                        onCommitResult(entry)
                        onExpressionChange(eval.plainValue)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalcKeyButton(
    label: String,
    fg: Color,
    bg: Color,
    border: Color,
    radius: androidx.compose.ui.unit.Dp,
    minHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (pressed) c.accent else bg, shape)
            .border(1.dp, if (pressed) c.accent else border, shape)
            .combinedClickable(
                interactionSource = src,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = label,
            style = AsciiType.title.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            color = if (pressed) c.bg else fg,
            maxLines = 1,
            softWrap = false,
        )
    }
}
