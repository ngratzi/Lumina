package com.ngratzi.lumina.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TideStationDao {
    @Query("SELECT * FROM tide_stations ORDER BY sortOrder")
    fun observeAll(): Flow<List<TideStationEntity>>

    @Query("SELECT * FROM tide_stations ORDER BY sortOrder")
    suspend fun getAll(): List<TideStationEntity>

    @Query("SELECT * FROM tide_stations WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): TideStationEntity?

    @Query("SELECT * FROM tide_stations WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<TideStationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: TideStationEntity)

    @Delete
    suspend fun delete(station: TideStationEntity)

    @Query("UPDATE tide_stations SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE tide_stations SET isActive = 1 WHERE stationId = :stationId")
    suspend fun setActive(stationId: String)

    @Query("SELECT COUNT(*) FROM tide_stations")
    suspend fun count(): Int

    @Transaction
    suspend fun setActiveStation(stationId: String) {
        clearActive()
        setActive(stationId)
    }
}
