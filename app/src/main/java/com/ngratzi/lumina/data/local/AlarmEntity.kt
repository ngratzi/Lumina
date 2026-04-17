package com.ngratzi.lumina.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ngratzi.lumina.data.model.SolarEvent

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val eventName: String,
    val enabled: Boolean,
    val offsetMinutes: Int,
) {
    fun toSolarEvent(): SolarEvent = SolarEvent.valueOf(eventName)
}
