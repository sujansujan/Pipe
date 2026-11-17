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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * Full-width monospace character rule (`──────────` or `· · · · ·`) with zero CSS rectangle borders.
 */
@Composable
fun AsciiRule(
    modifier: Modifier = Modifier,
    glyph: String = "─",
    color: Color = LocalAscii.current.border,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cols = rememberCols(AsciiType.small)
        val count = (cols / glyph.length.coerceAtLeast(1)).coerceAtLeast(8)
        AsciiText(
            text = glyph.repeat(count).take(cols),
            style = AsciiType.small,
            color = color,
            softWrap = false,
            maxLines = 1,
        )
    }
}

/**
 * Pure bracketed ASCII button (`[ label ]`) with zero drawn rectangle borders.
 * Pressing inverts the text span `[ label ]` like a terminal cursor selection.
 */
@Composable
fun AsciiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    spokenLabel: String = label,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val ink = when {
        !enabled -> c.dim
        primary -> c.accent
        else -> c.fg
    }
    Box(
        modifier = modifier
            .heightIn(min = 42.dp)
            .widthIn(min = 38.dp)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = if (pressed) "[*$label*]" else "[$label]",
            color = if (pressed) c.bg else ink,
            style = if (primary || pressed) AsciiType.title else AsciiType.body,
            modifier = Modifier
                .background(if (pressed) ink else Color.Transparent)
                .padding(horizontal = 2.dp)
                .clearAndSetSemantics { contentDescription = spokenLabel },
        )
    }
}

@Composable
fun AsciiPipePill(
    keyword: String,
    name: String? = null,
    selected: Boolean = false,
    isDefault: Boolean = false,
    accentColor: Color = LocalAscii.current.accent2,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val inverted = pressed || selected
    val ink = if (selected) c.accent else accentColor
    val suffix = if (isDefault) "*" else ""
    val display = if (name != null) "[|$keyword$suffix $name]" else "[|$keyword$suffix]"

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = display,
            color = if (inverted) c.bg else ink,
            style = if (selected) AsciiType.title else AsciiType.small,
            modifier = Modifier
                .background(if (inverted) ink else Color.Transparent)
                .padding(horizontal = 2.dp)
                .clearAndSetSemantics {
                    contentDescription = "pipe to ${name ?: keyword}, keyword $keyword"
                },
        )
    }
}

/**
 * Pure ASCII toggle row (`label ...................... [*ON*]` / `[OFF]`) with zero rectangle boxes.
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
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
                interactionSource = src,
                indication = null,
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            AsciiText(
                text = label,
                style = AsciiType.body,
                color = if (pressed) c.accent else c.fg,
            )
            if (!subtitle.isNullOrBlank()) {
                AsciiText(
                    text = "  └─ $subtitle",
                    style = AsciiType.small,
                    color = c.dim,
                )
            }
        }
        AsciiText(
            text = if (checked) "[*ON*]" else "[ OFF]",
            style = AsciiType.title,
            color = if (checked) c.accent else c.dim,
            modifier = Modifier
                .background(if (pressed) c.surface else Color.Transparent)
                .padding(horizontal = 2.dp),
        )
    }
}

/**
 * Pure ASCII collapsible selector (`label .......... [ Selected v ]`) with tree-branch options (`├─`, `└──`).
 * Zero drawn rectangle boxes.
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
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: options.firstOrNull()?.second.orEmpty()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.DropdownList,
                    onClick = { expanded = !expanded },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(0.48f).padding(end = 6.dp)) {
                AsciiText(text = label, style = AsciiType.body, color = c.fg)
                if (!subtitle.isNullOrBlank()) {
                    AsciiText(text = "  └─ $subtitle", style = AsciiType.small, color = c.dim)
                }
            }
            val arrow = if (expanded) "^" else "v"
            AsciiText(
                text = "[ $selectedLabel $arrow ]",
                style = AsciiType.title,
                color = if (expanded) c.accent2 else c.accent,
                maxLines = 1,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEachIndexed { idx, (optVal, optLabel) ->
                    val isSel = optVal == selectedValue
                    val branch = if (idx == options.lastIndex) "└── " else "├─ "
                    val mark = if (isSel) "(*)" else "( )"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    expanded = false
                                    onSelect(optVal)
                                },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsciiText(
                            text = "$branch$mark $optLabel",
                            style = if (isSel) AsciiType.title else AsciiType.body,
                            color = if (isSel) c.accent else c.fg,
                            maxLines = 1,
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
) {
    val c = LocalAscii.current
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .toggleable(
                value = checked, role = Role.Checkbox, onValueChange = onChange,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = if (checked) "[x]" else "[ ]",
            color = if (checked) c.accent else c.dim,
            style = if (checked) AsciiType.title else AsciiType.body,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.width(8.dp))
        AsciiText(label)
    }
}

@Composable
fun AsciiRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .selectable(
                selected = selected, role = Role.RadioButton, onClick = onClick,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = if (selected) "(*)" else "( )",
            color = if (selected) c.accent else c.dim,
            style = if (selected) AsciiType.title else AsciiType.body,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.width(4.dp))
        AsciiText(
            text = label,
            color = if (selected) c.fg else c.dim,
            style = if (selected) AsciiType.title else AsciiType.body,
        )
    }
}

/**
 * True Plain-Text-Sports ASCII Section Block using character box-drawing (`┌─ title ───┐` ... `└───────────┘`)
 * instead of CSS `Modifier.border` rectangles.
 */
