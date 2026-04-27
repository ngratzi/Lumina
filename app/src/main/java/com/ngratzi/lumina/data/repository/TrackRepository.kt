package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.local.TrackDao
import com.ngratzi.lumina.data.local.toEntity
import com.ngratzi.lumina.data.model.Track
import com.ngratzi.lumina.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(private val dao: TrackDao) {

    fun observeTracks(): Flow<List<Track>> =
        dao.observeTracks().map { list -> list.map { it.toModel() } }

    suspend fun createTrack(track: Track): Long = dao.insertTrack(track.toEntity())

    suspend fun updateTrack(track: Track) = dao.updateTrack(track.toEntity())

    suspend fun addPoint(point: TrackPoint) = dao.insertPoint(point.toEntity())

    suspend fun getTrackPoints(trackId: Long): List<TrackPoint> =
        dao.getTrackPoints(trackId).map { it.toModel() }

    suspend fun deleteTrack(id: Long) {
        dao.deleteTrackPoints(id)
        dao.deleteTrack(id)
    }
}
