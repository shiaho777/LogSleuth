package io.github.shiaho777.logsleuth.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF245C52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEAE4),
    onPrimaryContainer = Color(0xFF173E37),
    inversePrimary = Color(0xFFA7CBBF),
    secondary = Color(0xFF505A55),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E9E4),
    onSecondaryContainer = Color(0xFF303834),
    tertiary = Color(0xFF525950),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECEDE7),
    onTertiaryContainer = Color(0xFF353C34),
    background = Color(0xFFF7F7F3),
    onBackground = Color(0xFF222724),
    surface = Color(0xFFF7F7F3),
    onSurface = Color(0xFF222724),
    surfaceVariant = Color(0xFFE8EAE4),
    onSurfaceVariant = Color(0xFF4C554F),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF2D332F),
    inverseOnSurface = Color(0xFFF1F2EC),
    error = Color(0xFFA82F36),
    onError = Color.White,
    errorContainer = Color(0xFFFCE2E1),
    onErrorContainer = Color(0xFF751A22),
    outline = Color(0xFF646D66),
    outlineVariant = Color(0xFFDADDDA),
    surfaceBright = Color(0xFFFCFCF9),
    surfaceDim = Color(0xFFE0E3DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBF8),
    surfaceContainer = Color(0xFFEEEFEA),
    surfaceContainerHigh = Color(0xFFE9EBE5),
    surfaceContainerHighest = Color(0xFFE3E6DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7CBBF),
    onPrimary = Color(0xFF173E37),
    primaryContainer = Color(0xFF293F37),
    onPrimaryContainer = Color(0xFFD3E6DE),
    inversePrimary = Color(0xFF245C52),
    secondary = Color(0xFFB8C2BB),
    onSecondary = Color(0xFF29342D),
    secondaryContainer = Color(0xFF2A332E),
    onSecondaryContainer = Color(0xFFDDE4DD),
    tertiary = Color(0xFFBBC3B6),
    onTertiary = Color(0xFF2D352B),
    tertiaryContainer = Color(0xFF30372F),
    onTertiaryContainer = Color(0xFFDCE3D8),
    background = Color(0xFF171A18),
    onBackground = Color(0xFFEAEDE6),
    surface = Color(0xFF171A18),
    onSurface = Color(0xFFEAEDE6),
    surfaceVariant = Color(0xFF2B312D),
    onSurfaceVariant = Color(0xFFB9C3BA),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFE5E9DF),
    inverseOnSurface = Color(0xFF282E29),
    error = Color(0xFFFFAAAB),
    onError = Color(0xFF5F1019),
    errorContainer = Color(0xFF4F2327),
    onErrorContainer = Color(0xFFFFCCCB),
    outline = Color(0xFF929D93),
    outlineVariant = Color(0xFF3B443D),
    surfaceBright = Color(0xFF333B35),
    surfaceDim = Color(0xFF171A18),
    surfaceContainerLowest = Color(0xFF111412),
    surfaceContainerLow = Color(0xFF1D211E),
    surfaceContainer = Color(0xFF222723),
    surfaceContainerHigh = Color(0xFF282E29),
    surfaceContainerHighest = Color(0xFF2E352F),
)

private val SleuthTypography = Typography().let { type ->
    type.copy(
        headlineLarge = type.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineMedium = type.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = type.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
        titleMedium = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = type.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val SleuthShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Multiplier applied to log row text sizes (compact 0.85 / default 1 / comfortable 1.15). */
val LocalLogTextScale = compositionLocalOf { 1f }

/**
 * Per-theme level palettes. Level colors double as small text (tags, meta
 * counts), so each palette clears WCAG AA (≥4.5:1) against its theme's
 * surface — the bright shared set only worked on dark backgrounds.
 */
data class LevelPalette(
    val V: Color,
    val D: Color,
    val I: Color,
    val W: Color,
    val E: Color,
    val F: Color,
    val A: Color,
)

/** Darkened variants for light surfaces. */
val LightLevelPalette = LevelPalette(
    V = Color(0xFF646963),
    D = Color(0xFF535D56),
    I = Color(0xFF365F53),
    W = Color(0xFF78541D),
    E = Color(0xFF9F343B),
    F = Color(0xFF892B32),
    A = Color(0xFF892B32),
)

/** Bright variants for dark surfaces. */
val DarkLevelPalette = LevelPalette(
    V = Color(0xFF9DA59D),
    D = Color(0xFFB3BDB3),
    I = Color(0xFF9BBEAD),
    W = Color(0xFFD8B97F),
    E = Color(0xFFEAA5A5),
    F = Color(0xFFF0B4B3),
    A = Color(0xFFF0B4B3),
)

/** Level palette for the active theme; provided by [LogSleuthTheme]. */
val LocalLevelColors = compositionLocalOf { DarkLevelPalette }

@Composable
fun LogSleuthTheme(
    themeSetting: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (themeSetting) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        motionScheme = SleuthMotion,
        typography = SleuthTypography,
        shapes = SleuthShapes,
    ) {
        CompositionLocalProvider(
            LocalLevelColors provides if (dark) DarkLevelPalette else LightLevelPalette,
            content = content,
        )
    }
}
