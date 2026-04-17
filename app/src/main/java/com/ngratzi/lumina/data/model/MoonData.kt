package com.ngratzi.lumina.data.model

import java.time.ZonedDateTime

data class MoonData(
    val phase: Double,           // 0.0 (new) → 1.0 (back to new)
    val illumination: Double,    // 0.0 → 1.0
    val phaseName: String,
    val phaseEmoji: String,
    val moonrise: ZonedDateTime?,
    val moonTransit: ZonedDateTime?,
    val moonset: ZonedDateTime?,
    val distanceKm: Double,
    val isPerigee: Boolean,      // within 5% of closest approach
    val isApogee: Boolean,       // within 5% of farthest point
    val daysToFullMoon: Int,
    val daysToNewMoon: Int,
)
