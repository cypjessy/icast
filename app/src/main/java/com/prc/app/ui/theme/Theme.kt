package com.prc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0E7A55),          // premium emerald — light-friendly heroes & buttons
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2EC),
    onPrimaryContainer = Color(0xFF0A5C40),
    secondary = Color(0xFF161616),        // charcoal for text-strength accents
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE9E2),
    onSecondaryContainer = Color(0xFF161616),
    tertiary = Color(0xFFC2452F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEAE6),
    onTertiaryContainer = Color(0xFF9C3624),
    background = Color(0xFFFAF9F6),       // warm ivory canvas
    onBackground = Color(0xFF1A1A18),
    surface = Color(0xFFFFFEFB),          // lifted ivory cards
    onSurface = Color(0xFF1A1A18),
    surfaceVariant = Color(0xFFF1EFE9),
    onSurfaceVariant = Color(0xFF5C5B56),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBF8),
    surfaceContainer = Color(0xFFF7F5F1),
    surfaceContainerHigh = Color(0xFFF1EFE9),
    surfaceContainerHighest = Color(0xFFEAE7E0),
    surfaceBright = Color(0xFFFFFEFB),
    surfaceDim = Color(0xFFDBD8D1),
    inverseSurface = Color(0xFF1A1A18),
    inverseOnSurface = Color(0xFFFAF9F6),
    outline = Color(0xFFE2DFD8),
    outlineVariant = Color(0xFFE2DFD8),
    error = Color(0xFFC2452F),
    errorContainer = Color(0xFFFBEAE6)
)

/**
 * Premium dark scheme — deep emerald-tinted charcoal instead of flat black:
 * layered surface containers give cards visible lift, a brighter emerald
 * primary keeps buttons vivid, warm gold + soft coral accents glow, and
 * outlines are tinted so card edges read as designed rather than invisible.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF35A377),          // luminous emerald — pops on dark layers
    onPrimary = Color(0xFF04120C),
    primaryContainer = Color(0xFF144531),
    onPrimaryContainer = Color(0xFFA8E8CC),
    secondary = Color(0xFFE8BC55),        // warm gold accent
    onSecondary = Color(0xFF201800),
    secondaryContainer = Color(0xFF3A2F12),
    onSecondaryContainer = Color(0xFFF2D48E),
    tertiary = Color(0xFFEDA28F),         // soft coral
    onTertiary = Color(0xFF2B120B),
    tertiaryContainer = Color(0xFF3D211A),
    onTertiaryContainer = Color(0xFFF5C4B7),
    background = Color(0xFF0B100D),       // near-black with a green undertone
    onBackground = Color(0xFFE4EAE6),
    surface = Color(0xFF101612),          // cards lift off the background
    onSurface = Color(0xFFE4EAE6),
    surfaceVariant = Color(0xFF1A221D),
    onSurfaceVariant = Color(0xFFA3B2AA),
    surfaceContainerLowest = Color(0xFF080C09),
    surfaceContainerLow = Color(0xFF0E1410),
    surfaceContainer = Color(0xFF141B17),
    surfaceContainerHigh = Color(0xFF1B241E),
    surfaceContainerHighest = Color(0xFF232E27),
    surfaceBright = Color(0xFF1B241E),
    surfaceDim = Color(0xFF0B100D),
    inverseSurface = Color(0xFFE4EAE6),
    inverseOnSurface = Color(0xFF101612),
    outline = Color(0xFF2C3831),          // visible but soft card edges
    outlineVariant = Color(0xFF26312A),
    error = Color(0xFFEDA28F),
    errorContainer = Color(0xFF3D211A)
)

@Composable
fun PRCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Brand palette only — dynamic color was overriding the Kazi green on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
