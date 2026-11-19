package com.sujan.pipe.ui.ascii

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Section 5 of `ascii-app`:
 * `data class GridMetrics(val cols: Int, val cellWidthPx: Int, val lineHeightPx: Int)`
 */
@Immutable
data class GridMetrics(
    val cols: Int,
    val cellWidthPx: Int,
    val lineHeightPx: Int,
)

val LocalGridMetrics = compositionLocalOf { GridMetrics(cols = 36, cellWidthPx = 24, lineHeightPx = 42) }

/**
 * Section 5 of `ascii-app`:
 * `CellGrid` measures `"0"` in the single monospace font and provides `GridMetrics(cols, cellWidthPx, lineHeightPx)`.
 */
@Composable
fun CellGrid(
    modifier: Modifier = Modifier,
    content: @Composable (GridMetrics) -> Unit,
) {
    val c = LocalAscii.current
    val measurer = rememberTextMeasurer()
    val style = AsciiType.body
    val metrics = remember(style) {
        val m = measurer.measure("0", style).size
        Pair(m.width.coerceAtLeast(1), m.height.coerceAtLeast(1))
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        val cols = (constraints.maxWidth / metrics.first).coerceAtLeast(24)
        val grid = GridMetrics(cols = cols, cellWidthPx = metrics.first, lineHeightPx = metrics.second)
        CompositionLocalProvider(LocalGridMetrics provides grid) {
            content(grid)
        }
    }
}

/**
 * Section 5 of `ascii-app`: Grid-Snapped Images (`CellImage`).
 * Snaps image dimensions to exact multiples of `grid.cellWidthPx` and `grid.lineHeightPx`
 * with sharp `0.dp` rectangular edges (`border(1.dp, c.dim)`) and an ASCII fallback/placeholder.
 */
const val UNIFIED_THUMB_COLS = 4
const val UNIFIED_THUMB_ROWS = 2