@Composable
fun AsciiBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    rightActionLabel: String? = null,
    onRightAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAscii.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.small)
            val prefix = if (title != null) "+- $title " else "+-"
            val rightPart = if (rightActionLabel != null) " [$rightActionLabel] -+" else "-+"
            val dashCount = (cols - prefix.length - rightPart.length).coerceAtLeast(2)
            val headerLine = prefix + "-".repeat(dashCount) + rightPart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiText(
                    text = headerLine,
                    style = AsciiType.small,
                    color = c.accent2,
                    softWrap = false,
                    maxLines = 1,
                    modifier = if (rightActionLabel != null && onRightAction != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onRightAction,
                        )
                    } else Modifier,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            content = content,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = rememberCols(AsciiType.small)
            val bottomLine = "+" + "-".repeat((cols - 2).coerceAtLeast(6)) + "+"
            AsciiText(
                text = bottomLine,
                style = AsciiType.small,
                color = c.border,
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun AsciiTopHeader(
    path: String,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(text = "+- $path", style = AsciiType.title, color = c.accent2)
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        AsciiRule(glyph = "═", color = c.border, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/**
 * Pure terminal prompt text field (`label:` / `> input_here` / `────────────`) with zero 4-sided rectangle borders.
 */
@Composable
fun AsciiTextField(
    label: String?,
    promptPrefix: String = "$ ",
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
    val ruleCol = if (errorText != null) c.error else c.border

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (label != null) {
            AsciiText(text = ":: $label", style = AsciiType.small, color = c.dim)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(
                text = promptPrefix,
                style = AsciiType.title,
                color = c.accent,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty() && placeholder.isNotEmpty()) {
                    AsciiText(text = placeholder, style = AsciiType.body, color = c.dim, maxLines = 1)
                }
                val fMod = if (focusRequester != null) {
                    Modifier.fillMaxWidth().focusRequester(focusRequester)
                } else {
                    Modifier.fillMaxWidth()
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = fMod.semantics { contentDescription = label ?: "search query" },
                    textStyle = AsciiType.body.copy(color = c.fg),
                    cursorBrush = SolidColor(c.accent),
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                )
            }
            if (trailingControl != null) {
                trailingControl()
            }
        }
        AsciiRule(glyph = "-", color = ruleCol)
        if (errorText != null) {
            Spacer(Modifier.height(2.dp))
            AsciiText(text = errorText, style = AsciiType.small, color = c.error)
        }
    }
}

@Composable
fun AsciiUndoBanner(
    message: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAscii.current
    Column(modifier = modifier.fillMaxWidth()) {
        AsciiRule(glyph = "=", color = c.warn)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiText(text = "! $message", style = AsciiType.small, color = c.warn, modifier = Modifier.weight(1f))
            AsciiButton(label = "undo", onClick = onUndo, primary = true, spokenLabel = "undo $message")
            AsciiButton(label = "x", onClick = onDismiss, spokenLabel = "dismiss")
        }
        AsciiRule(glyph = "=", color = c.warn)
    }
}

@Composable
fun BoxWithConstraintsScope.rememberCols(style: TextStyle = AsciiType.body): Int {
    val tm = rememberTextMeasurer()
    val cellPx = remember(style) { tm.measure("M", style).size.width.coerceAtLeast(1) }
    return (constraints.maxWidth / cellPx).coerceAtLeast(16)
}

fun leader(key: String, value: String, cols: Int): String {
    val dots = (cols - key.length - value.length - 2).coerceAtLeast(2)
    return "$key " + ".".repeat(dots) + " $value"
}

@Composable
fun AsciiFrameDivider(
    leftLabel: String,
    rightLabel: String? = null,
) {
    val c = LocalAscii.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(
            text = ":: $leftLabel",
            style = AsciiType.small,
            color = c.accent2,
            maxLines = 1,
        )
        if (rightLabel != null) {
            AsciiText(
                text = rightLabel,
                style = AsciiType.small,
                color = c.dim,
                maxLines = 1,
            )
        }
    }
}

fun frame(title: String, lines: List<String>, cols: Int): String {
    val safeCols = cols.coerceAtLeast(20)
    val inner = safeCols - 2
    val top = "+- $title ".take(safeCols - 1).padEnd(safeCols - 1, '-') + "+"
    val body = lines.map { "| " + it.take(inner - 2).padEnd(inner - 2, ' ') + " |" }
    val bottom = "+" + "-".repeat(inner) + "+"
    return (listOf(top) + body + bottom).joinToString("\n")
}
