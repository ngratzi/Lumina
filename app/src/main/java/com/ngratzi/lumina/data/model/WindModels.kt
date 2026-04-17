package com.ngratzi.lumina.data.model

import java.time.ZonedDateTime

enum class WindSource { NOAA_STATION, OPEN_METEO }

data class WindObservation(
    val time: ZonedDateTime,
    val speedKnots: Double,
    val gustKnots: Double?,
    val directionDeg: Double,
    val source: WindSource,
) {
    val beaufortForce: Int get() = when {
        speedKnots < 1   -> 0
        speedKnots < 4   -> 1
        speedKnots < 7   -> 2
        speedKnots < 11  -> 3
        speedKnots < 17  -> 4
        speedKnots < 22  -> 5
        speedKnots < 28  -> 6
        speedKnots < 34  -> 7
        speedKnots < 41  -> 8
        speedKnots < 48  -> 9
        speedKnots < 56  -> 10
        speedKnots < 64  -> 11
        else             -> 12
    }

    val beaufortLabel: String get() = when (beaufortForce) {
        0    -> "Calm"
        1    -> "Light air"
        2    -> "Light breeze"
        3    -> "Gentle breeze"
        4    -> "Moderate breeze"
        5    -> "Fresh breeze"
        6    -> "Strong breeze"
        7    -> "Near gale"
        8    -> "Gale"
        9    -> "Severe gale"
        10   -> "Storm"
        11   -> "Violent storm"
        else -> "Hurricane"
    }

    val compassDirection: String get() {
        val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        val index = ((directionDeg + 11.25) / 22.5).toInt() % 16
        return dirs[index]
    }
}

data class WindForecast(
    val hourly: List<WindObservation>,
)
