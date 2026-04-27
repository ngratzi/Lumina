package com.ngratzi.lumina.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ngratzi.lumina.data.model.Waypoint
import com.ngratzi.lumina.data.model.WaypointIcon

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lon: Double,
    val icon: String,          // WaypointIcon.name()
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toModel() = Waypoint(
        id        = id,
        name      = name,
        lat       = lat,
        lon       = lon,
        icon      = WaypointIcon.valueOf(icon),
        createdAt = createdAt,
    )
}

fun Waypoint.toEntity() = WaypointEntity(
    id        = id,
    name      = name,
    lat       = lat,
    lon       = lon,
    icon      = icon.name,
    createdAt = createdAt,
)
