package com.ngratzi.lumina.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.data.repository.WeatherRepository
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeatherUiState(
    val isLoading: Boolean = true,
    val weather: WeatherData? = null,
    val error: String? = null,
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val location = locationHelper.getLastLocation()
            if (location == null) {
                _uiState.update { it.copy(isLoading = false, error = "Location unavailable") }
                return@launch
            }
            val weather = weatherRepository.fetchWeather(location.latitude, location.longitude)
            if (weather == null) {
                _uiState.update { it.copy(isLoading = false, error = "Could not load weather data") }
            } else {
                _uiState.update { it.copy(isLoading = false, weather = weather) }
            }
        }
    }
}