@Composable
fun CellImage(
    painter: Painter?,
    contentDescription: String,
    grid: GridMetrics = LocalGridMetrics.current,
    cellCols: Int = UNIFIED_THUMB_COLS,
    cellRows: Int = UNIFIED_THUMB_ROWS,
    caption: String? = null,
    placeholderText: String = "..",
    placeholderColor: Color = LocalAscii.current.cyan,
    fitInside: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val density = LocalDensity.current
    val widthDp = with(density) { (cellCols * grid.cellWidthPx).toDp() }
    val heightDp = with(density) { (cellRows * grid.lineHeightPx).toDp() }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(width = widthDp, height = heightDp)
                .background(c.surface)
                .border(1.dp, if (painter != null) c.dim else placeholderColor.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    contentScale = if (fitInside) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (fitInside) Modifier.fillMaxSize().padding(4.dp) else Modifier.fillMaxSize(),
                )
            } else {
                AsciiText(
                    text = placeholderText.take(cellCols.coerceAtLeast(3)),
                    style = AsciiType.title,
                    color = placeholderColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        if (caption != null) {
            AsciiText(
                text = "[img: ${caption.take((cellCols - 7).coerceAtLeast(8))}]",
                style = AsciiType.body,
                color = c.dim,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun CellBitmapImage(
    bitmap: ImageBitmap?,
    contentDescription: String,
    grid: GridMetrics = LocalGridMetrics.current,
    cellCols: Int = UNIFIED_THUMB_COLS,
    cellRows: Int = UNIFIED_THUMB_ROWS,
    caption: String? = null,
    placeholderText: String = "..",
    placeholderColor: Color = LocalAscii.current.cyan,
    fitInside: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val painter = remember(bitmap) { bitmap?.let { BitmapPainter(it) } }
    CellImage(
        painter = painter,
        contentDescription = contentDescription,
        grid = grid,
        cellCols = cellCols,
        cellRows = cellRows,
        caption = caption,
        placeholderText = placeholderText,
        placeholderColor = placeholderColor,
        fitInside = fitInside,
        modifier = modifier,
    )
}

fun rule(cols: Int, ch: Char = '-'): String = ch.toString().repeat(cols.coerceAtLeast(8))

fun row(cols: Int, left: String, right: String): String {
    val safeCols = cols.coerceAtLeast(right.length + 4)
    val availLeft = (safeCols - right.length - 1).coerceAtLeast(1)
    val l = if (left.length > availLeft) {
        left.take((availLeft - 1).coerceAtLeast(1)) + "~"
    } else {
        left
    }
    return l.padEnd((safeCols - right.length).coerceAtLeast(l.length), ' ') + right
}

fun bar(frac: Float, width: Int): String {
    val safeWidth = width.coerceAtLeast(6)
    val n = ((safeWidth - 2) * frac.coerceIn(0f, 1f)).roundToInt().coerceIn(0, safeWidth - 2)
    return "[" + "#".repeat(n) + "-".repeat(safeWidth - 2 - n) + "]"
}

fun money(minor: Long): String = String.format(Locale.US, "%+,.2f", minor / 100.0)

/**
 * Stepped blinking `_` caret (`530ms` step interval; static `_` when reduced motion is enabled).
 */
@Composable
fun rememberBlinkingCaret(active: Boolean = true): String {
    val reducedMotion = isReducedMotionEnabled()
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(active, reducedMotion) {
        if (!active || reducedMotion) {
            visible = true
            return@LaunchedEffect
        }
        while (true) {
            delay(530L)
            visible = !visible
        }
    }
    return if (visible) "_" else " "
}

/**
 * Decorative horizontal ASCII rule (`---`) in `dim`.
 */
@Composable
fun AsciiRule(
    modifier: Modifier = Modifier,
    glyph: String = "-",
    color: Color = LocalAscii.current.dim,
) {
    val safeChar = glyph.firstOrNull()?.let { ch ->
        if (ch.code in 0x20..0x7E) ch else '-'
    } ?: '-'
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        val cols = rememberCols(AsciiType.body)
        AsciiText(
            text = rule(cols, safeChar),
            style = AsciiType.body,
            color = color,
            softWrap = false,
            maxLines = 1,
        )
    }
}

/**
 * Section 6 of `ascii-app`:
 * Section = blank line, `UPPERCASE` label in `purple` (bold), optional `---` rule in `dim`.
 */
@Composable
fun AsciiSection(
    title: String,
    rightText: String = "",
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.body)
            val leftUp = title.uppercase(Locale.US)
            val rightUp = rightText.uppercase(Locale.US)
            if (rightUp.isNotEmpty()) {
                val full = row(cols, leftUp, rightUp)
                val splitIdx = (full.length - rightUp.length).coerceAtLeast(0)
                val annotated = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.purple, fontWeight = FontWeight.Bold)) {
                        append(full.substring(0, splitIdx))
                    }
                    withStyle(SpanStyle(color = c.orange, fontWeight = FontWeight.Bold)) {
                        append(full.substring(splitIdx))
                    }
                }
                AsciiText(
                    text = annotated,
                    style = AsciiType.title,
                    softWrap = false,
                    maxLines = 1,
                )
            } else {
                AsciiText(
                    text = leftUp,
                    style = AsciiType.title,
                    color = c.purple,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
        AsciiRule(glyph = "-", color = c.dim)
    }
}

/**
 * Section 6 of `ascii-app` Button vocabulary:
 * - Primary (`primary = true`): `[ label ]` in `pink`
 * - Standard: `[ label ]` in `cyan`
 * - Secondary (`secondary = true`): `( label )` in `dim`
 * - Destructive (`destructive = true`): `[ label! ]` in `red`
 * - Pressed state = `surface` / inverted highlight, zero ripple, light haptic tick.
 */
