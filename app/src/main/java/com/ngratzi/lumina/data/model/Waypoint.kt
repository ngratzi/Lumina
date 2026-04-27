package com.ngratzi.lumina.data.model

enum class WaypointIcon(val label: String, val symbol: String) {
    ANCHOR(   "Anchor",   "⚓"),
    FLAG(     "Flag",     "⚑"),
    STAR(     "Star",     "★"),
    DIAMOND(  "Diamond",  "◆"),
    CIRCLE(   "Circle",   "●"),
    TRIANGLE( "Triangle", "▲"),
    CROSSHAIR("Waypoint", "✛"),
    HAZARD(   "Hazard",   "✕"),
    BUOY(     "Buoy",     "⊕"),
}

data class Waypoint(
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val icon: WaypointIcon = WaypointIcon.CROSSHAIR,
    val createdAt: Long = System.currentTimeMillis(),
)
