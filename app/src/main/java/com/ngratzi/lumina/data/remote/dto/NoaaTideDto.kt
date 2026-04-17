package com.ngratzi.lumina.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoaaTidePredictionResponse(
    val predictions: List<NoaaTidePrediction>? = null,
    val error: NoaaError? = null,
)

@Serializable
data class NoaaTidePrediction(
    val t: String,   // "2024-01-15 06:30"
    val v: String,   // height in ft
    val type: String, // "H" or "L"
)

@Serializable
data class NoaaWaterLevelResponse(
    val data: List<NoaaWaterLevelSample>? = null,
    val error: NoaaError? = null,
)

@Serializable
data class NoaaWaterLevelSample(
    val t: String,  // "2024-01-15 00:00"
    val v: String,  // height in ft (may be empty string if missing)
    @SerialName("f") val flags: String = "",
)

@Serializable
data class NoaaError(
    val message: String,
)

// Station metadata from NOAA mdapi
@Serializable
data class NoaaStationListResponse(
    val stations: List<NoaaStationMeta>? = null,
)

@Serializable
data class NoaaStationMeta(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val state: String? = null,
)
