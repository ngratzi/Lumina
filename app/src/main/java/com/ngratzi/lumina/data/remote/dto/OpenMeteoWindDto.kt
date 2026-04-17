package com.ngratzi.lumina.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWindResponse(
    val hourly: OpenMeteoHourly? = null,
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String>,
    @SerialName("windspeed_10m") val windspeed: List<Double?>,
    @SerialName("winddirection_10m") val winddirection: List<Double?>,
    @SerialName("windgusts_10m") val windgusts: List<Double?>,
)
