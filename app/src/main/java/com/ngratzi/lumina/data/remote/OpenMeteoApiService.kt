package com.ngratzi.lumina.data.remote

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

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
