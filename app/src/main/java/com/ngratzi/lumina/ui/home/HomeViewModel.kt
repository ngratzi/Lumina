package com.ngratzi.lumina.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.data.repository.SolarRepository
import com.ngratzi.lumina.domain.SolarCalculator
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.theme.SkyThemeTokens
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val locationName: String = "",
    val currentTime: ZonedDateTime = ZonedDateTime.now(),
    val sunTimes: SunTimes? = null,
    val moonData: MoonData? = null,
    val skyState: SkyState = SkyState(
        phase = SkyPhase.NIGHT,
        solarAltitude = -90.0,
        isEvening = false,
        nextEventName = "",
        minutesUntilNextEvent = 0,
    ),
    val skyPalette: SkyPalette = SkyThemeTokens.Night,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val solarRepository: SolarRepository,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var lat = 0.0
    private var lon = 0.0

    init {
        loadLocation()
        startClock()
    }

    private fun loadLocation() {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location == null) {
                _uiState.update { it.copy(isLoading = false, error = "Location unavailable") }
                return@launch
            }
            lat = location.latitude
            lon = location.longitude
            val name = locationHelper.reverseGeocode(lat, lon)
            _uiState.update { it.copy(locationName = name ?: "") }
            loadSolarData()
        }
    }

    private fun loadSolarData(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val sunTimes = solarRepository.getSunTimes(date, lat, lon)
            val moonData = solarRepository.getMoonData(date, lat, lon)
            _uiState.update { state ->
                state.copy(
                    sunTimes = sunTimes,
                    moonData = moonData,
                    isLoading = false,
                )
            }
            updateSkyState()
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L) // update every 30s
                val now = ZonedDateTime.now()
                val prevDate = _uiState.value.currentTime.toLocalDate()
                _uiState.update { it.copy(currentTime = now) }

                // Reload solar data at midnight
                if (now.toLocalDate() != prevDate) {
                    loadSolarData(now.toLocalDate())
                } else {
                    updateSkyState()
                }
            }
        }
    }

    private fun updateSkyState() {
        val state = _uiState.value
        val now = state.currentTime
        val sunTimes = state.sunTimes ?: return

        val altitude = if (lat != 0.0) SolarCalculator.getSunAltitude(now, lat, lon) else -90.0
        val isEvening = if (lon != 0.0) SolarCalculator.isEvening(now, lon) else false
        val phase = solarAltitudeToPhase(altitude, isEvening)

        val (nextName, minutesUntil) = nextEvent(now, sunTimes)

        val skyState = SkyState(
            phase = phase,
            solarAltitude = altitude,
            isEvening = isEvening,
            nextEventName = nextName,
            minutesUntilNextEvent = minutesUntil,
        )

        // Compute smooth palette interpolation
        val phaseProgress = phaseProgress(altitude, isEvening, phase)
        val palette = SkyThemeTokens.interpolate(phase, phaseProgress)

        _uiState.update { it.copy(skyState = skyState, skyPalette = palette) }
    }

    private fun nextEvent(now: ZonedDateTime, sunTimes: SunTimes): Pair<String, Long> {
        fun eventsFor(st: SunTimes) = listOf(
            "Astronomical Dawn" to st.astronomicalDawn,
            "Nautical Dawn"     to st.nauticalDawn,
            "Blue Hour"         to st.blueHourStart,
            "Sunrise"           to st.sunrise,
            "Golden Hour"       to st.goldenHourEnd,
            "Golden Hour"       to st.goldenHourStart,
            "Sunset"            to st.sunset,
            "Blue Hour"         to st.blueHourEnd,
            "Nautical Dusk"     to st.nauticalDusk,
        )

        // Try today's events first
        val todayNext = eventsFor(sunTimes)
            .filter { (_, t) -> t != null && t.isAfter(now) }
            .minByOrNull { (_, t) -> t!! }

        if (todayNext != null) {
            val minutes = java.time.Duration.between(now, todayNext.second!!).toMinutes()
            return todayNext.first to minutes
        }

        // All today's events have passed (e.g. late night) — look at tomorrow
        val tomorrow = SolarCalculator.getSunTimes(
            now.toLocalDate().plusDays(1), lat, lon, now.zone
        )
        val tomorrowNext = eventsFor(tomorrow)
            .filter { (_, t) -> t != null && t.isAfter(now) }
            .minByOrNull { (_, t) -> t!! }
            ?: return "" to 0L

        val minutes = java.time.Duration.between(now, tomorrowNext.second!!).toMinutes()
        return tomorrowNext.first to minutes
    }

    private fun phaseProgress(altitude: Double, isEvening: Boolean, phase: SkyPhase): Float {
        return when (phase) {
            SkyPhase.NIGHT                  -> ((altitude + 90.0) / 72.0).toFloat()
            SkyPhase.ASTRONOMICAL_TWILIGHT  -> ((altitude + 18.0) / 6.0).toFloat()
            SkyPhase.NAUTICAL_TWILIGHT      -> ((altitude + 12.0) / 6.0).toFloat()
            SkyPhase.BLUE_HOUR_MORNING,
            SkyPhase.BLUE_HOUR_EVENING      -> ((altitude + 6.0) / 6.0).toFloat()
            SkyPhase.GOLDEN_HOUR_MORNING,
            SkyPhase.GOLDEN_HOUR_EVENING    -> (altitude / 6.0).toFloat()
            SkyPhase.DAYLIGHT               -> ((altitude - 6.0) / 60.0).toFloat()
        }.coerceIn(0f, 1f)
    }
}