@Composable
fun AsciiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    secondary: Boolean = false,
    destructive: Boolean = false,
    spokenLabel: String = label.trim('[', ']', '(', ')', '!', ' '),
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()

    val clean = label.trim()
    val isSec = secondary || clean.equals("cancel", ignoreCase = true) || clean.equals("close", ignoreCase = true) || clean.equals("keep", ignoreCase = true)
    val isDest = destructive || clean.equals("del", ignoreCase = true) || clean.equals("delete", ignoreCase = true) || clean.equals("unin", ignoreCase = true) || clean.equals("uninstall", ignoreCase = true)
    val formatted = when {
        clean.startsWith("[") || clean.startsWith("(") -> clean
        isDest -> "[ ${clean.trimEnd('!')}! ]"
        isSec -> "( $clean )"
        else -> "[ $clean ]"
    }
    val ink = when {
        !enabled -> c.dim
        isDest -> c.red
        primary -> c.pink
        isSec -> c.dim
        else -> c.cyan
    }

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 36.dp)
            .clickable(
                interactionSource = src,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onClick()
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spokenLabel
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = formatted,
            color = if (pressed) c.bg else ink,
            style = if (primary || pressed || isDest) AsciiType.title else AsciiType.body,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .background(if (pressed) ink else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
        )
    }
}

@Composable
fun AsciiPipePill(
    keyword: String,
    name: String? = null,
    selected: Boolean = false,
    isDefault: Boolean = false,
    accentColor: Color = LocalAscii.current.cyan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inverted = pressed || selected
    val ink = if (selected) c.pink else accentColor
    val suffix = if (isDefault) "*" else ""
    val display = if (name != null) "[ $keyword$suffix $name ]" else "[ $keyword$suffix ]"

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = src,
                indication = null,
                role = Role.Button,
                onClick = {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onClick()
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "pipe to ${name ?: keyword}, keyword $keyword"
            }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = display,
            color = if (inverted) c.bg else ink,
            style = if (inverted) AsciiType.title else AsciiType.body,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .background(if (inverted) ink else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
        )
    }
}

/**
 * Section 6 of `ascii-app` Toggle:
 * `[x] enabled` (`[x]` in `green`) and `[ ] enabled` (`[ ]` in `dim`).
 */
