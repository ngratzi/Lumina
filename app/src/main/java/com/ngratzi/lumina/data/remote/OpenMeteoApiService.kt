package com.ngratzi.lumina.data.remote

import com.ngratzi.lumina.data.remote.dto.OpenMeteoWeatherResponse
import com.ngratzi.lumina.data.remote.dto.OpenMeteoWindResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApiService {

    @GET("v1/forecast")
    suspend fun getWindForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "windspeed_10m,winddirection_10m,windgusts_10m",
        @Query("windspeed_unit") windspeedUnit: String = "kn",
        @Query("forecast_days") forecastDays: Int = 2,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoWindResponse

    @GET("v1/forecast")
    suspend fun getWeatherForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code,surface_pressure,windspeed_10m,winddirection_10m,windgusts_10m",
        @Query("hourly") hourly: String = "temperature_2m,weathercode,precipitation_probability,surface_pressure,windspeed_10m,winddirection_10m,windgusts_10m",
        @Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max,windspeed_10m_max",
        @Query("temperature_unit") temperatureUnit: String = "fahrenheit",
        @Query("windspeed_unit") windspeedUnit: String = "kn",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("past_hours") pastHours: Int = 24,
        @Query("timezone") timezone: String = "auto",
    ): OpenMeteoWeatherResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
