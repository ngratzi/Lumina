package com.ngratzi.lumina.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class NoaaWindResponse(
    val data: List<NoaaWindSample>? = null,
    val error: NoaaError? = null,
)

@Serializable
data class NoaaWindSample(
    val t: String,   // "2024-01-15 00:00"
    val s: String,   // speed in knots
    val d: String,   // direction degrees
    val dr: String = "",  // compass direction (e.g. "W")
    val g: String = "",   // gust speed (may be empty)
    val f: String = "",   // flags
)
