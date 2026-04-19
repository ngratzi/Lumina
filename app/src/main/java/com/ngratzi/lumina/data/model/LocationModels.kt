package com.ngratzi.lumina.data.model

import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class LocationMode { GPS, MANUAL }

data class GeocodedResult(
    val displayName: String,
    val lat: Double,
    val lon: Double,
)

data class ResolvedLocation(
    val lat: Double,
    val lon: Double,
    val displayName: String,
    val mode: LocationMode,
    val zone: ZoneId,
)

/** Approximate UTC offset from longitude — nearest whole hour, clamped to valid range. */
fun estimateZoneFromLon(lon: Double): ZoneId =
    ZoneOffset.ofHours((lon / 15.0).roundToInt().coerceIn(-12, 14))
