package com.sujan.pipe.ui.ascii

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sujan.pipe.model.EngineColorRole
import kotlin.math.floor

private fun c(hex: Long) = Color(0xFF000000 or hex)

/**
 * Stepped easing per `ascii-app` Section 7:
 * `fun stepEasing(n: Int) = Easing { f -> floor(f * n) / n }`
 */
fun stepEasing(n: Int): Easing = Easing { f -> floor(f * n) / n }

/**
 * Checks whether system reduced motion (`ANIMATOR_DURATION_SCALE == 0f`) is enabled.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)
}

/**
 * Modern Multi-Color Terminal Palette tokens per `ascii-app` Section 4:
 * - `bg`: main canvas background
 * - `surface`: selected row / modal / active block background
 * - `fg`: primary body text
 * - `dim`: comments, rules, secondary labels, borders
 * - `cyan`: links, paths, secondary interactive tags
 * - `green`: success, positive numbers, active toggles `[x]`, contacts
 * - `orange`: numbers, dates, timestamps, parameters, folders
 * - `pink`: keywords, primary actions `[ add ]`, cursor `_`
 * - `purple`: headers, section titles, active markers `>`
 * - `yellow`: warnings, fzf search match highlights, starred `*`
 * - `red`: errors `err:`, destructive actions `[ delete! ]`
 */
@Immutable
data class AsciiColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val dim: Color,
    val cyan: Color,
    val green: Color,
    val orange: Color,
    val pink: Color,
    val purple: Color,
    val yellow: Color,
    val red: Color,
) {
    // Semantic aliases used across screens & overlay
    val border: Color get() = dim
    val accent: Color get() = pink
    val accent2: Color get() = purple
    val accent3: Color get() = cyan
    val good: Color get() = green
    val warn: Color get() = orange
    val error: Color get() = red

    fun forRole(role: EngineColorRole): Color = when (role) {
        EngineColorRole.ACCENT -> pink
        EngineColorRole.ACCENT2 -> purple
        EngineColorRole.ACCENT3 -> cyan
        EngineColorRole.GOOD -> green
        EngineColorRole.WARN -> orange
        EngineColorRole.FG -> fg
    }
}

data class AsciiPalette(val name: String, val dark: AsciiColors, val light: AsciiColors)

/**
 * Canonical `ascii-app` Section 4 palette: Dracula (Dark) & Alucard (Light).
 */
val DraculaAlucard = AsciiPalette(
    name = "dracula / alucard",
    dark = AsciiColors(
        bg = c(0x282A36),
        surface = c(0x44475A),
        fg = c(0xF8F8F2),
        dim = c(0x6272A4),
        cyan = c(0x8BE9FD),
        green = c(0x50FA7B),
        orange = c(0xFFB86C),
        pink = c(0xFF79C6),
        purple = c(0xBD93F9),
        yellow = c(0xF1FA8C),
        red = c(0xFF5555),
    ),
    light = AsciiColors(
        bg = c(0xFFFBEB),
        surface = c(0xF2EEDE),
        fg = c(0x1F1F1F),
        dim = c(0x6C664B),
        cyan = c(0x036A96),
        green = c(0x14710A),
        orange = c(0xA34D14),
        pink = c(0xA3144D),
        purple = c(0x644AC9),
        yellow = c(0x846E15),
        red = c(0xCB3A2A),
    ),
)

val Moss = AsciiPalette(
    name = "moss terminal",
    dark = AsciiColors(
        bg = c(0x0E1912),
        surface = c(0x1B3022),
        fg = c(0xE9F3E3),
        dim = c(0x6E8F75),
        cyan = c(0x7FD3FF),
        green = c(0x6EE7A0),
        orange = c(0xFFB38A),
        pink = c(0xC6F36B),
        purple = c(0xB8A9FF),
        yellow = c(0xFFD166),
        red = c(0xFF7A7A),
    ),
    light = AsciiColors(
        bg = c(0xF3F7E4),
        surface = c(0xE4EDC8),
        fg = c(0x17281C),
        dim = c(0x56694F),
        cyan = c(0x0B6A93),
        green = c(0x196B35),
        orange = c(0x9E4217),
        pink = c(0x3F6B00),
        purple = c(0x5B3E96),
        yellow = c(0x8F5B00),
        red = c(0xBF2E2E),
    ),
)

val Ember = AsciiPalette(
    name = "ember terminal",
    dark = AsciiColors(
        bg = c(0x1B1411),
        surface = c(0x2E221D),
        fg = c(0xF8EBDD),
        dim = c(0x9A8270),
        cyan = c(0x7FD3FF),
        green = c(0x8FD694),
        orange = c(0xFF8A3D),
        pink = c(0xFF6F91),
        purple = c(0xD39BFF),
        yellow = c(0xFFC857),
        red = c(0xFF6B5E),
    ),
    light = AsciiColors(
        bg = c(0xFBF1E0),
        surface = c(0xF3E1C4),
        fg = c(0x2B1A12),
        dim = c(0x6E5645),
        cyan = c(0x0B6A93),
        green = c(0x276B2A),
        orange = c(0xA63D00),
        pink = c(0xB0224F),
        purple = c(0x644AC9),
        yellow = c(0x855400),
        red = c(0xB02A18),
    ),
)

