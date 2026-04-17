package com.ngratzi.lumina.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NoaaCurrentResponse(
    @SerialName("current_predictions") val currentPredictions: NoaaCurrentPredictions? = null,
    val error: NoaaError? = null,
)

@Serializable
data class NoaaCurrentPredictions(
    val cp: List<NoaaCurrentSample>? = null,
)

@Serializable
data class NoaaCurrentSample(
    @SerialName("Time") val time: String,              // "2024-01-15 02:15"
    @SerialName("Velocity_Major") val velocity: String, // knots, negative = ebb
    @SerialName("meanFloodDir") val floodDir: String? = null, // degrees
    @SerialName("Type") val type: String,              // "max_flood", "max_ebb", "slack"
)
