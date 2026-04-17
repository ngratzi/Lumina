package com.ngratzi.lumina.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ngratzi.lumina.data.model.TideStation

@Entity(tableName = "tide_stations")
data class TideStationEntity(
    @PrimaryKey val stationId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val state: String,
    val customLabel: String?,
    val hasCurrentData: Boolean,
    val hasWindSensor: Boolean,
    val sortOrder: Int = 0,
    val isActive: Boolean = false,
) {
    fun toModel() = TideStation(
        stationId      = stationId,
        name           = name,
        lat            = lat,
        lon            = lon,
        state          = state,
        customLabel    = customLabel,
        hasCurrentData = hasCurrentData,
        hasWindSensor  = hasWindSensor,
    )
}

fun TideStation.toEntity(sortOrder: Int = 0, isActive: Boolean = false) = TideStationEntity(
    stationId      = stationId,
    name           = name,
    lat            = lat,
    lon            = lon,
    state          = state,
    customLabel    = customLabel,
    hasCurrentData = hasCurrentData,
    hasWindSensor  = hasWindSensor,
    sortOrder      = sortOrder,
    isActive       = isActive,
)
