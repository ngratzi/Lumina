package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.local.TideStationDao
import com.ngratzi.lumina.data.local.toEntity
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.data.remote.NoaaApiService
import com.ngratzi.lumina.data.remote.NoaaStationApiService
import com.ngratzi.lumina.data.remote.dto.NoaaStationMeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class TideRepository @Inject constructor(
    private val noaaApi: NoaaApiService,
    private val stationApi: NoaaStationApiService,
    private val stationDao: TideStationDao,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // ─── Stations ─────────────────────────────────────────────────────────────

    fun observeStations(): Flow<List<TideStation>> =
        stationDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeActiveStation(): Flow<TideStation?> =
        stationDao.observeActive().map { it?.toModel() }

    suspend fun getActiveStation(): TideStation? =
        stationDao.getActive()?.toModel()

    suspend fun setActiveStation(stationId: String) =
        stationDao.setActiveStation(stationId)

    suspend fun addStation(station: TideStation) {
        val count = stationDao.count()
        stationDao.upsert(station.toEntity(sortOrder = count))
    }

    suspend fun removeStation(station: TideStation) =
        stationDao.delete(station.toEntity())

    /**
     * Returns all NOAA stations (real-time water level + harmonic prediction), deduplicated.
     * Result is cached in memory for the process lifetime — the combined list is ~3 000 stations
     * and changes rarely, so a single fetch per app session is appropriate.
     */
    private var cachedAllStations: List<TideStation>? = null

    private suspend fun getAllStations(): List<TideStation> {
        cachedAllStations?.let { return it }
        val waterLevel = try { stationApi.getWaterLevelStations().stations ?: emptyList() } catch (_: Exception) { emptyList() }
        val harmonic   = try { stationApi.getPredictionStations().stations  ?: emptyList() } catch (_: Exception) { emptyList() }
        val seenIds = mutableSetOf<String>()
        return (waterLevel + harmonic)
            .filter { seenIds.add(it.id) }
            .map    { it.toModel() }
            .also   { cachedAllStations = it }
    }

    /** Nearest [limit] stations to [lat]/[lon], sorted by distance. */
    suspend fun searchNearbyStations(lat: Double, lon: Double, limit: Int = 30): List<TideStation> =
        getAllStations()
            .sortedBy { haversineKm(lat, lon, it.lat, it.lon) }
            .take(limit)

    /**
     * Full-text search over all ~3 000 NOAA stations.
     * Matches station name or state (case-insensitive), sorted nearest-first,
     * capped at [limit] so the UI stays manageable.
     */
    suspend fun searchStationsByName(
        query: String,
        lat: Double,
        lon: Double,
        limit: Int = 60,
    ): List<TideStation> {
        val q = query.trim().lowercase()
        return getAllStations()
            .filter { it.name.lowercase().contains(q) || it.state.lowercase().contains(q) }
            .sortedBy { haversineKm(lat, lon, it.lat, it.lon) }
            .take(limit)
    }

    // ─── Tide data ────────────────────────────────────────────────────────────

    suspend fun getTideEvents(stationId: String, date: LocalDate): List<TideEvent> {
        val begin = dateFormatter.format(date)
        val end = dateFormatter.format(date.plusDays(6))
        val response = noaaApi.getTidePredictions(stationId = stationId, beginDate = begin, endDate = end)

        return response.predictions?.mapNotNull { prediction ->
            val height = prediction.v.toDoubleOrNull() ?: return@mapNotNull null
            val time = parseNoaaTime(prediction.t) ?: return@mapNotNull null
            TideEvent(
                time = time,
                heightFt = height,
                type = if (prediction.type == "H") TideType.HIGH else TideType.LOW,
                isVerified = false,
            )
        } ?: emptyList()
    }

    suspend fun getPredictedCurve(stationId: String, date: LocalDate): List<WaterLevelSample> {
        val begin = dateFormatter.format(date)
        val end = begin
        val response = noaaApi.getPredictedWaterLevel(stationId = stationId, beginDate = begin, endDate = end)

        return response.data?.mapNotNull { sample ->
            val height = sample.v.toDoubleOrNull() ?: return@mapNotNull null
            val time = parseNoaaTime(sample.t) ?: return@mapNotNull null
            WaterLevelSample(time = time, heightFt = height, isVerified = false)
        } ?: emptyList()
    }

    /** Returns verified samples if available (past ~2 days), null otherwise. */
    suspend fun getVerifiedCurve(stationId: String, date: LocalDate): List<WaterLevelSample>? {
        return try {
            val begin = dateFormatter.format(date)
            val end = begin
            val response = noaaApi.getVerifiedWaterLevel(stationId = stationId, beginDate = begin, endDate = end)
            if (response.error != null) return null
            response.data
                ?.filter { it.v.isNotBlank() }
                ?.mapNotNull { sample ->
                    val height = sample.v.toDoubleOrNull() ?: return@mapNotNull null
                    val time = parseNoaaTime(sample.t) ?: return@mapNotNull null
                    WaterLevelSample(time = time, heightFt = height, isVerified = true)
                }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    // ─── Currents ─────────────────────────────────────────────────────────────

    suspend fun getTidalCurrents(stationId: String, date: LocalDate): List<TidalCurrent>? {
        return try {
            val begin = dateFormatter.format(date)
            val end = begin
            val response = noaaApi.getCurrentPredictions(stationId = stationId, beginDate = begin, endDate = end)
            if (response.error != null) return null
            response.currentPredictions?.cp?.mapNotNull { sample ->
                val velocity = sample.velocity.toDoubleOrNull() ?: return@mapNotNull null
                val time = parseNoaaTime(sample.time) ?: return@mapNotNull null
                val state = when {
                    sample.type.contains("slack", ignoreCase = true) -> CurrentState.SLACK
                    velocity > 0 -> CurrentState.FLOOD
                    else -> CurrentState.EBB
                }
                TidalCurrent(
                    time = time,
                    velocityKnots = abs(velocity),
                    directionDeg = sample.floodDir?.toDoubleOrNull(),
                    state = state,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun parseNoaaTime(s: String): ZonedDateTime? = try {
        ZonedDateTime.of(
            java.time.LocalDateTime.parse(s, parseFormatter),
            ZoneId.systemDefault()
        )
    } catch (e: Exception) { null }

    private fun NoaaStationMeta.toModel() = TideStation(
        stationId = id,
        name = name,
        lat = lat,
        lon = lng,
        state = state ?: "",
    )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
