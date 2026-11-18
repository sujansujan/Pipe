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
 * Strict `ascii-app` Section 4 color tokens:
 * - Foreground (`fg`), background (`bg`), dim (`dim`), and at most ONE alert accent (`error`).
 * - Legacy role fields (`surface`, `border`, `accent`, `accent2`, `accent3`, `good`, `warn`)
 *   resolve strictly to `bg`, `fg`, `dim`, or `error` so the entire app works in pure monochrome + 1 alert color,
 *   while selection uses inverted video (`bg` and `fg` swapped).
 */
@Immutable
data class AsciiColors(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val fg: Color,
    val dim: Color,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val good: Color,
    val warn: Color,
    val error: Color,
) {
    val alert: Color get() = error

    fun forRole(role: EngineColorRole): Color = when (role) {
        EngineColorRole.ACCENT -> fg
        EngineColorRole.ACCENT2 -> fg
        EngineColorRole.ACCENT3 -> dim
        EngineColorRole.GOOD -> fg
        EngineColorRole.WARN -> error
        EngineColorRole.FG -> fg
    }
}

data class AsciiPalette(val name: String, val dark: AsciiColors, val light: AsciiColors)

/**
 * Canonical `ascii-app` Section 4 palettes:
 * - paper (light): bg `#F4F1E8`, fg `#1A1A1A`, dim `#5C5C5C`, alert `#B00020`
 * - terminal (dark): bg `#0C0C0C`, fg `#D8D8D8`, dim `#8A8A8A`, alert `#FF6B6B`
 */
val TerminalPaper = AsciiPalette(
    name = "terminal / paper",
    dark = AsciiColors(
        bg = c(0x0C0C0C),
        surface = c(0x0C0C0C),
        border = c(0x8A8A8A),
        fg = c(0xD8D8D8),
        dim = c(0x8A8A8A),
        accent = c(0xD8D8D8),
        accent2 = c(0xD8D8D8),
        accent3 = c(0x8A8A8A),
        good = c(0xD8D8D8),
        warn = c(0xFF6B6B),
        error = c(0xFF6B6B),
    ),
    light = AsciiColors(
        bg = c(0xF4F1E8),
        surface = c(0xF4F1E8),
        border = c(0x5C5C5C),
        fg = c(0x1A1A1A),
        dim = c(0x5C5C5C),
        accent = c(0x1A1A1A),
        accent2 = c(0x1A1A1A),
        accent3 = c(0x5C5C5C),
        good = c(0x1A1A1A),
        warn = c(0xB00020),
        error = c(0xB00020),
    ),
)

val Moss = TerminalPaper

val DraculaAlucard = AsciiPalette(
    name = "dracula / alucard",
    dark = AsciiColors(
        bg = c(0x0C0C0C),
        surface = c(0x0C0C0C),
        border = c(0x8A8A8A),
        fg = c(0xD8D8D8),
        dim = c(0x8A8A8A),
        accent = c(0xD8D8D8),
        accent2 = c(0xD8D8D8),
        accent3 = c(0x8A8A8A),
        good = c(0xD8D8D8),
        warn = c(0xFF6B6B),
        error = c(0xFF6B6B),
    ),
    light = TerminalPaper.light,
)

val Ember = TerminalPaper
val Lagoon = TerminalPaper

enum class PaletteChoice(val palette: AsciiPalette) {
    MOSS(TerminalPaper),
    DRACULA(DraculaAlucard),
    EMBER(Ember),
    LAGOON(Lagoon),
}

val LocalAscii = staticCompositionLocalOf { TerminalPaper.dark }
val Mono: FontFamily = FontFamily.Monospace

/**
 * Strict `ascii-app` Section 1 & Section 4 Typography:
 * - One monospace font, ONE size per screen (`15.sp`, line height `21.sp`).
 * - Regular plus Bold only. No font-size scale (`body`, `title`, and `small` share the exact same cell width and height).
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
    palette: AsciiPalette = TerminalPaper,
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
            background = c.bg, surface = c.bg, onBackground = c.fg, onSurface = c.fg,
            primary = c.fg, onPrimary = c.bg, secondary = c.dim, onSecondary = c.bg,
            tertiary = c.dim, onTertiary = c.bg, error = c.error, onError = c.bg,
            outline = c.dim, outlineVariant = c.border,
        ),
        typography = AsciiTypography,
        shapes = AsciiSquareShapes,
    ) {
        val view = LocalView.current
        if (configureSystemBars && !view.isInEditMode) {
            SideEffect {
                (view.context as? Activity)?.window?.let { w ->
                    w.statusBarColor = android.graphics.Color.parseColor(if (dark) "#0C0C0C" else "#F4F1E8")
                    w.navigationBarColor = android.graphics.Color.parseColor(if (dark) "#0C0C0C" else "#F4F1E8")
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
