package com.ngratzi.lumina.data.model

data class Track(
    val id: Long = 0,
    val name: String,
    val color: Int,           // ARGB int
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceNm: Double = 0.0,
    val isVisible: Boolean = true,
)

data class TrackPoint(
    val id: Long = 0,
    val trackId: Long,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
)

/** 10 preset track colors. Index 0 (Cyan) is the default. */
val TrackColors: List<Int> = listOf(
    0xFF4FC3F7.toInt(), // Cyan  (default)
    0xFFEF5350.toInt(), // Red
    0xFFFF9800.toInt(), // Orange
    0xFFFFEB3B.toInt(), // Yellow
    0xFF66BB6A.toInt(), // Green
    0xFF26A69A.toInt(), // Teal
    0xFF42A5F5.toInt(), // Blue
    0xFFAB47BC.toInt(), // Purple
    0xFFEC407A.toInt(), // Pink
    0xFFFFFFFF.toInt(), // White
)
