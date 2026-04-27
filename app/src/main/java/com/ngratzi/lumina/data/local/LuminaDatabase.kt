package com.ngratzi.lumina.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlarmEntity::class,
        TideStationEntity::class,
        WaypointEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun tideStationDao(): TideStationDao
    abstract fun waypointDao(): WaypointDao
    abstract fun trackDao(): TrackDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS waypoints (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                icon TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tracks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER,
                totalDistanceNm REAL NOT NULL DEFAULT 0.0,
                isVisible INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackId INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_track_points_trackId ON track_points(trackId)"
        )
    }
}
