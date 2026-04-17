package com.ngratzi.lumina.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ngratzi.lumina.data.model.SkyPhase

/**
 * Defines the visual palette for a sky phase.
 * All screen surfaces and colors derive from the active palette.
 */
@Stable
data class SkyPalette(
    // Hero gradient: 3 stops from zenith → mid-sky → horizon
    val gradientTop: Color,
    val gradientMid: Color,
    val gradientBottom: Color,
    // Surfaces
    val surfaceDim: Color,      // cards, bottom nav
    val surfaceContainer: Color, // elevated cards
    val outlineColor: Color,    // card borders, dividers
    // Text
    val onSurface: Color,       // primary text
    val onSurfaceVariant: Color, // secondary text, labels
    // Accent
    val accent: Color,          // highlighted values, active elements
    val sunColor: Color,        // sun/moon indicator on arc
)

object SkyThemeTokens {
    val Night = SkyPalette(
        gradientTop        = Color(0xFF000008),
        gradientMid        = Color(0xFF010115),
        gradientBottom     = Color(0xFF020220),
        surfaceDim         = Color(0xE6030315),
        surfaceContainer   = Color(0xE60A0A28),
        outlineColor       = Color(0xFF1A2050),
        onSurface          = Color(0xFFDDE8FF),
        onSurfaceVariant   = Color(0xFF7888BB),
        accent             = Color(0xFF5577CC),
        sunColor           = Color(0xFFCCDDFF),
    )

    val AstronomicalTwilight = SkyPalette(
        gradientTop        = Color(0xFF020218),
        gradientMid        = Color(0xFF040432),
        gradientBottom     = Color(0xFF06063D),
        surfaceDim         = Color(0xE6040420),
        surfaceContainer   = Color(0xE60C0C38),
        outlineColor       = Color(0xFF1E2860),
        onSurface          = Color(0xFFDDE8FF),
        onSurfaceVariant   = Color(0xFF8090C8),
        accent             = Color(0xFF6688DD),
        sunColor           = Color(0xFFAABBFF),
    )

    val NauticalTwilight = SkyPalette(
        gradientTop        = Color(0xFF060648),
        gradientMid        = Color(0xFF0A0A5C),
        gradientBottom     = Color(0xFF0D0D70),
        surfaceDim         = Color(0xE6080840),
        surfaceContainer   = Color(0xE6121258),
        outlineColor       = Color(0xFF243070),
        onSurface          = Color(0xFFE0EAFF),
        onSurfaceVariant   = Color(0xFF9098CC),
        accent             = Color(0xFF7799EE),
        sunColor           = Color(0xFF99BBFF),
    )

    val BlueHourMorning = SkyPalette(
        gradientTop        = Color(0xFF0D1B5E),
        gradientMid        = Color(0xFF1A3A8F),
        gradientBottom     = Color(0xFF2952A8),
        surfaceDim         = Color(0xE60E1A55),
        surfaceContainer   = Color(0xE61A3078),
        outlineColor       = Color(0xFF2A4888),
        onSurface          = Color(0xFFE8F0FF),
        onSurfaceVariant   = Color(0xFFAAC0E8),
        accent             = Color(0xFF88BBEE),
        sunColor           = Color(0xFFFFEE99),
    )

    val GoldenHourMorning = SkyPalette(
        gradientTop        = Color(0xFF4A1500),
        gradientMid        = Color(0xFF9B3800),
        gradientBottom     = Color(0xFFD46000),
        surfaceDim         = Color(0xE63A1000),
        surfaceContainer   = Color(0xE6602000),
        outlineColor       = Color(0xFF8B3500),
        onSurface          = Color(0xFFFFF0DD),
        onSurfaceVariant   = Color(0xFFFFCC88),
        accent             = Color(0xFFFFD060),
        sunColor           = Color(0xFFFFEE44),
    )

