package com.asif.familyhisab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asif.familyhisab.R
import com.asif.familyhisab.data.ThemeMode

val Hind = FontFamily(
    Font(R.font.hind_regular, FontWeight.Normal),
    Font(R.font.hind_medium, FontWeight.Medium),
    Font(R.font.hind_semibold, FontWeight.SemiBold),
    Font(R.font.hind_bold, FontWeight.Bold),
)

/** Colours outside Material's scheme: money in/out and the signature gradient. */
@Immutable
data class Extra(
    val expense: Color,
    val income: Color,
    val warn: Color,
    val card: Color,
    val cardBorder: Color,
    val muted: Color,
    val hero: Brush,
    val dark: Boolean,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(Color.Red, Color.Green, Color.Yellow, Color.White, Color.LightGray, Color.Gray, Brush.linearGradient(listOf(Color.Blue, Color.Magenta)), false)
}

val HeroColors = listOf(Color(0xFF5B4BDB), Color(0xFF8E54E9), Color(0xFFE0569B))
val Violet = Color(0xFF6C5CE7)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E6FF),
    onPrimaryContainer = Color(0xFF2A1F8F),
    secondary = Color(0xFFE84393),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3F0),
    tertiary = Color(0xFF00B894),
    background = Color(0xFFF5F4FB),
    onBackground = Color(0xFF1B1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A2E),
    surfaceVariant = Color(0xFFEFEDF8),
    onSurfaceVariant = Color(0xFF6B6A80),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1EFFA),
    surfaceContainerLow = Color(0xFFF8F7FD),
    outline = Color(0xFFD9D6EA),
    outlineVariant = Color(0xFFE9E7F3),
    error = Color(0xFFE53950),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D93FF),
    onPrimary = Color(0xFF1A1259),
    primaryContainer = Color(0xFF2E2770),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Color(0xFFFF7EB6),
    onSecondary = Color(0xFF3B0A24),
    secondaryContainer = Color(0xFF4A1834),
    tertiary = Color(0xFF3DDBB0),
    background = Color(0xFF0E0D16),
    onBackground = Color(0xFFF2F1FA),
    surface = Color(0xFF181724),
    onSurface = Color(0xFFF2F1FA),
    surfaceVariant = Color(0xFF242335),
    onSurfaceVariant = Color(0xFFA6A4BC),
    surfaceContainer = Color(0xFF1C1B2A),
    surfaceContainerHigh = Color(0xFF222132),
    surfaceContainerHighest = Color(0xFF2A293C),
    surfaceContainerLow = Color(0xFF15141F),
    outline = Color(0xFF3A3950),
    outlineVariant = Color(0xFF2A293B),
    error = Color(0xFFFF6B81),
)

private fun type(): Typography {
    fun s(size: Int, w: FontWeight, lh: Int = (size * 1.45).toInt()) =
        TextStyle(fontFamily = Hind, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp)
    return Typography(
        displayLarge = s(44, FontWeight.Bold, 52),
        displayMedium = s(38, FontWeight.Bold, 46),
        displaySmall = s(32, FontWeight.Bold, 40),
        headlineLarge = s(28, FontWeight.Bold),
        headlineMedium = s(24, FontWeight.Bold),
        headlineSmall = s(21, FontWeight.SemiBold),
        titleLarge = s(19, FontWeight.SemiBold),
        titleMedium = s(16, FontWeight.SemiBold),
        titleSmall = s(14, FontWeight.SemiBold),
        bodyLarge = s(16, FontWeight.Normal),
        bodyMedium = s(14, FontWeight.Normal),
        bodySmall = s(12, FontWeight.Normal),
        labelLarge = s(14, FontWeight.SemiBold),
        labelMedium = s(12, FontWeight.Medium),
        labelSmall = s(11, FontWeight.Medium),
    )
}

@Composable
fun HisabTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extra = if (dark) Extra(
        expense = Color(0xFFFF7A85), income = Color(0xFF3DDBB0), warn = Color(0xFFFFC14D),
        card = Color(0xFF1C1B2A), cardBorder = Color(0xFF2A293B), muted = Color(0xFFA6A4BC),
        hero = Brush.linearGradient(HeroColors), dark = true,
    ) else Extra(
        expense = Color(0xFFEF4E5E), income = Color(0xFF00B386), warn = Color(0xFFF39C12),
        card = Color.White, cardBorder = Color(0xFFECEAF5), muted = Color(0xFF6B6A80),
        hero = Brush.linearGradient(HeroColors), dark = false,
    )
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = type(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
