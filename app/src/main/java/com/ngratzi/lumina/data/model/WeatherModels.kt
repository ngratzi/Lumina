package com.ngratzi.lumina.data.model

import java.time.LocalDate
import java.time.ZonedDateTime

enum class PressureTrend {
    RISING_FAST, RISING, STEADY, FALLING, FALLING_FAST;

    val label: String get() = when (this) {
        RISING_FAST  -> "Rising rapidly"
        RISING       -> "Rising"
        STEADY       -> "Steady"
        FALLING      -> "Falling"
        FALLING_FAST -> "Falling rapidly"
    }

    val arrow: String get() = when (this) {
        RISING_FAST  -> "↑↑"
        RISING       -> "↑"
        STEADY       -> "→"
        FALLING      -> "↓"
        FALLING_FAST -> "↓↓"
    }
}

data class CurrentWeather(
    val temperature: Double,           // °F
    val feelsLike: Double,             // °F
    val weatherCode: Int,
    val condition: String,
    val surfacePressure: Double,       // hPa
    val pressureTrend: PressureTrend,
    val windSpeedKnots: Double,
    val windGustKnots: Double?,
    val windDirectionDeg: Double,
)

data class HourlyWeather(
    val time: ZonedDateTime,
    val temperature: Double,           // °F
    val weatherCode: Int,
    val precipProbability: Int,        // 0-100 %
    val surfacePressure: Double,       // hPa
    val windSpeedKnots: Double,
    val windGustKnots: Double?,
    val windDirectionDeg: Double,
)

data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val condition: String,
    val tempMax: Double,               // °F
    val tempMin: Double,               // °F
    val precipProbability: Int,        // 0-100 %
    val windSpeedMaxKnots: Double,
)

data class WeatherData(
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,           // next 12 hours
    val daily: List<DailyForecast>,            // 7 days
    val pressureHistory: List<Pair<ZonedDateTime, Double>>, // 24h for sparkline
)

fun wmoCondition(code: Int): String = when (code) {
    0            -> "Clear sky"
    1            -> "Mainly clear"
    2            -> "Partly cloudy"
    3            -> "Overcast"
    45, 48       -> "Foggy"
    51, 53, 55   -> "Drizzle"
    56, 57       -> "Freezing drizzle"
    61, 63, 65   -> "Rain"
    66, 67       -> "Freezing rain"
    71, 73, 75   -> "Snow"
    77           -> "Snow grains"
    80, 81, 82   -> "Rain showers"
    85, 86       -> "Snow showers"
    95           -> "Thunderstorm"
    96, 99       -> "Thunderstorm with hail"
    else         -> "Unknown"
}

fun wmoIcon(code: Int): String = when (code) {
    0            -> "☀️"
    1            -> "🌤"
    2            -> "⛅"
    3            -> "☁️"
    45, 48       -> "🌫"
    51, 53, 55   -> "🌦"
    56, 57       -> "🌨"
    61, 63, 65   -> "🌧"
    66, 67       -> "🌨"
    71, 73, 75   -> "❄️"
    77           -> "🌨"
    80, 81, 82   -> "🌧"
    85, 86       -> "🌨"
    95           -> "⛈"
    96, 99       -> "⛈"
    else         -> "🌡"
}
