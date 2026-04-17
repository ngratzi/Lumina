package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.local.AlarmDao
import com.ngratzi.lumina.data.local.AlarmEntity
import com.ngratzi.lumina.data.model.AlarmConfig
import com.ngratzi.lumina.data.model.SolarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
) {
    fun observeAlarms(): Flow<List<AlarmConfig>> =
        alarmDao.observeAll().map { entities ->
            // Merge stored entities with full event list, using defaults for any missing
            val stored = entities.associateBy { it.eventName }
            SolarEvent.entries.map { event ->
                val entity = stored[event.name]
                AlarmConfig(
                    event = event,
                    enabled = entity?.enabled ?: false,
                    offsetMinutes = entity?.offsetMinutes ?: event.defaultOffsetMinutes,
                )
            }
        }

    suspend fun setEnabled(event: SolarEvent, enabled: Boolean) {
        val existing = alarmDao.getByEvent(event.name)
        if (existing != null) {
            alarmDao.setEnabled(event.name, enabled)
        } else {
            alarmDao.upsert(AlarmEntity(event.name, enabled, event.defaultOffsetMinutes))
        }
    }

    suspend fun setOffset(event: SolarEvent, offsetMinutes: Int) {
        val existing = alarmDao.getByEvent(event.name)
        if (existing != null) {
            alarmDao.setOffset(event.name, offsetMinutes)
        } else {
            alarmDao.upsert(AlarmEntity(event.name, false, offsetMinutes))
        }
    }

    suspend fun getAll(): List<AlarmConfig> {
        val stored = alarmDao.getAll().associateBy { it.eventName }
        return SolarEvent.entries.map { event ->
            val entity = stored[event.name]
            AlarmConfig(
                event = event,
                enabled = entity?.enabled ?: false,
                offsetMinutes = entity?.offsetMinutes ?: event.defaultOffsetMinutes,
            )
        }
    }
}
