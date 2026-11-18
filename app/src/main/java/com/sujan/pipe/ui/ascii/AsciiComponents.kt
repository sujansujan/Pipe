package com.sujan.pipe.ui.ascii

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Section 5 of `ascii-app`:
 * `CellGrid` measures `"0"` in the single monospace font and passes the exact column count `cols` to content.
 */
@Composable
fun CellGrid(
    modifier: Modifier = Modifier,
    content: @Composable (cols: Int) -> Unit,
) {
    val c = LocalAscii.current
    val measurer = rememberTextMeasurer()
    val style = AsciiType.body
    val cellPx = remember(style) { measurer.measure("0", style).size.width.coerceAtLeast(1) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        val cols = (constraints.maxWidth / cellPx).coerceAtLeast(24)
        content(cols)
    }
}

/**
 * Section 5 of `ascii-app`:
 * `fun rule(cols: Int, ch: Char = '-') = ch.toString().repeat(cols)`
 */
fun rule(cols: Int, ch: Char = '-'): String = ch.toString().repeat(cols.coerceAtLeast(8))

/**
 * Section 5 of `ascii-app`:
 * `fun row(cols: Int, left: String, right: String): String`
 * Right-aligned column with ASCII `~` truncation when `left` exceeds available cell width.
 */
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

/**
 * Section 5 of `ascii-app`:
 * `fun bar(frac: Float, width: Int): String`
 */
fun bar(frac: Float, width: Int): String {
    val safeWidth = width.coerceAtLeast(6)
    val n = ((safeWidth - 2) * frac.coerceIn(0f, 1f)).roundToInt().coerceIn(0, safeWidth - 2)
    return "[" + "#".repeat(n) + "-".repeat(safeWidth - 2 - n) + "]"
}

/**
 * Section 5 of `ascii-app`:
 * `fun money(minor: Long): String = "%+,.2f".format(minor / 100.0)`
 */
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
 * Decorative horizontal ASCII rule (`---`).
 * Carries `Modifier.clearAndSetSemantics {}` per Section 10 so TalkBack never reads `"hyphen hyphen hyphen"`.
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
 * Section break = blank line, `UPPERCASE` label, and `---` rule.
 * Used on all normal screens (since boxes are reserved exclusively for Modals).
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
            val line = if (rightText.isNotEmpty()) {
                row(cols, title.uppercase(Locale.US), rightText.uppercase(Locale.US))
            } else {
                title.uppercase(Locale.US)
            }
            AsciiText(
                text = line,
                style = AsciiType.title,
                color = c.fg,
                softWrap = false,
                maxLines = 1,
            )
        }
        AsciiRule(glyph = "-", color = c.dim)
    }
}

/**
 * Section 6 of `ascii-app` Button vocabulary:
 * - Primary / Standard: `[ label ]`
 * - Secondary (`secondary = true`): `( label )`
 * - Destructive (`destructive = true`): `[ label! ]` in alert color
 * - Pressed state = inverted video (`bg = ink`, `fg = c.bg`), no ripple, 48dp minimum touch target, light haptic tick.
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
    val formatted = when {
        clean.startsWith("[") || clean.startsWith("(") -> clean
        destructive -> "[ ${clean.trimEnd('!')}! ]"
        secondary || clean.equals("cancel", ignoreCase = true) || clean.equals("close", ignoreCase = true) || clean.equals("keep", ignoreCase = true) -> "( $clean )"
        else -> "[ $clean ]"
    }
    val ink = when {
        !enabled -> c.dim
        destructive -> c.error
        primary -> c.fg
        secondary -> c.dim
        else -> c.fg
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 40.dp)
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
            style = if (primary || pressed || destructive) AsciiType.title else AsciiType.body,
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
    accentColor: Color = LocalAscii.current.fg,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val haptics = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inverted = pressed || selected
    val suffix = if (isDefault) "*" else ""
    val display = if (name != null) "[ $keyword$suffix $name ]" else "[ $keyword$suffix ]"

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
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
            color = if (inverted) c.bg else c.fg,
            style = if (inverted) AsciiType.title else AsciiType.body,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .background(if (inverted) c.fg else Color.Transparent)
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .clearAndSetSemantics {},
        )
    }
}

/**
 * Section 6 of `ascii-app` Toggle:
 * `[x] enabled` and `[ ] enabled` with `Role.Checkbox`, 48dp minimum height, and inverted video on press.
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
            .background(if (pressed) c.fg else Color.Transparent)
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
        val mark = if (checked) "[x]" else "[ ]"
        AsciiText(
            text = "$mark ${label.lowercase(Locale.US)}",
            style = if (checked) AsciiType.title else AsciiType.body,
            color = if (pressed) c.bg else c.fg,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (!subtitle.isNullOrBlank()) {
            AsciiText(
                text = "    ${subtitle.lowercase(Locale.US)}",
                style = AsciiType.body,
                color = if (pressed) c.bg else c.dim,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Section 6 of `ascii-app` Select:
 * `category: [ food v ]` opens a modal list (`+- category? ---+`).
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
            .background(if (pressed) c.fg else Color.Transparent)
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
            val line = row(cols, "$cleanLabel:", "[ $selectedLabel v ]")
            AsciiText(
                text = line,
                style = AsciiType.body,
                color = if (pressed) c.bg else c.fg,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        if (!subtitle.isNullOrBlank()) {
            AsciiText(
                text = "  ${subtitle.lowercase(Locale.US)}",
                style = AsciiType.body,
                color = if (pressed) c.bg else c.dim,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }

    if (modalOpen) {
        Dialog(onDismissRequest = { modalOpen = false }) {
            AsciiModalBox(
                title = "$cleanLabel?",
                onDismiss = { modalOpen = false },
            ) {
                options.forEach { (optVal, optLabel) ->
                    val isSel = optVal == selectedValue
                    val optSrc = remember { MutableInteractionSource() }
                    val optPressed by optSrc.collectIsPressedAsState()
                    val inverted = isSel || optPressed
                    val prefix = if (isSel) "> " else "  "
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(if (inverted) c.fg else Color.Transparent)
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
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = "$prefix${optLabel.lowercase(Locale.US)}",
                            style = if (isSel) AsciiType.title else AsciiType.body,
                            color = if (inverted) c.bg else c.fg,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AsciiButton(
                        label = "keep",
                        secondary = true,
                        onClick = { modalOpen = false },
                    )
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
    val inverted = selected || pressed
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (inverted) c.fg else Color.Transparent)
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
            color = if (inverted) c.bg else c.fg,
            style = if (selected) AsciiType.title else AsciiType.body,
        )
    }
}

/**
 * Section 6 of `ascii-app`:
 * Modal box (`+- title ---+` / `| ... |` / `+-----------+`).
 * In `ascii-app`: "Modal: the only place a box is allowed."
 * When `AsciiBox` is called on a normal screen (non-modal), it renders as a clean `AsciiSection`
 * (`UPPERCASE` title + `---` rule + blank line), whereas inside Modals (`AsciiModalBox` or `isModal = true`)
 * it renders the full `+---+\n|   |\n+---+` ASCII box!
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
 * Strict `ascii-app` Section 6 Modal Box:
 * ```
 * +- delete? --------------+
 * | remove 'Groceries'?    |
 * |                        |
 * |   [ delete ]  ( keep ) |
 * +------------------------+
 * ```
 */
