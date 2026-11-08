package com.takidz.runv2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.ThemeMode

/**
 * Approved Design B Color Palette:
 * - Primary theme: Warm Night (`#171310` warm charcoal background with `#E8A33D` golden-amber accent)
 * - Complementary light theme: Warm Paper (`#FBF6EE` background)
 * - Small, clean chat text (13.5sp) without heavy bolding.
 */
data class RunDesignColors(
    val isDark: Boolean,
    val appBg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val bubbleIn: Color,
    val bubbleOut: Color,
    val bubbleOutInk: Color,
    val online: Color,
    val away: Color,
    val danger: Color
)

val WarmNightPalette = RunDesignColors(
    isDark = true,
    appBg = Color(0xFF171310),
    surface = Color(0xFF211A15),
    surface2 = Color(0xFF2B221B),
    ink = Color(0xFFF2E7DA),
    inkSoft = Color(0xFFA78F7A),
    line = Color(0xFF332820),
    accent = Color(0xFFE8A33D),
    accentInk = Color(0xFF1F1407),
    accentSoft = Color(0xFF382714),
    bubbleIn = Color(0xFF282019),
    bubbleOut = Color(0xFFC68225),
    bubbleOutInk = Color(0xFFFFF8EE),
    online = Color(0xFF3D9D63),
    away = Color(0xFFD9A428),
    danger = Color(0xFFC93B2E)
)

val WarmPaperPalette = RunDesignColors(
    isDark = false,
    appBg = Color(0xFFFBF6EE),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3EADD),
    ink = Color(0xFF2A2019),
    inkSoft = Color(0xFF857162),
    line = Color(0xFFEADFCE),
    accent = Color(0xFFD95D28),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFBE3D3),
    bubbleIn = Color(0xFFF1E7D7),
    bubbleOut = Color(0xFFD95D28),
    bubbleOutInk = Color(0xFFFFF7F0),
    online = Color(0xFF3D9D63),
    away = Color(0xFFD9A428),
    danger = Color(0xFFC93B2E)
)

val LocalRunColors = staticCompositionLocalOf { WarmNightPalette }

val RunTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

@Composable
fun RunV2Theme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.WARM_NIGHT -> true
        ThemeMode.WARM_PAPER -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (useDark) WarmNightPalette else WarmPaperPalette

    val materialScheme = if (useDark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            background = palette.appBg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            background = palette.appBg,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            error = palette.danger
        )
    }

    CompositionLocalProvider(
        LocalRunColors provides palette,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = RunTypography,
            content = content
        )
    }
}
