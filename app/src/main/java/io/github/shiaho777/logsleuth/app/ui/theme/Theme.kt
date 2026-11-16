package io.github.shiaho777.logsleuth.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    secondary = Color(0xFF4A6360),
    tertiary = Color(0xFF48617A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70D7C8),
    secondary = Color(0xFFB0CCC8),
    tertiary = Color(0xFFB0C9E8),
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
    V = Color(0xFF6C6C70),
    D = Color(0xFF0060DF),
    I = Color(0xFF146C2E),
    W = Color(0xFF8F4700),
    E = Color(0xFFBC000D),
    F = Color(0xFFA50011),
    A = Color(0xFF8944AB),
)

/** Bright variants for dark surfaces. */
val DarkLevelPalette = LevelPalette(
    V = Color(0xFF8E8E93),
    D = Color(0xFF0A84FF),
    I = Color(0xFF30D158),
    W = Color(0xFFFF9F0A),
    E = Color(0xFFFF453A),
    F = Color(0xFFFF6961),
    A = Color(0xFFBF5AF2),
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
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(
            LocalLevelColors provides if (dark) DarkLevelPalette else LightLevelPalette,
            content = content,
        )
    }
}
