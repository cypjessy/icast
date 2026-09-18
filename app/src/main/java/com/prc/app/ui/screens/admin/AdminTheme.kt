package com.prc.app.ui.screens.admin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware palette for the admin portal.
 *
 * The portal was originally designed light-only with hardcoded "paper" colors,
 * which produced glaring white areas while the rest of the app ran in dark
 * mode. Every admin screen now reads from [AdminPalette] instead; in dark mode
 * the portal joins the app's AMOLED theme with pure-black chrome.
 */
data class AdminPalette(
    // chrome (top bar, bottom bar, hero bands)
    val Ink: Color,
    val InkRaised: Color,
    // text
    val InkSoft: Color,
    // surfaces
    val Paper: Color,
    val Card: Color,
    val Hairline: Color,
    // accents
    val Green: Color,
    val Mint: Color,
    val Gold: Color,
    val GoldSoft: Color,
    val GreenSoft: Color,
    val Red: Color,
    val RedSoft: Color,
    // AI violet accents
    val Violet: Color,
    val VioletTint: Color,
    // strong text on cards
    val InkStrong: Color,
    // translucent white for icons on dark chrome
    val OnInk: Color,
    // theme-aware chrome (top bar + bottom nav)
    val Chrome: Color,
    val OnChrome: Color,
    val OnChromeSoft: Color,
    val ChromeInactive: Color,
    val ChromeSelected: Color,
    val ChromeHairline: Color
)

/** Light palette — the original premium paper look. */
val AdminLight = AdminPalette(
    Ink = Color(0xFF141414),
    InkRaised = Color(0xFF242424),
    InkSoft = Color(0xFF6A6A66),
    Paper = Color(0xFFF7F7F5),
    Card = Color(0xFFFCFCFB),
    Hairline = Color(0xFFE5E5E2),
    Green = Color(0xFF0E7A55),
    Mint = Color(0xFFD9A324),
    Gold = Color(0xFFD9A324),
    GoldSoft = Color(0xFFFBF3E0),
    GreenSoft = Color(0xFFE3F2EC),
    Red = Color(0xFFB4442C),
    RedSoft = Color(0xFFF9E9E4),
    Violet = Color(0xFF6B4FA0),
    VioletTint = Color(0xFFEFEAF7),
    InkStrong = Color(0xFF182420),
    OnInk = Color.White,
    // premium light chrome: warm ivory bars, charcoal text, emerald accent
    Chrome = Color(0xFFFBFAF7),
    OnChrome = Color(0xFF1C1C1A),
    OnChromeSoft = Color(0xFF1C1C1A).copy(alpha = 0.55f),
    ChromeInactive = Color(0xFF1C1C1A).copy(alpha = 0.42f),
    ChromeSelected = Color(0xFF0E7A55),
    ChromeHairline = Color(0xFFE7E5DF)
)

/**
 * AMOLED dark palette — true black chrome and surfaces (OLED pixels off),
 * gold stays for approvals, violet for AI, cards lift with subtle dark greys.
 */
val AdminAmoled = AdminPalette(
    Ink = Color(0xFF000000),
    InkRaised = Color(0xFF161616),
    InkSoft = Color(0xFF9A9A94),
    Paper = Color(0xFF000000),
    Card = Color(0xFF0E0E0E),
    Hairline = Color(0xFF232323),
    Green = Color(0xFF34B27B),
    Mint = Color(0xFFE4B84C),
    Gold = Color(0xFFE4B84C),
    GoldSoft = Color(0xFF2A2210),
    GreenSoft = Color(0xFF1A1A1A),
    Red = Color(0xFFE8836E),
    RedSoft = Color(0xFF2A1410),
    Violet = Color(0xFFB79DF0),
    VioletTint = Color(0xFF1E1830),
    InkStrong = Color(0xFFEDEDEA),
    OnInk = Color.White,
    // AMOLED chrome: pure black bars, white text, mint accent
    Chrome = Color(0xFF000000),
    OnChrome = Color.White,
    OnChromeSoft = Color.White.copy(alpha = 0.55f),
    ChromeInactive = Color.White.copy(alpha = 0.45f),
    ChromeSelected = Color(0xFFE4B84C),
    ChromeHairline = Color.White.copy(alpha = 0.08f)
)

/** Current admin palette, following the system dark mode. */
@Composable
fun adminPalette(): AdminPalette =
    if (isSystemInDarkTheme()) AdminAmoled else AdminLight
