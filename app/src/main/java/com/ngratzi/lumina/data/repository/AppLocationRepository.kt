package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.model.LocationMode
import com.ngratzi.lumina.data.model.ResolvedLocation
import com.ngratzi.lumina.data.model.estimateZoneFromLon
import java.time.ZoneId
import com.ngratzi.lumina.util.LocationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocationRepository @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val locationHelper: LocationHelper,
) {
    /** Emits the current resolved location whenever mode or manual coords change. */
    val resolvedLocation: Flow<ResolvedLocation?> = combine(
        prefs.locationMode,
        prefs.manualLat,
        prefs.manualLon,
        prefs.manualLocationName,
    ) { mode, lat, lon, name ->
        when (mode) {
            LocationMode.MANUAL -> {
                if (lat != null && lon != null) {
                    ResolvedLocation(lat, lon, name, LocationMode.MANUAL, estimateZoneFromLon(lon))
                } else null
            }
            LocationMode.GPS -> null // callers must call getGpsLocation() for GPS
        }
    }

    /**
     * Returns the best available location for one-shot use (e.g. on init or refresh).
     * - MANUAL mode: returns stored coords immediately (no GPS hardware used)
     * - GPS mode: calls FusedLocationProviderClient for a fresh fix
     */
    suspend fun getLocation(): ResolvedLocation? {
        val mode = prefs.locationMode.value()
        return when (mode) {
            LocationMode.MANUAL -> {
                val lat  = prefs.manualLat.value()
                val lon  = prefs.manualLon.value()
                val name = prefs.manualLocationName.value()
                if (lat != null && lon != null)
                    ResolvedLocation(lat, lon, name, LocationMode.MANUAL, estimateZoneFromLon(lon))
                else getGpsLocation()
            }
            LocationMode.GPS -> getGpsLocation()
        }
    }

    private suspend fun getGpsLocation(): ResolvedLocation? {
        val location = locationHelper.getCurrentLocation() ?: return null
        val name = locationHelper.reverseGeocode(location.latitude, location.longitude) ?: ""
        return ResolvedLocation(location.latitude, location.longitude, name, LocationMode.GPS, ZoneId.systemDefault())
    }
}

// Read the first emitted value from a Flow without keeping a subscription.
private suspend fun <T> Flow<T>.value(): T = first()
