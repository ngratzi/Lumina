package com.ngratzi.lumina.data.remote

import com.ngratzi.lumina.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Query

interface NoaaApiService {

    // Water level predictions (hilo)
    @GET("datagetter")
    suspend fun getTidePredictions(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,  // YYYYMMDD
        @Query("end_date") endDate: String,
        @Query("product") product: String = "predictions",
        @Query("datum") datum: String = "MLLW",
        @Query("interval") interval: String = "hilo",
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("application") app: String = "lumina",
        @Query("format") format: String = "json",
    ): NoaaTidePredictionResponse

    // Verified water levels (6-minute samples for chart curve)
    @GET("datagetter")
    suspend fun getVerifiedWaterLevel(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,
        @Query("end_date") endDate: String,
        @Query("product") product: String = "water_level",
        @Query("datum") datum: String = "MLLW",
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("application") app: String = "lumina",
        @Query("format") format: String = "json",
    ): NoaaWaterLevelResponse

    // Predicted water levels (6-minute samples for chart curve)
    @GET("datagetter")
    suspend fun getPredictedWaterLevel(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,
        @Query("end_date") endDate: String,
        @Query("product") product: String = "predictions",
        @Query("datum") datum: String = "MLLW",
        @Query("interval") interval: String = "6",  // 6-minute
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("application") app: String = "lumina",
        @Query("format") format: String = "json",
    ): NoaaWaterLevelResponse

    // Wind / meteorological
    @GET("datagetter")
    suspend fun getWind(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,
        @Query("end_date") endDate: String,
        @Query("product") product: String = "wind",
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("application") app: String = "lumina",
        @Query("format") format: String = "json",
    ): NoaaWindResponse

    // Tidal current predictions
    @GET("datagetter")
    suspend fun getCurrentPredictions(
        @Query("station") stationId: String,
        @Query("begin_date") beginDate: String,
        @Query("end_date") endDate: String,
        @Query("product") product: String = "currents_predictions",
        @Query("interval") interval: String = "MAX_SLACK",
        @Query("units") units: String = "english",
        @Query("time_zone") timeZone: String = "lst_ldt",
        @Query("application") app: String = "lumina",
        @Query("format") format: String = "json",
    ): NoaaCurrentResponse

    companion object {
        const val BASE_URL = "https://api.tidesandcurrents.noaa.gov/api/prod/"
        const val STATIONS_URL = "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/"
    }
}

interface NoaaStationApiService {
    /** Real-time sensor stations (water_level, wind, etc.) */
    @GET("stations.json")
    suspend fun getWaterLevelStations(
        @Query("type") type: String = "waterlevels",
        @Query("units") units: String = "english",
    ): NoaaStationListResponse

    /** Harmonic prediction-only stations (no real-time sensor required). */
    @GET("stations.json")
    suspend fun getPredictionStations(
        @Query("type") type: String = "tidepredictions",
        @Query("units") units: String = "english",
    ): NoaaStationListResponse
}
