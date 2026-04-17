package com.ngratzi.lumina.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherResponse(
    val current: OpenMeteoCurrentWeather? = null,
    val hourly: OpenMeteoWeatherHourly? = null,
    val daily: OpenMeteoDailyWeather? = null,
)

@Serializable
data class OpenMeteoCurrentWeather(
    val time: String = "",
    @SerialName("temperature_2m")        val temperature: Double = 0.0,
    @SerialName("apparent_temperature")  val apparentTemperature: Double = 0.0,
    @SerialName("weather_code")          val weatherCode: Int = 0,
    @SerialName("surface_pressure")      val surfacePressure: Double = 0.0,
    @SerialName("windspeed_10m")         val windspeed: Double = 0.0,
    @SerialName("winddirection_10m")     val winddirection: Double = 0.0,
    @SerialName("windgusts_10m")         val windgusts: Double = 0.0,
)

@Serializable
data class OpenMeteoWeatherHourly(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m")           val temperature: List<Double?> = emptyList(),
    @SerialName("weathercode")              val weatherCode: List<Int?> = emptyList(),
    @SerialName("precipitation_probability") val precipProbability: List<Int?> = emptyList(),
    @SerialName("surface_pressure")         val surfacePressure: List<Double?> = emptyList(),
    @SerialName("windspeed_10m")            val windspeed: List<Double?> = emptyList(),
    @SerialName("winddirection_10m")        val winddirection: List<Double?> = emptyList(),
    @SerialName("windgusts_10m")            val windgusts: List<Double?> = emptyList(),
)

@Serializable
data class OpenMeteoDailyWeather(
    val time: List<String> = emptyList(),
    @SerialName("weathercode")                     val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max")              val tempMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min")              val tempMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max")   val precipProbability: List<Int?> = emptyList(),
    @SerialName("windspeed_10m_max")               val windspeedMax: List<Double?> = emptyList(),
)
