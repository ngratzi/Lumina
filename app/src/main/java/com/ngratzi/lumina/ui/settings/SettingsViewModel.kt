package com.ngratzi.lumina.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.GeocodedResult
import com.ngratzi.lumina.data.model.LocationMode
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.data.repository.UserPreferencesRepository
import com.ngratzi.lumina.data.repository.UserPreferencesRepository.Companion.DEFAULT_JEEP_MAX_RAIN
import com.ngratzi.lumina.data.repository.UserPreferencesRepository.Companion.DEFAULT_JEEP_MAX_TEMP
import com.ngratzi.lumina.data.repository.UserPreferencesRepository.Companion.DEFAULT_JEEP_MIN_TEMP
import com.ngratzi.lumina.service.AlarmScheduler
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val alarmScheduler: AlarmScheduler,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    val jeepEnabled: StateFlow<Boolean> = prefs.jeepEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val jeepMinTemp: StateFlow<Int> = prefs.jeepMinTemp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_JEEP_MIN_TEMP)
    val jeepMaxTemp: StateFlow<Int> = prefs.jeepMaxTemp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_JEEP_MAX_TEMP)
    val jeepMaxRain: StateFlow<Int> = prefs.jeepMaxRain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_JEEP_MAX_RAIN)

    val tailingTideThreshold: StateFlow<Double> = prefs.tailingTideThresholdFt
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UserPreferencesRepository.DEFAULT_TAILING_THRESHOLD,
        )

    val locationMode: StateFlow<LocationMode> = prefs.locationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationMode.GPS)

    val manualLocationName: StateFlow<String> = prefs.manualLocationName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _searchResults = MutableStateFlow<List<GeocodedResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodedResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setJeepEnabled(v: Boolean)  { viewModelScope.launch { prefs.setJeepEnabled(v) } }
    fun setJeepMinTemp(v: Int)      { viewModelScope.launch { prefs.setJeepMinTemp(v) } }
    fun setJeepMaxTemp(v: Int)      { viewModelScope.launch { prefs.setJeepMaxTemp(v) } }
    fun setJeepMaxRain(v: Int)      { viewModelScope.launch { prefs.setJeepMaxRain(v) } }

    fun setTailingTideThreshold(ft: Double) {
        viewModelScope.launch { prefs.setTailingTideThreshold(ft) }
    }

    fun fireTestAlarm(event: SolarEvent) {
        alarmScheduler.fireTestNotification(event)
    }

    fun setGpsMode() {
        viewModelScope.launch { prefs.setLocationMode(LocationMode.GPS) }
        _searchResults.value = emptyList()
    }

    fun searchLocations(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = locationHelper.forwardGeocode(query)
            _isSearching.value = false
        }
    }

    fun selectLocation(result: GeocodedResult) {
        viewModelScope.launch {
            prefs.setManualLocation(result.displayName, result.lat, result.lon)
            _searchResults.value = emptyList()
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }
}
