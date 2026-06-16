package com.homehub.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.homehub.core.ServiceLocator

// Leuchtende Spot-Akzente (High Contrast). Diese Namen werden überall referenziert.
val Teal = Color(0xFF40D2E0)           // Cyan
val Violet = Color(0xFF63E06A)         // Markengrün (auch Orb-Verlauf)
val Amber = Color(0xFFF2B33C)          // Bernstein/Orange
val Danger = Color(0xFFFF6B5C)         // Korallenrot
val Success = Color(0xFF63E06A)        // Grün

// Zusätzliche Bento-Spot-Farben (für farbige Kachel-Hintergründe / Glow)
val SpotGreen = Color(0xFF63E06A)
val SpotTeal = Color(0xFF40D2E0)
val SpotAmber = Color(0xFFF2B33C)
val SpotBlue = Color(0xFF5B9CFF)
val SpotViolet = Color(0xFFB18CFF)
val SpotPink = Color(0xFFFF7BC4)

private val BrandGreen = Color(0xFF63E06A)

// Elevated Dark Mode: reines Schwarz als Basis, gehobene Grautöne als Karten.
private val DarkScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF06230B),
    primaryContainer = Color(0xFF1E3D22),
    onPrimaryContainer = Color(0xFFBFF3C2),
    secondary = Teal,
    onSecondary = Color(0xFF04282D),
    secondaryContainer = Color(0xFF123238),
    onSecondaryContainer = Color(0xFFBDEEF6),
    tertiary = Amber,
    onTertiary = Color(0xFF3A2A06),
    background = Color(0xFF000000),             // reines Schwarz
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF161618),                // gehobene Karten
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF222226),         // innere Flächen / Chips
    onSurfaceVariant = Color(0xFF9C9CA6),
    surfaceContainerHighest = Color(0xFF26262B),
    error = Danger,
    onError = Color(0xFF2A0A06),
    outline = Color(0xFF2E2E33)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E9E4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFD2),
    onPrimaryContainer = Color(0xFF11421F),
    secondary = Color(0xFF1F93A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9ECF3),
    onSecondaryContainer = Color(0xFF093840),
    tertiary = Color(0xFFB9842A),
    onTertiary = Color.White,
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFECECEF),
    onSurfaceVariant = Color(0xFF6B6B73),
    error = Color(0xFFC8503F),
    onError = Color.White,
    outline = Color(0xFFD9D6CD)
)

private val Display = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Display, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
)

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    val mode by ServiceLocator.settings.themeMode.collectAsState()
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
