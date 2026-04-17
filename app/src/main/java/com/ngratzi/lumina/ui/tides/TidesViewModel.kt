package com.ngratzi.lumina.ui.tides

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.data.repository.TideRepository
import com.ngratzi.lumina.data.repository.UserPreferencesRepository
import com.ngratzi.lumina.data.repository.WindRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos

data class TidesUiState(
    val isLoading: Boolean = true,
    val activeStation: TideStation? = null,
    val savedStations: List<TideStation> = emptyList(),
    val predictedCurve: List<WaterLevelSample> = emptyList(),
    val verifiedCurve: List<WaterLevelSample>? = null,
    val tideEvents: List<TideEvent> = emptyList(),
    val currents: List<TidalCurrent>? = null,
    val currentWind: WindObservation? = null,
    val windForecast: WindForecast? = null,
    val currentTime: ZonedDateTime = ZonedDateTime.now(),
    val error: String? = null,
    // Station picker
    val nearbyStations: List<TideStation> = emptyList(),
    val isSearchingStations: Boolean = false,
    val searchError: String? = null,
    val stationSearchQuery: String = "",
    val stationSearchResults: List<TideStation> = emptyList(),
    val userLat: Double = 0.0,
    val userLon: Double = 0.0,
    val tailingTideThreshold: Double = UserPreferencesRepository.DEFAULT_TAILING_THRESHOLD,
)

@HiltViewModel
class TidesViewModel @Inject constructor(
    private val tideRepository: TideRepository,
    private val windRepository: WindRepository,
    private val locationHelper: com.ngratzi.lumina.util.LocationHelper,
    private val userPrefs: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TidesUiState())
    val uiState: StateFlow<TidesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefs.tailingTideThresholdFt.collect { threshold ->
                _uiState.update { it.copy(tailingTideThreshold = threshold) }
            }
        }
        viewModelScope.launch {
            tideRepository.observeStations().collect { stations ->
                _uiState.update { it.copy(savedStations = stations) }
            }
        }
        viewModelScope.launch {
            tideRepository.observeActiveStation().collect { station ->
                _uiState.update { it.copy(activeStation = station) }
                if (station != null) {
                    loadTideData(station)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
        startClock()
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val now = ZonedDateTime.now()
                val prevDate = _uiState.value.currentTime.toLocalDate()
                _uiState.update { it.copy(currentTime = now) }
                // Reload data at midnight
                if (now.toLocalDate() != prevDate) {
                    _uiState.value.activeStation?.let { loadTideData(it) }
                }
            }
        }
    }

    fun setActiveStation(stationId: String) {
        viewModelScope.launch {
            tideRepository.setActiveStation(stationId)
        }
    }

    fun addAndActivateStation(station: TideStation) {
        viewModelScope.launch {
            tideRepository.addStation(station)
            tideRepository.setActiveStation(station.stationId)
        }
    }

    fun searchNearbyStations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearchingStations = true, searchError = null) }
            try {
                val location = locationHelper.getCurrentLocation()
                if (location == null) {
                    _uiState.update { it.copy(isSearchingStations = false, searchError = "Location unavailable") }
                    return@launch
                }
                val stations = tideRepository.searchNearbyStations(location.latitude, location.longitude)
                _uiState.update {
                    it.copy(
                        isSearchingStations = false,
                        nearbyStations = stations,
                        userLat = location.latitude,
                        userLon = location.longitude,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingStations = false, searchError = "Could not load stations: ${e.message}") }
            }
        }
    }

    fun setStationSearchQuery(query: String) {
        _uiState.update { it.copy(stationSearchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(stationSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val state = _uiState.value
            val results = tideRepository.searchStationsByName(query, state.userLat, state.userLon)
            // Discard results if query changed while we were searching
            if (_uiState.value.stationSearchQuery == query) {
                _uiState.update { it.copy(stationSearchResults = results) }
            }
        }
    }

    fun refresh() {
        val station = _uiState.value.activeStation ?: return
        loadTideData(station)
    }

    /**
     * Generates a smooth 6-minute-interval tide curve from H/L events using cosine
     * interpolation. Used as a fallback for subordinate stations that only support
     * interval=hilo from the NOAA API.
     */
    private fun syntheticCurve(events: List<TideEvent>, date: LocalDate): List<WaterLevelSample> {
        if (events.size < 2) return emptyList()
        val zone    = ZoneId.systemDefault()
        val sorted  = events.sortedBy { it.time.toInstant().toEpochMilli() }
        val dayStart = date.atStartOfDay(zone)
        val dayEnd   = dayStart.plusDays(1)

        val samples = mutableListOf<WaterLevelSample>()
        var t = dayStart
        while (!t.isAfter(dayEnd)) {
            val epochMs = t.toInstant().toEpochMilli()
            val before  = sorted.lastOrNull  { it.time.toInstant().toEpochMilli() <= epochMs }
            val after   = sorted.firstOrNull { it.time.toInstant().toEpochMilli() >  epochMs }
            val height  = when {
                before == null -> after!!.heightFt
                after  == null -> before.heightFt
                else -> {
                    val t0   = before.time.toInstant().toEpochMilli().toDouble()
                    val t1   = after.time.toInstant().toEpochMilli().toDouble()
                    val frac = ((epochMs - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
                    // Cosine easing: matches the natural sinusoidal shape of tides
                    val cosT = (1.0 - cos(PI * frac)) / 2.0
                    before.heightFt + (after.heightFt - before.heightFt) * cosT
                }
            }
            samples.add(WaterLevelSample(time = t, heightFt = height, isVerified = false))
            t = t.plusMinutes(6)
        }
        return samples
    }

    private fun loadTideData(station: TideStation) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val today = LocalDate.now()
            try {
                val eventsDeferred    = async { tideRepository.getTideEvents(station.stationId, today) }
                val curveDeferred     = async { tideRepository.getPredictedCurve(station.stationId, today) }
                val verifiedDeferred  = async { tideRepository.getVerifiedCurve(station.stationId, today) }
                val currentDeferred   = async { tideRepository.getTidalCurrents(station.stationId, today) }
                val noaaWindDeferred  = async {
                    if (station.hasWindSensor)
                        windRepository.getNoaaWindObservations(station.stationId, today)
                    else null
                }
                val meteoWindDeferred = async { windRepository.getOpenMeteoForecast(station.lat, station.lon) }

                val noaaWindObs = noaaWindDeferred.await()
                val currentWind = noaaWindObs?.lastOrNull()
                    ?: meteoWindDeferred.await()?.hourly?.firstOrNull {
                        !it.time.isBefore(ZonedDateTime.now())
                    }

                val tideEvents     = eventsDeferred.await()
                val predictedCurve = curveDeferred.await().ifEmpty {
                    // Subordinate stations don't support 6-minute predictions — build a
                    // synthetic cosine curve from the H/L events so the chart still renders.
                    syntheticCurve(tideEvents, today)
                }

                _uiState.update {
                    it.copy(
                        isLoading      = false,
                        tideEvents     = tideEvents,
                        predictedCurve = predictedCurve,
                        verifiedCurve  = verifiedDeferred.await(),
                        currents       = currentDeferred.await(),
                        currentWind    = currentWind,
                        windForecast   = meteoWindDeferred.await(),
                        currentTime    = ZonedDateTime.now(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
