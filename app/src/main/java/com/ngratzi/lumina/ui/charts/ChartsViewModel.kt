package com.ngratzi.lumina.ui.charts

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChartLayer(val displayName: String, val description: String) {
    NAUTICAL(  "Nautical",  "Street map · nav marks & aids"),
    OCEAN(     "Ocean",     "Depth contours & shading · global"),
    SATELLITE( "Satellite", "True-colour imagery · nav marks"),
}

data class ChartsUiState(
    val location: Location?        = null,
    val isLocating: Boolean        = false,
    val initialCenterDone: Boolean = false,
    val selectedLayer: ChartLayer  = ChartLayer.NAUTICAL,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val locationHelper: LocationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    init { locateMe() }

    fun locateMe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val loc = locationHelper.getCurrentLocation()
            _uiState.update { it.copy(location = loc, isLocating = false) }
        }
    }

    fun setLayer(layer: ChartLayer) = _uiState.update { it.copy(selectedLayer = layer) }

    fun onInitialCenterDone() = _uiState.update { it.copy(initialCenterDone = true) }
}
