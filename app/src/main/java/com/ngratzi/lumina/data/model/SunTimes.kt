package com.ngratzi.lumina.data.model

import java.time.ZonedDateTime

data class SunTimes(
    val astronomicalDawn: ZonedDateTime?,
    val nauticalDawn: ZonedDateTime?,
    val blueHourStart: ZonedDateTime?,   // civil dawn
    val sunrise: ZonedDateTime?,
    val goldenHourEnd: ZonedDateTime?,   // sun at +6°
    val solarNoon: ZonedDateTime,
    val goldenHourStart: ZonedDateTime?, // sun at +6°, descending
    val sunset: ZonedDateTime?,
    val blueHourEnd: ZonedDateTime?,     // civil dusk
    val nauticalDusk: ZonedDateTime?,
    val astronomicalDusk: ZonedDateTime?,
)