@Composable
fun AsciiModalBox(
    title: String,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAscii.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(4.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        ) {
            val cols = rememberCols(AsciiType.body)
            val cleanTitle = title.lowercase(Locale.US).trim()
            val prefix = "+- $cleanTitle "
            val dashCount = (cols - prefix.length - 1).coerceAtLeast(2)
            val topRule = prefix + "-".repeat(dashCount) + "+"
            AsciiText(
                text = topRule,
                style = AsciiType.title,
                color = c.fg,
                softWrap = false,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = "| ",
                style = AsciiType.body,
                color = c.dim,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
            AsciiText(
                text = " |",
                style = AsciiType.body,
                color = c.dim,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
        ) {
            val cols = rememberCols(AsciiType.body)
            val bottomRule = "+" + "-".repeat((cols - 2).coerceAtLeast(6)) + "+"
            AsciiText(
                text = bottomRule,
                style = AsciiType.body,
                color = c.fg,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

/**
 * Section 2 & Section 6 Header:
 * `PIPE` in uppercase on the left, section/context in uppercase on the right, followed by a `---` rule.
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
            AsciiText(
                text = "PIPE  $rightContext",
                style = AsciiType.title,
                color = c.fg,
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
 * `label: value_` where `_` is a drawn, blinking caret and the native cursor is hidden (`SolidColor(Color.Transparent)`).
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
                style = AsciiType.body,
                color = c.dim,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                val displayWithCaret = if (value.text.isEmpty() && !isFocused && placeholder.isNotEmpty()) {
                    placeholder.lowercase(Locale.US)
                } else {
                    value.text + (if (isFocused) caret else "")
                }
                val displayColor = if (value.text.isEmpty() && !isFocused && placeholder.isNotEmpty()) c.dim else c.fg
                AsciiText(
                    text = displayWithCaret,
                    style = AsciiType.body,
                    color = displayColor,
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
        AsciiRule(glyph = "-", color = if (errorText != null) c.error else c.dim)
        if (errorText != null) {
            val normalizedErr = if (errorText.startsWith("err:")) {
                errorText.lowercase(Locale.US)
            } else {
                "err: " + errorText.trimStart('!', ' ').lowercase(Locale.US)
            }
            AsciiText(
                text = normalizedErr,
                style = AsciiType.body,
                color = c.error,
                maxLines = 1,
            )
        }
    }
}

/**
 * Section 6 of `ascii-app` One-Line Status Bar (`> saved.`, `err: ...`) and Bottom Command Bar (`[a]dd [f]ilter ...`).
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
        Column(modifier = modifier.fillMaxWidth().background(c.bg)) {
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
                    color = c.fg,
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
        val formatted = when {
            isErr && raw.startsWith("err:", ignoreCase = true) -> raw.lowercase(Locale.US)
            isErr -> "err: ${raw.trimStart('!', ' ').lowercase(Locale.US)}"
            raw.startsWith("ok:", ignoreCase = true) -> "> ${raw.removePrefix("ok:").trim().lowercase(Locale.US).trimEnd('.')}."
            raw.startsWith(">") -> raw.lowercase(Locale.US)
            else -> "> ${raw.lowercase(Locale.US).trimEnd('.')}."
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
                    color = if (isErr) c.error else c.fg,
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
                val inverted = active || pressed
                val cleanSpoken = label.replace("[", "").replace("]", "")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(if (inverted) c.fg else Color.Transparent)
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
                        style = if (inverted) AsciiType.title else AsciiType.body,
                        color = if (inverted) c.bg else c.fg,
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
