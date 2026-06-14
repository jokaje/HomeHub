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

// nzb360-inspirierte Akzente. Diese Namen werden in der ganzen App referenziert
// (Orb, Status-Punkte, Badges) – die Werte tragen jetzt die neue Identität.
val Teal = Color(0xFF4FC6DA)           // frisches Cyan (Sekundärakzent)
val Violet = Color(0xFF6BD16A)         // Markengrün (für Orb-Verlauf etc.)
val Amber = Color(0xFFE8B84B)          // warmes Gold (Akzent)
val Danger = Color(0xFFE0705F)         // weiches Korallenrot
val Success = Color(0xFF6BD16A)        // Grün

// Markengrün
private val BrandGreen = Color(0xFF6BD16A)

private val DarkScheme = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF0C2A11),
    primaryContainer = Color(0xFF2E4A33),       // gefüllte Nav-Pille
    onPrimaryContainer = Color(0xFFC6F3C8),
    secondary = Teal,
    onSecondary = Color(0xFF06262C),
    secondaryContainer = Color(0xFF1E3B42),
    onSecondaryContainer = Color(0xFFBDEBF4),
    tertiary = Amber,
    onTertiary = Color(0xFF3A2C08),
    background = Color(0xFF16161B),             // warmes Near-Black
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF212229),                // Karten
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF2B2C34),         // Chips, innere Flächen
    onSurfaceVariant = Color(0xFF9A9AA4),
    error = Danger,
    onError = Color.White,
    outline = Color(0xFF34353E)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E9E4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFD2),
    onPrimaryContainer = Color(0xFF11421F),
    secondary = Color(0xFF2C8C9E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9ECF3),
    onSecondaryContainer = Color(0xFF093840),
    tertiary = Color(0xFFB9842A),
    onTertiary = Color.White,
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1C1C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C20),
    surfaceVariant = Color(0xFFECEAE3),
    onSurfaceVariant = Color(0xFF6C6B73),
    error = Color(0xFFC8503F),
    onError = Color.White,
    outline = Color(0xFFD9D6CD)
)

// Rundlich-geometrische Anmutung über kräftige Gewichte und enges Tracking.
// (Annäherung an die nzb360-Schrift mit Systemschrift – ein echtes Font-File
//  könnte später unter res/font/ ergänzt werden.)
private val Display = FontFamily.SansSerif

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
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
