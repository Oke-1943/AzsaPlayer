package io.github.oke1943.azsaplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.oke1943.azsaplayer.settings.DisplaySettings
import io.github.oke1943.azsaplayer.settings.ThemeMode
import kotlin.math.sqrt

/** 強調色の候補（6.5）。 */
val ACCENT_COLORS: List<Long> = listOf(
    0xFF6750A4, 0xFF1E88E5, 0xFF00897B, 0xFF43A047, 0xFFF4511E, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF546E7A, 0xFFFFB300,
)

/** タクティカルテーマを使っているか（見出しの書式や背景の模様を切り替える）。 */
val LocalTactical = staticCompositionLocalOf { false }

@Composable
fun MusicAppTheme(display: DisplaySettings = DisplaySettings(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val accent = Color(display.accentColor)
    val tactical = display.theme == ThemeMode.TACTICAL
    val scheme = when (display.theme) {
        ThemeMode.LIGHT -> light(accent)
        ThemeMode.DARK -> dark(accent)
        ThemeMode.BLACK -> black(accent)
        ThemeMode.TACTICAL -> Tactical.scheme
        ThemeMode.SYSTEM -> if (systemDark) dark(accent) else light(accent)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * display.fontScale.coerceIn(0.7f, 1.6f)),
        LocalTactical provides tactical,
    ) {
        MaterialTheme(colorScheme = scheme, typography = if (tactical) Tactical.typography else Typography(), content = content)
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

/**
 * タクティカル：紫の機体・蛍光グリーンの発光・橙の警告表示をイメージした暗い配色。
 * 見出しは明朝系の太字、ラベルは等幅の英大文字で、画面全体にごく薄い六角形の格子を重ねる。
 */
object Tactical {
    val Violet = Color(0xFF6B3FA0)
    val Green = Color(0xFF8CE03C)
    val Orange = Color(0xFFFF7A1A)
    private val Night = Color(0xFF0B0812)

    val scheme: ColorScheme = darkColorScheme(
        primary = Green,
        onPrimary = Color(0xFF0B1400),
        primaryContainer = Color(0xFF2C4A10),
        onPrimaryContainer = Color(0xFFCCF5A6),
        secondary = Orange,
        onSecondary = Color(0xFF1E0B00),
        secondaryContainer = Color(0xFF4A2408),
        onSecondaryContainer = Color(0xFFFFD2B0),
        tertiary = Color(0xFFA77BE6),
        onTertiary = Color(0xFF1A0B2E),
        tertiaryContainer = Violet,
        onTertiaryContainer = Color(0xFFEBDDFF),
        background = Night,
        onBackground = Color(0xFFEAE4F2),
        surface = Night,
        onSurface = Color(0xFFEAE4F2),
        surfaceVariant = Color(0xFF221A30),
        onSurfaceVariant = Color(0xFFB2A6C4),
        surfaceContainerLowest = Color(0xFF07050C),
        surfaceContainerLow = Color(0xFF110C1A),
        surfaceContainer = Color(0xFF161021),
        surfaceContainerHigh = Color(0xFF1D1529),
        surfaceContainerHighest = Color(0xFF261C35),
        outline = Color(0xFF5A4778),
        outlineVariant = Color(0xFF342848),
        error = Color(0xFFFF4040),
        onError = Color(0xFF220000),
        inverseSurface = Color(0xFFEAE4F2),
        inverseOnSurface = Night,
        inversePrimary = Color(0xFF3F6E12),
        scrim = Color.Black,
    )

    val typography: Typography = Typography().let { t ->
        val serif = FontFamily.Serif
        val mono = FontFamily.Monospace
        t.copy(
            headlineSmall = t.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.Black),
            titleLarge = t.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.Black),
            titleMedium = t.titleMedium.copy(fontFamily = serif, fontWeight = FontWeight.Bold),
            labelLarge = t.labelLarge.copy(fontFamily = mono, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            labelMedium = t.labelMedium.copy(fontFamily = mono, letterSpacing = 1.sp),
            labelSmall = t.labelSmall.copy(fontFamily = mono, letterSpacing = 1.sp),
        )
    }

    /** 画面全体にごく薄く重ねる六角形の格子。格子は画面の大きさが変わったときだけ作り直す。 */
    fun Modifier.hexOverlay(): Modifier = drawWithCache {
        val r = 22.dp.toPx()
        val w = sqrt(3f) * r
        val h = 1.5f * r
        val path = Path()
        var row = 0
        var y = 0f
        while (y < size.height + r) {
            var x = if (row % 2 == 0) 0f else w / 2f
            while (x < size.width + w) {
                for (i in 0..5) {
                    val a = Math.toRadians((60.0 * i) - 30.0)
                    val p = Offset(x + r * kotlin.math.cos(a).toFloat(), y + r * kotlin.math.sin(a).toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                x += w
            }
            y += h
            row++
        }
        val stroke = Stroke(width = 1.dp.toPx())
        val color = Green.copy(alpha = 0.035f)
        onDrawWithContent {
            drawContent()
            drawPath(path, color, style = stroke)
        }
    }
}
