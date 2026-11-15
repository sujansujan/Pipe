package com.sujan.pipe.ui.ascii

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
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
            .heightIn(min = 44.dp)
            .widthIn(min = 40.dp)
            .background(if (pressed) ink else Color.Transparent)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = "[$label]",
            color = if (pressed) c.bg else ink,
            style = if (primary) AsciiType.title else AsciiType.body,
            modifier = Modifier.clearAndSetSemantics { contentDescription = spokenLabel },
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
            .heightIn(min = 42.dp)
            .background(if (inverted) ink else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsciiText(
            text = display,
            color = if (inverted) c.bg else ink,
            style = if (selected) AsciiType.title else AsciiType.small,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "pipe to ${name ?: keyword}, keyword $keyword"
            },
        )
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
            .heightIn(min = 48.dp)
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
            .heightIn(min = 48.dp)
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

@Composable
fun AsciiBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    rightActionLabel: String? = null,
    onRightAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAscii.current
    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .border(1.dp, c.border)
                .padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 12.dp),
            content = content,
        )
        if (title != null) {
            AsciiText(
                text = " $title ",
                modifier = Modifier.padding(start = 10.dp).background(c.bg),
                style = AsciiType.small,
                color = c.accent2,
            )
        }
        if (rightActionLabel != null && onRightAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp)
                    .background(c.bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onRightAction,
                    )
                    .padding(horizontal = 4.dp),
            ) {
                AsciiText(
                    text = "[$rightActionLabel]",
                    style = AsciiType.small,
                    color = c.accent,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = rightActionLabel },
                )
            }
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
            AsciiText(text = path, style = AsciiType.title, color = c.accent2)
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

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
    val borderCol = if (errorText != null) c.error else c.accent

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            AsciiText(text = "$label:", style = AsciiType.small, color = c.dim)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(c.surface)
                .border(1.dp, borderCol)
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
        if (errorText != null) {
            Spacer(Modifier.height(4.dp))
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.warn)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiText(text = message, style = AsciiType.small, color = c.warn, modifier = Modifier.weight(1f))
        AsciiButton(label = "undo", onClick = onUndo, primary = true, spokenLabel = "undo $message")
        AsciiButton(label = "x", onClick = onDismiss, spokenLabel = "dismiss")
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

/**
 * Draws a full-width ASCII box-drawing horizontal divider line:
 * `├─ label ─────────────────────────────── rightMeta ─┤`
 */
@Composable
fun AsciiFrameDivider(
    leftLabel: String,
    rightLabel: String? = null,
    borderChar: Char = '─',
    leftCorner: String = "├─",
    rightCorner: String = "─┤",
) {
    val c = LocalAscii.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = rememberCols(AsciiType.small)
        val leftPart = "$leftCorner $leftLabel "
        val rightPart = if (rightLabel != null) " $rightLabel $rightCorner" else rightCorner
        val fillCount = (cols - leftPart.length - rightPart.length).coerceAtLeast(2)
        val line = leftPart + borderChar.toString().repeat(fillCount) + rightPart
        AsciiText(
            text = line,
            style = AsciiType.small,
            color = c.dim,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
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
