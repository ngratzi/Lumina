package com.ngratzi.lumina.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY eventName")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY eventName")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE eventName = :eventName LIMIT 1")
    suspend fun getByEvent(eventName: String): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: AlarmEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE eventName = :eventName")
    suspend fun setEnabled(eventName: String, enabled: Boolean)

    @Query("UPDATE alarms SET offsetMinutes = :offsetMinutes WHERE eventName = :eventName")
    suspend fun setOffset(eventName: String, offsetMinutes: Int)
}
