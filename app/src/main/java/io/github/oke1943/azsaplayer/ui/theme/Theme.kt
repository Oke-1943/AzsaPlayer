package io.github.oke1943.azsaplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.oke1943.azsaplayer.settings.DisplaySettings
import io.github.oke1943.azsaplayer.settings.ThemeMode

/** 強調色の候補（6.5）。 */
val ACCENT_COLORS: List<Long> = listOf(
    0xFF6750A4, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF4511E, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF546E7A, 0xFFFFB300,
)

@Composable
fun MusicAppTheme(display: DisplaySettings = DisplaySettings(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val accent = Color(display.accentColor)
    val scheme = when (display.theme) {
        ThemeMode.LIGHT -> light(accent)
        ThemeMode.DARK -> dark(accent)
        ThemeMode.BLACK -> black(accent)
        ThemeMode.SYSTEM -> if (systemDark) dark(accent) else light(accent)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * display.fontScale.coerceIn(0.7f, 1.6f)),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun light(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    secondary = accent,
    tertiary = accent,
    primaryContainer = lerp(accent, Color.White, 0.8f),
    onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
    secondaryContainer = lerp(accent, Color.White, 0.85f),
    onSecondaryContainer = lerp(accent, Color.Black, 0.6f),
)

private fun dark(accent: Color): ColorScheme = darkColorScheme(
    primary = lerp(accent, Color.White, 0.35f),
    secondary = lerp(accent, Color.White, 0.35f),
    tertiary = lerp(accent, Color.White, 0.35f),
    primaryContainer = lerp(accent, Color.Black, 0.55f),
    onPrimaryContainer = lerp(accent, Color.White, 0.8f),
    secondaryContainer = lerp(accent, Color.Black, 0.6f),
    onSecondaryContainer = lerp(accent, Color.White, 0.8f),
)

private fun black(accent: Color): ColorScheme = dark(accent).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)