@Composable
fun AsciiToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val stateWord = if (checked) "enabled" else "disabled"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (pressed) c.surface else Color.Transparent)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { next ->
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    onCheckedChange(next)
                },
                interactionSource = src,
                indication = null,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $stateWord"
            }
            .padding(vertical = 6.dp),
    ) {
        val annotated = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = if (checked) c.green else c.dim,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                )
            ) {
                append(if (checked) "[x] " else "[ ] ")
            }
            withStyle(
                SpanStyle(
                    color = c.fg,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                )
            ) {
                append(label.lowercase(Locale.US))
            }
        }
        AsciiText(
            text = annotated,
            style = AsciiType.body,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (!subtitle.isNullOrBlank()) {
            AsciiText(
                text = "    ${subtitle.lowercase(Locale.US)}",
                style = AsciiType.body,
                color = c.dim,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Section 6 of `ascii-app` Select:
 * `category: [ food v ]` with bracketed value in `cyan`; opens a modal list (`+- category? ---+`).
 */
@Composable
fun <T> AsciiDropdownRow(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var modalOpen by remember { mutableStateOf(false) }

    val selectedLabel = (options.firstOrNull { it.first == selectedValue }?.second
        ?: options.firstOrNull()?.second.orEmpty()).lowercase(Locale.US)
    val cleanLabel = label.lowercase(Locale.US)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (pressed) c.surface else Color.Transparent)
            .clickable(
                interactionSource = src,
                indication = null,
                role = Role.Button,
                onClick = {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    modalOpen = true
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$cleanLabel: $selectedLabel"
            }
            .padding(vertical = 6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.body)
            val rightToken = "[ $selectedLabel v ]"
            val full = row(cols, "$cleanLabel:", rightToken)
            val splitIdx = (full.length - rightToken.length).coerceAtLeast(0)
            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(color = c.purple)) {
                    append(full.substring(0, splitIdx))
                }
                withStyle(SpanStyle(color = c.cyan, fontWeight = FontWeight.Bold)) {
                    append(full.substring(splitIdx))
                }
            }
            AsciiText(
                text = annotated,
                style = AsciiType.body,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (!subtitle.isNullOrBlank()) {
            AsciiText(
                text = "  ${subtitle.lowercase(Locale.US)}",
                style = AsciiType.body,
                color = c.dim,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }

    if (modalOpen) {
        Dialog(onDismissRequest = { modalOpen = false }) {
            AsciiModalBox(
                title = cleanLabel,
                onDismiss = { modalOpen = false },
            ) {
                options.forEach { (optVal, optLabel) ->
                    val isSel = optVal == selectedValue
                    val optSrc = remember { MutableInteractionSource() }
                    val optPressed by optSrc.collectIsPressedAsState()
                    val highlighted = isSel || optPressed
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = if (isSel) c.pink else c.dim, fontWeight = FontWeight.Bold)) {
                            append(if (isSel) "[*] " else "[ ] ")
                        }
                        withStyle(
                            SpanStyle(
                                color = if (isSel) c.cyan else c.fg,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            )
                        ) {
                            append(optLabel.lowercase(Locale.US))
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(if (highlighted) c.bg else Color.Transparent)
                            .clickable(
                                interactionSource = optSrc,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                    modalOpen = false
                                    onSelect(optVal)
                                },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = optLabel.lowercase(Locale.US) + (if (isSel) ", selected" else "")
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = annotated,
                            style = AsciiType.body,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AsciiCheckbox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) = AsciiToggleRow(
    label = label,
    checked = checked,
    onCheckedChange = onChange,
    modifier = modifier,
)

@Composable
fun AsciiRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val highlighted = selected || pressed
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (highlighted) c.surface else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
                interactionSource = src,
                indication = null,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = if (selected) "> $label" else "  $label",
            color = if (selected) c.purple else c.fg,
            style = if (selected) AsciiType.title else AsciiType.body,
        )
    }
}

/**
 * Section 6 of `ascii-app`:
 * On normal screens, sections render as `AsciiSection` (`UPPERCASE` in `purple` bold + `---` rule in `dim`).
 * Inside Modals (`isModal = true`), renders `AsciiModalBox` (`surface` background, `purple`/`dim` `+---+` box border).
 */
@Composable
fun AsciiBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    rightActionLabel: String? = null,
    onRightAction: (() -> Unit)? = null,
    isModal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isModal) {
        AsciiModalBox(
            title = title ?: "pipe",
            onDismiss = onRightAction,
            modifier = modifier,
            content = content,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (title != null) {
            AsciiSection(
                title = title,
                rightText = rightActionLabel.orEmpty(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            content = content,
        )
    }
}

/**
 * Section 6 of `ascii-app` Modal Box:
 * `surface` background with `purple` / `dim` ASCII `+---+` border.
 */
@Composable
fun AsciiModalBox(
    title: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAscii.current
    val cleanTitle = title.lowercase(Locale.US).trim().removeSuffix("?")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.purple)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = cleanTitle.uppercase(Locale.US),
                style = AsciiType.title,
                color = c.purple,
                maxLines = 1,
            )
            if (onDismiss != null) {
                AsciiButton(
                    label = "x",
                    secondary = true,
                    onClick = onDismiss,
                    spokenLabel = "close $cleanTitle",
                )
            }
        }
        AsciiRule(glyph = "-", color = c.dim)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * Section 6 of `ascii-app` Header:
 * `PIPE` in `purple` bold, context/section in `orange` right-aligned, then a `---` rule in `dim`.
 */
@Composable
fun AsciiTopHeader(
    path: String,
    actions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val rightContext = path
        .removePrefix("~/pipe/")
        .removePrefix("~/pipe")
        .trim('/')
        .ifEmpty { "PROMPT" }
        .uppercase(Locale.US)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val headerAnn = buildAnnotatedString {
                withStyle(SpanStyle(color = c.purple, fontWeight = FontWeight.Bold)) {
                    append("PIPE  ")
                }
                withStyle(SpanStyle(color = c.orange, fontWeight = FontWeight.Bold)) {
                    append(rightContext)
                }
            }
            AsciiText(
                text = headerAnn,
                style = AsciiType.title,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        AsciiRule(glyph = "-", color = c.dim)
    }
}

/**
 * Section 6 of `ascii-app` Input:
 * `amount: 1,250.00_` where label is `purple`, value is `fg`, and `_` is a drawn, blinking caret in `pink`.
 * Native caret is hidden (`cursorBrush = SolidColor(Color.Transparent)`).
 */
@Composable
fun AsciiTextField(
    label: String?,
    promptPrefix: String = "",
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    trailingControl: (@Composable () -> Unit)? = null,
) {
    val c = LocalAscii.current
    var isFocused by remember { mutableStateOf(false) }
    val caret = rememberBlinkingCaret(active = isFocused)
    val cleanLabel = label?.lowercase(Locale.US)?.trim()?.removeSuffix(":")
    val prefixStr = when {
        !cleanLabel.isNullOrEmpty() -> "$cleanLabel: "
        promptPrefix.isNotEmpty() -> promptPrefix
        else -> "> "
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = prefixStr,
                style = AsciiType.title,
                color = c.purple,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val annotatedValue = buildAnnotatedString {
                    if (value.text.isEmpty() && !isFocused && placeholder.isNotEmpty()) {
                        withStyle(SpanStyle(color = c.dim)) {
                            append(placeholder.lowercase(Locale.US))
                        }
                    } else {
                        withStyle(SpanStyle(color = c.fg)) {
                            append(value.text)
                        }
                        if (isFocused) {
                            withStyle(SpanStyle(color = c.pink, fontWeight = FontWeight.Bold)) {
                                append(caret)
                            }
                        }
                    }
                }
                AsciiText(
                    text = annotatedValue,
                    style = AsciiType.body,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                val fMod = if (focusRequester != null) {
                    Modifier.fillMaxWidth().focusRequester(focusRequester)
                } else {
                    Modifier.fillMaxWidth()
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = fMod
                        .onFocusChanged { isFocused = it.isFocused }
                        .semantics { contentDescription = cleanLabel ?: "input" },
                    textStyle = AsciiType.body.copy(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
            if (trailingControl != null) {
                trailingControl()
            }
        }
        AsciiRule(glyph = "-", color = if (errorText != null) c.red else c.dim)
        if (errorText != null) {
            val normalizedErr = if (errorText.startsWith("err:")) {
                errorText.lowercase(Locale.US)
            } else {
                "err: " + errorText.trimStart('!', ' ').lowercase(Locale.US)
            }
            AsciiText(
                text = normalizedErr,
                style = AsciiType.body,
                color = c.red,
                maxLines = 1,
            )
        }
    }
}

/**
 * Section 6 of `ascii-app` Feedback:
 * One-line status bar at the bottom (`> saved.` in `green`, `err:` in `red`, `warn:` in `yellow`).
 */
@Composable
fun AsciiStatusBar(
    message: String?,
    undoMessage: String? = null,
    onUndo: (() -> Unit)? = null,
    onDismissUndo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    if (undoMessage != null && onUndo != null) {
        Column(modifier = modifier.fillMaxWidth().background(c.surface)) {
            AsciiRule(glyph = "-", color = c.dim)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = "> ${undoMessage.lowercase(Locale.US)}.",
                    style = AsciiType.body,
                    color = c.yellow,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                AsciiButton(label = "[u]ndo", onClick = onUndo, primary = true, spokenLabel = "undo $undoMessage")
                if (onDismissUndo != null) {
                    AsciiButton(label = "x", secondary = true, onClick = onDismissUndo, spokenLabel = "dismiss")
                }
            }
        }
        return
    }
    if (!message.isNullOrBlank()) {
        val raw = message.trim()
        val isErr = raw.startsWith("!") || raw.startsWith("err:", ignoreCase = true)
        val isWarn = raw.startsWith("warn:", ignoreCase = true)
        val formatted = when {
            isErr && raw.startsWith("err:", ignoreCase = true) -> raw.lowercase(Locale.US)
            isErr -> "err: ${raw.trimStart('!', ' ').lowercase(Locale.US)}"
            raw.startsWith("ok:", ignoreCase = true) -> "> ${raw.removePrefix("ok:").trim().lowercase(Locale.US).trimEnd('.')}."
            raw.startsWith(">") -> raw.lowercase(Locale.US)
            else -> "> ${raw.lowercase(Locale.US).trimEnd('.')}."
        }
        val statusCol = when {
            isErr -> c.red
            isWarn -> c.yellow
            else -> c.green
        }
        Column(modifier = modifier.fillMaxWidth().background(c.bg)) {
            AsciiRule(glyph = "-", color = c.dim)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AsciiText(
                    text = formatted,
                    style = AsciiType.body,
                    color = statusCol,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun AsciiCommandBar(
    items: List<Triple<String, Boolean, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth().background(c.bg)) {
        AsciiRule(glyph = "-", color = c.dim)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (label, active, onClick) ->
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val highlighted = active || pressed
                val cleanSpoken = label.replace("[", "").replace("]", "")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(if (highlighted) c.surface else Color.Transparent)
                        .clickable(
                            interactionSource = src,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                onClick()
                            },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = cleanSpoken + (if (active) ", active" else "")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsciiText(
                        text = label,
                        style = if (highlighted) AsciiType.title else AsciiType.body,
                        color = if (active) c.pink else if (pressed) c.cyan else c.fg,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
fun AsciiUndoBanner(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) = AsciiStatusBar(
    message = null,
    undoMessage = message,
    onUndo = onUndo,
    onDismissUndo = onDismiss,
    modifier = modifier,
)

@Composable
fun BoxWithConstraintsScope.rememberCols(style: TextStyle = AsciiType.body): Int {
    val tm = rememberTextMeasurer()
    val cellPx = remember(style) { tm.measure("0", style).size.width.coerceAtLeast(1) }
    return (constraints.maxWidth / cellPx).coerceAtLeast(24)
}

fun leader(key: String, value: String, cols: Int): String =
    row(cols, key.lowercase(Locale.US), value)

@Composable
fun AsciiFrameDivider(
    leftLabel: String,
    rightLabel: String? = null,
) = AsciiSection(
    title = leftLabel,
    rightText = rightLabel.orEmpty(),
)

fun frame(title: String, lines: List<String>, cols: Int): String {
    val safeCols = cols.coerceAtLeast(20)
    val inner = safeCols - 2
    val top = "+- ${title.lowercase(Locale.US)} ".take(safeCols - 1).padEnd(safeCols - 1, '-') + "+"
    val body = lines.map { "| " + it.take(inner - 2).padEnd(inner - 2, ' ') + " |" }
    val bottom = "+" + "-".repeat(inner) + "+"
    return (listOf(top) + body + bottom).joinToString("\n")
}

/**
 * 1-tap inline segmented option selector (`label:` followed by `[opt1] [*opt2*] [opt3]`).
 * Eliminates modal dropdown clicks for 2-4 option settings.
 */
@Composable
fun <T> AsciiInlineSegmentedRow(
    label: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val cleanLabel = label.lowercase(Locale.US)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        AsciiText(
            text = "$cleanLabel:",
            style = AsciiType.body,
            color = c.purple,
            maxLines = 1,
        )
        if (!subtitle.isNullOrBlank()) {
            AsciiText(
                text = "  ${subtitle.lowercase(Locale.US)}",
                style = AsciiType.body,
                color = c.dim,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { (optVal, optLabel) ->
                val isSel = optVal == selectedValue
                val src = remember { MutableInteractionSource() }
                val pressed by src.collectIsPressedAsState()
                val active = isSel || pressed
                val text = if (isSel) "[*${optLabel.lowercase(Locale.US)}*]" else "[${optLabel.lowercase(Locale.US)}]"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .background(if (active) c.surface else Color.Transparent)
                        .clickable(
                            interactionSource = src,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                runCatching { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                onSelect(optVal)
                            },
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$cleanLabel $optLabel" + (if (isSel) ", selected" else "")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsciiText(
                        text = text,
                        style = if (active) AsciiType.title else AsciiType.body,
                        color = if (isSel) c.pink else if (pressed) c.cyan else c.dim,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}