val Lagoon = AsciiPalette(
    name = "lagoon terminal",
    dark = AsciiColors(
        bg = c(0x0A1D26),
        surface = c(0x143544),
        fg = c(0xE6F4F1),
        dim = c(0x6E98A1),
        cyan = c(0x5EEAD4),
        green = c(0x7BE495),
        orange = c(0xFFB347),
        pink = c(0xFF7A59),
        purple = c(0xBD93F9),
        yellow = c(0xFFD166),
        red = c(0xFF6B7A),
    ),
    light = AsciiColors(
        bg = c(0xEEF9F5),
        surface = c(0xDDF1EA),
        fg = c(0x0E2A33),
        dim = c(0x4A6B72),
        cyan = c(0x0F766E),
        green = c(0x177036),
        orange = c(0x9A5B00),
        pink = c(0xC03A16),
        purple = c(0x5B3E96),
        yellow = c(0x8A5A00),
        red = c(0xC1272D),
    ),
)

enum class PaletteChoice(val palette: AsciiPalette) {
    DRACULA(DraculaAlucard),
    MOSS(Moss),
    EMBER(Ember),
    LAGOON(Lagoon),
}

val LocalAscii = staticCompositionLocalOf { DraculaAlucard.dark }
val Mono: FontFamily = FontFamily.Monospace

/**
 * Section 1 & Section 4 Typography:
 * One monospace font, ONE size per screen (`15.sp`, line height `21.sp`).
 * Regular plus Bold only.
 */
object AsciiType {
    private val base = TextStyle(
        fontFamily = Mono,
        fontFeatureSettings = "liga 0, calt 0",
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )
    val body: TextStyle = base.copy(fontWeight = FontWeight.Normal)
    val title: TextStyle = base.copy(fontWeight = FontWeight.Bold)
    val small: TextStyle = base.copy(fontWeight = FontWeight.Normal)
}

private val AsciiTypography = Typography(
    displayLarge = AsciiType.title, displayMedium = AsciiType.title, displaySmall = AsciiType.title,
    headlineLarge = AsciiType.title, headlineMedium = AsciiType.title, headlineSmall = AsciiType.title,
    titleLarge = AsciiType.title, titleMedium = AsciiType.title, titleSmall = AsciiType.title,
    bodyLarge = AsciiType.body, bodyMedium = AsciiType.body, bodySmall = AsciiType.body,
    labelLarge = AsciiType.title, labelMedium = AsciiType.body, labelSmall = AsciiType.body,
)

private val SquareCornerShape = RoundedCornerShape(0.dp)

private val AsciiSquareShapes = Shapes(
    extraSmall = SquareCornerShape,
    small = SquareCornerShape,
    medium = SquareCornerShape,
    large = SquareCornerShape,
    extraLarge = SquareCornerShape,
)

enum class ThemeMode { System, Light, Dark }

@Composable
fun PipeTheme(
    mode: ThemeMode = ThemeMode.System,
    palette: AsciiPalette = DraculaAlucard,
    configureSystemBars: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) palette.dark else palette.light
    val base = if (dark) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = base.copy(
            background = c.bg, surface = c.surface, onBackground = c.fg, onSurface = c.fg,
            primary = c.pink, onPrimary = c.bg, secondary = c.purple, onSecondary = c.bg,
            tertiary = c.cyan, onTertiary = c.bg, error = c.red, onError = c.bg,
            outline = c.dim, outlineVariant = c.dim,
        ),
        typography = AsciiTypography,
        shapes = AsciiSquareShapes,
    ) {
        val view = LocalView.current
        if (configureSystemBars && !view.isInEditMode) {
            SideEffect {
                (view.context as? Activity)?.window?.let { w ->
                    WindowCompat.getInsetsController(w, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
        }
        CompositionLocalProvider(
            LocalAscii provides c,
            LocalContentColor provides c.fg,
            LocalTextStyle provides AsciiType.body,
            content = content,
        )
    }
}

/**
 * Pure foundation `BasicText` wrapper (no Material `Text` component).
 */
@Composable
fun AsciiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AsciiType.body,
    color: Color = LocalAscii.current.fg,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text = text,
    modifier = modifier,
    style = style.copy(color = color),
    softWrap = softWrap,
    maxLines = maxLines,
    overflow = if (maxLines == 1) TextOverflow.Clip else TextOverflow.Visible,
)

@Composable
fun AsciiText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = AsciiType.body,
    color: Color = LocalAscii.current.fg,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text = text,
    modifier = modifier,
    style = style.copy(color = color),
    softWrap = softWrap,
    maxLines = maxLines,
    overflow = if (maxLines == 1) TextOverflow.Clip else TextOverflow.Visible,
)
