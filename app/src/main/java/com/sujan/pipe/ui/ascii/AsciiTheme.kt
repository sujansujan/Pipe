package com.sujan.pipe.ui.ascii

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sujan.pipe.model.EngineColorRole

private fun c(hex: Long) = Color(0xFF000000 or hex)

@Immutable
data class AsciiColors(
    val bg: Color, val surface: Color, val border: Color,
    val fg: Color, val dim: Color,
    val accent: Color, val accent2: Color, val accent3: Color,
    val good: Color, val warn: Color, val error: Color,
) {
    fun forRole(role: EngineColorRole): Color = when (role) {
        EngineColorRole.ACCENT -> accent
        EngineColorRole.ACCENT2 -> accent2
        EngineColorRole.ACCENT3 -> accent3
        EngineColorRole.GOOD -> good
        EngineColorRole.WARN -> warn
        EngineColorRole.FG -> fg
    }
}

data class AsciiPalette(val name: String, val dark: AsciiColors, val light: AsciiColors)

/** Default Unix terminal phosphor palette (`Moss` from `user:ascii-app`). */
val Moss = AsciiPalette(
    name = "moss",
    dark = AsciiColors(
        bg = c(0x0E1912), surface = c(0x16271C), border = c(0x33513D), fg = c(0xE9F3E3), dim = c(0x94B09B),
        accent = c(0xC6F36B), accent2 = c(0xFFB38A), accent3 = c(0x7FD3FF),
        good = c(0x6EE7A0), warn = c(0xFFD166), error = c(0xFF7A7A),
    ),
    light = AsciiColors(
        bg = c(0xF3F7E4), surface = c(0xE4EDC8), border = c(0xBBCB8F), fg = c(0x17281C), dim = c(0x56694F),
        accent = c(0x3F6B00), accent2 = c(0x9E4217), accent3 = c(0x0B6A93),
        good = c(0x196B35), warn = c(0x8F5B00), error = c(0xBF2E2E),
    ),
)

/** WCAG-verified Dracula (Dark) + Alucard (Light) pair matching Plain Text Sports apps. */
val DraculaAlucard = AsciiPalette(
    name = "dracula",
    dark = AsciiColors(
        bg = c(0x1E1F29), surface = c(0x282A36), border = c(0x44475A), fg = c(0xF8F8F2), dim = c(0xA6B0D8),
        accent = c(0xFF79C6), accent2 = c(0x8BE9FD), accent3 = c(0xF1FA8C),
        good = c(0x50FA7B), warn = c(0xFFB86C), error = c(0xFF6E6E),
    ),
    light = AsciiColors(
        bg = c(0xFFFBEB), surface = c(0xF3EED9), border = c(0xC8C2AE), fg = c(0x1F1F28), dim = c(0x595B6E),
        accent = c(0xA31567), accent2 = c(0x096A7E), accent3 = c(0x7A5900),
        good = c(0x146C32), warn = c(0x8D4B00), error = c(0xB81D2A),
    ),
)

val Ember = AsciiPalette(
    name = "ember",
    dark = AsciiColors(
        bg = c(0x1B1411), surface = c(0x2A1F1A), border = c(0x5A4034), fg = c(0xF8EBDD), dim = c(0xB39E8C),
        accent = c(0xFF8A3D), accent2 = c(0xFF6F91), accent3 = c(0xB8E986),
        good = c(0x8FD694), warn = c(0xFFC857), error = c(0xFF6B5E),
    ),
    light = AsciiColors(
        bg = c(0xFBF1E0), surface = c(0xF3E1C4), border = c(0xD9BE98), fg = c(0x2B1A12), dim = c(0x6E5645),
        accent = c(0xA63D00), accent2 = c(0xB0224F), accent3 = c(0x3F6A0E),
        good = c(0x276B2A), warn = c(0x855400), error = c(0xB02A18),
    ),
)

val Lagoon = AsciiPalette(
    name = "lagoon",
    dark = AsciiColors(
        bg = c(0x0A1D26), surface = c(0x11303C), border = c(0x2A5566), fg = c(0xE6F4F1), dim = c(0x8FB3B8),
        accent = c(0xFF7A59), accent2 = c(0x5EEAD4), accent3 = c(0xFFD166),
        good = c(0x7BE495), warn = c(0xFFB347), error = c(0xFF6B7A),
    ),
    light = AsciiColors(
        bg = c(0xEEF9F5), surface = c(0xDDF1EA), border = c(0xA8D4C6), fg = c(0x0E2A33), dim = c(0x4A6B72),
        accent = c(0xC03A16), accent2 = c(0x0F766E), accent3 = c(0x8A5A00),
        good = c(0x177036), warn = c(0x9A5B00), error = c(0xC1272D),
    ),
)

enum class PaletteChoice(val palette: AsciiPalette) {
    MOSS(Moss),
    DRACULA(DraculaAlucard),
    EMBER(Ember),
    LAGOON(Lagoon),
}

val LocalAscii = staticCompositionLocalOf { Moss.dark }
val Mono: FontFamily = FontFamily.Monospace

object AsciiType {
    private val base = TextStyle(fontFamily = Mono, fontFeatureSettings = "liga 0, calt 0")
    val body = base.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val title = body.copy(fontWeight = FontWeight.Bold)
    val small = base.copy(fontSize = 12.sp, lineHeight = 18.sp)
}

private val AsciiTypography = Typography(
    displayLarge = AsciiType.title, displayMedium = AsciiType.title, displaySmall = AsciiType.title,
    headlineLarge = AsciiType.title, headlineMedium = AsciiType.title, headlineSmall = AsciiType.title,
    titleLarge = AsciiType.title, titleMedium = AsciiType.title, titleSmall = AsciiType.title,
    bodyLarge = AsciiType.body, bodyMedium = AsciiType.body, bodySmall = AsciiType.small,
    labelLarge = AsciiType.title, labelMedium = AsciiType.body, labelSmall = AsciiType.small,
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
    palette: AsciiPalette = Moss,
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
            primary = c.accent, onPrimary = c.bg, secondary = c.accent2, onSecondary = c.bg,
            tertiary = c.accent3, onTertiary = c.bg, error = c.error, onError = c.bg,
            outline = c.dim, outlineVariant = c.border,
        ),
        typography = AsciiTypography,
        shapes = AsciiSquareShapes,
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
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

@Composable
fun AsciiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AsciiType.body,
    color: Color = LocalAscii.current.fg,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) = Text(text, modifier, color = color, style = style, softWrap = softWrap, maxLines = maxLines)
