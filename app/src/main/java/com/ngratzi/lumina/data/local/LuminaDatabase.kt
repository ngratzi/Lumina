package com.ngratzi.lumina.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, TideStationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun tideStationDao(): TideStationDao
}
