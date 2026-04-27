package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.local.WaypointDao
import com.ngratzi.lumina.data.local.toEntity
import com.ngratzi.lumina.data.model.Waypoint
import com.ngratzi.lumina.data.model.WaypointIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaypointRepository @Inject constructor(
    private val dao: WaypointDao,
) {
    fun observeWaypoints(): Flow<List<Waypoint>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun save(waypoint: Waypoint): Long =
        dao.insert(waypoint.toEntity())

    suspend fun update(waypoint: Waypoint) =
        dao.update(waypoint.toEntity())

    suspend fun delete(id: Long) =
        dao.deleteById(id)
}
