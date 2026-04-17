package com.ngratzi.lumina.data.model

import java.time.ZonedDateTime

data class TideStation(
    val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val state: String,
    val customLabel: String? = null,
    val hasCurrentData: Boolean = false,
    val hasWindSensor: Boolean = false,
)

enum class TideType { HIGH, LOW }

data class TideEvent(
    val time: ZonedDateTime,
    val heightFt: Double,
    val type: TideType,
    val isVerified: Boolean = false,  // true = NOAA observed data
)

enum class CurrentState { FLOOD, EBB, SLACK }

data class TidalCurrent(
    val time: ZonedDateTime,
    val velocityKnots: Double,
    val directionDeg: Double?,
    val state: CurrentState,
)

// Raw water level sample used to draw the tide chart curve
data class WaterLevelSample(
    val time: ZonedDateTime,
    val heightFt: Double,
    val isVerified: Boolean,
)
