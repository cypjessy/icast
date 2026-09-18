package com.prc.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

/**
 * Blends the Android system bars with the current page.
 *
 * Call from any screen root: pass [statusBarColor] to paint the status bar a
 * matching colour (e.g. the hero's emerald), else the bars stay transparent
 * and the page's own edge-to-edge background shows through. [darkIcons] true
 * means dark icons (light page); false means light icons (dark/coloured page).
 */
@Composable
fun SystemBarAppearance(darkIcons: Boolean, statusBarColor: Color? = null) {
    val activity = LocalContext.current as? Activity ?: return
    LaunchedEffect(darkIcons, statusBarColor) {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        if (statusBarColor != null) {
            window.statusBarColor = AndroidColor.argb(
                (statusBarColor.alpha * 255).toInt(),
                (statusBarColor.red * 255).toInt(),
                (statusBarColor.green * 255).toInt(),
                (statusBarColor.blue * 255).toInt()
            )
        }
    }
}

/** True when [color] is perceptually dark (relative luminance < 0.5). */
fun Color.isDarkColor(): Boolean {
    val r = red; val g = green; val b = blue
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return lum < 0.5f
}
