package io.github.shiaho777.logsleuth.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF70D7C8),
    secondary = Color(0xFFB0CCC8),
    tertiary = Color(0xFFB0C9E8),
)

/** Level colors shared across light/dark themes. */
object LevelColors {
    val V = Color(0xFF8E8E93)
    val D = Color(0xFF0A84FF)
    val I = Color(0xFF30D158)
    val W = Color(0xFFFF9F0A)
    val E = Color(0xFFFF453A)
    val F = Color(0xFFD70015)
}

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
        else -> expressiveLightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
