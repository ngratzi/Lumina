package com.ngratzi.lumina.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ngratzi.lumina.data.model.TrackPoint

@Entity(
    tableName = "track_points",
    indices = [Index("trackId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
) {
    fun toModel() = TrackPoint(id, trackId, lat, lon, timestamp)
}

fun TrackPoint.toEntity() = TrackPointEntity(id, trackId, lat, lon, timestamp)