    val Daylight = SkyPalette(
        gradientTop        = Color(0xFF0A4A7A),
        gradientMid        = Color(0xFF1A7AB5),
        gradientBottom     = Color(0xFF4AAACE),
        surfaceDim         = Color(0xE60A3860),
        surfaceContainer   = Color(0xE6125888),
        outlineColor       = Color(0xFF1E5C90),
        onSurface          = Color(0xFFF0F8FF),
        onSurfaceVariant   = Color(0xFFB8D8F0),
        accent             = Color(0xFFFFFFFF),
        sunColor           = Color(0xFFFFEE00),
    )

    val GoldenHourEvening = SkyPalette(
        gradientTop        = Color(0xFF5C1800),
        gradientMid        = Color(0xFFA83C00),
        gradientBottom     = Color(0xFFE07000),
        surfaceDim         = Color(0xE6481200),
        surfaceContainer   = Color(0xE6702200),
        outlineColor       = Color(0xFF9B3800),
        onSurface          = Color(0xFFFFF5EE),
        onSurfaceVariant   = Color(0xFFFFCC99),
        accent             = Color(0xFFFFD070),
        sunColor           = Color(0xFFFFDD44),
    )

    val BlueHourEvening = SkyPalette(
        gradientTop        = Color(0xFF0F1E6E),
        gradientMid        = Color(0xFF1C3D95),
        gradientBottom     = Color(0xFF2B55B5),
        surfaceDim         = Color(0xE60E1A60),
        surfaceContainer   = Color(0xE61A3080),
        outlineColor       = Color(0xFF2C4A90),
        onSurface          = Color(0xFFEAF0FF),
        onSurfaceVariant   = Color(0xFFAABEE8),
        accent             = Color(0xFF99CCFF),
        sunColor           = Color(0xFFFFDD88),
    )

    fun forPhase(phase: SkyPhase): SkyPalette = when (phase) {
        SkyPhase.NIGHT                  -> Night
        SkyPhase.ASTRONOMICAL_TWILIGHT  -> AstronomicalTwilight
        SkyPhase.NAUTICAL_TWILIGHT      -> NauticalTwilight
        SkyPhase.BLUE_HOUR_MORNING      -> BlueHourMorning
        SkyPhase.GOLDEN_HOUR_MORNING    -> GoldenHourMorning
        SkyPhase.DAYLIGHT               -> Daylight
        SkyPhase.GOLDEN_HOUR_EVENING    -> GoldenHourEvening
        SkyPhase.BLUE_HOUR_EVENING      -> BlueHourEvening
    }

    /**
     * Returns a smooth interpolation between the palette for [phase] and the next one,
     * based on how far through the current phase the sun is (0.0 → 1.0).
     */
    fun interpolate(phase: SkyPhase, progress: Float): SkyPalette {
        val current = forPhase(phase)
        val next = forPhase(phase.next())
        val t = progress.coerceIn(0f, 1f)
        return SkyPalette(
            gradientTop        = lerp(current.gradientTop,        next.gradientTop,        t),
            gradientMid        = lerp(current.gradientMid,        next.gradientMid,        t),
            gradientBottom     = lerp(current.gradientBottom,     next.gradientBottom,     t),
            surfaceDim         = lerp(current.surfaceDim,         next.surfaceDim,         t),
            surfaceContainer   = lerp(current.surfaceContainer,   next.surfaceContainer,   t),
            outlineColor       = lerp(current.outlineColor,       next.outlineColor,       t),
            onSurface          = lerp(current.onSurface,          next.onSurface,          t),
            onSurfaceVariant   = lerp(current.onSurfaceVariant,   next.onSurfaceVariant,   t),
            accent             = lerp(current.accent,             next.accent,             t),
            sunColor           = lerp(current.sunColor,           next.sunColor,           t),
        )
    }
}

/** Ordered cycle of sky phases for interpolation. */
fun SkyPhase.next(): SkyPhase {
    val all = SkyPhase.entries
    return all[(all.indexOf(this) + 1) % all.size]
}

/** Mutable sky theme state — hoisted to the root composable and passed down. */
@Stable
class SkyThemeState(initial: SkyPalette = SkyThemeTokens.Night) {
    var palette by mutableStateOf(initial)
}
