package com.ngratzi.lumina.ui.theme

import androidx.compose.ui.graphics.Color

// Static colors used for non-sky-themed elements
val NightBlack = Color(0xFF000008)
val StarWhite  = Color(0xFFEEF4FF)
val MoonGlow   = Color(0xFFCCDDFF)
val SunGold    = Color(0xFFFFDD44)
val TideBlue   = Color(0xFF4499CC)
val WindGreen  = Color(0xFF44AA66)
val WindAmber  = Color(0xFFCC8833)
val WindOrange = Color(0xFFCC5522)
val WindRed    = Color(0xFFCC2233)

// Beaufort color scale
fun beaufortColor(force: Int): Color = when {
    force <= 3 -> WindGreen
    force <= 5 -> WindAmber
    force <= 7 -> WindOrange
    else       -> WindRed
}
