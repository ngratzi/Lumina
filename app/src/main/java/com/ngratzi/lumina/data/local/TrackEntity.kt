package com.ngratzi.lumina.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ngratzi.lumina.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceNm: Double = 0.0,
    val isVisible: Boolean = true,
) {
    fun toModel() = Track(id, name, color, startTime, endTime, totalDistanceNm, isVisible)
}

fun Track.toEntity() = TrackEntity(id, name, color, startTime, endTime, totalDistanceNm, isVisible)
