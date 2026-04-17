package com.ngratzi.lumina.data.model

enum class SkyPhase(val displayName: String) {
    NIGHT("Night"),
    ASTRONOMICAL_TWILIGHT("Astronomical Twilight"),
    NAUTICAL_TWILIGHT("Nautical Twilight"),
    BLUE_HOUR_MORNING("Blue Hour"),
    GOLDEN_HOUR_MORNING("Golden Hour"),
    DAYLIGHT("Daylight"),
    GOLDEN_HOUR_EVENING("Golden Hour"),
    BLUE_HOUR_EVENING("Blue Hour"),
}

data class SkyState(
    val phase: SkyPhase,
    val solarAltitude: Double,
    val isEvening: Boolean,
    val nextEventName: String,
    val minutesUntilNextEvent: Long,
)

fun solarAltitudeToPhase(altitude: Double, isEvening: Boolean): SkyPhase = when {
    altitude < -18.0 -> SkyPhase.NIGHT
    altitude < -12.0 -> SkyPhase.ASTRONOMICAL_TWILIGHT
    altitude < -6.0  -> SkyPhase.NAUTICAL_TWILIGHT
    altitude < 0.0   -> if (isEvening) SkyPhase.BLUE_HOUR_EVENING else SkyPhase.BLUE_HOUR_MORNING
    altitude < 6.0   -> if (isEvening) SkyPhase.GOLDEN_HOUR_EVENING else SkyPhase.GOLDEN_HOUR_MORNING
    else             -> SkyPhase.DAYLIGHT
}
