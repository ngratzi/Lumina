package com.ngratzi.lumina.ui.charts

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.Track
import com.ngratzi.lumina.data.model.TrackPoint
import com.ngratzi.lumina.data.model.Waypoint
import com.ngratzi.lumina.data.model.WaypointIcon
import com.ngratzi.lumina.data.repository.TrackRepository
import com.ngratzi.lumina.data.repository.WaypointRepository
import com.ngratzi.lumina.service.TrackRecordingService
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val location: Location?           = null,
    val isLocating: Boolean           = true,
    val initialCenterDone: Boolean    = false,
    val selectedLayer: ChartLayer     = ChartLayer.NAUTICAL,
    // Waypoints
    val waypoints: List<Waypoint>     = emptyList(),
    val isPlacingWaypoint: Boolean    = false,
    val pendingWaypointLat: Double?   = null,
    val pendingWaypointLon: Double?   = null,
    val showWaypointEditor: Boolean   = false,
    val activeWaypoint: Waypoint?     = null,
    val selectedWaypoint: Waypoint?   = null,
    val showWaypointList: Boolean     = false,
    // Tracks
    val tracks: List<Track>           = emptyList(),
    val trackPoints: Map<Long, List<TrackPoint>> = emptyMap(),
    val showTrackList: Boolean        = false,
    val isRecording: Boolean          = false,
    val activeTrackId: Long?          = null,
    val recordingDistanceNm: Double   = 0.0,
)

@HiltViewModel
class ChartsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationHelper: LocationHelper,
    private val waypointRepository: WaypointRepository,
    private val trackRepository: TrackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChartsUiState())
    val uiState: StateFlow<ChartsUiState> = _uiState.asStateFlow()

    init {
        locateMe()
        viewModelScope.launch {
            waypointRepository.observeWaypoints().collect { wps ->
                _uiState.update { it.copy(waypoints = wps) }
            }
        }
        viewModelScope.launch {
            trackRepository.observeTracks().collect { tracks ->
                _uiState.update { it.copy(tracks = tracks) }
                loadVisibleTrackPoints(tracks)
            }
        }
        viewModelScope.launch {
            TrackRecordingService.state.collect { rs ->
                _uiState.update { it.copy(
                    isRecording         = rs.isRecording,
                    activeTrackId       = rs.trackId,
                    recordingDistanceNm = rs.distanceNm,
                )}
            }
        }
    }

    private fun loadVisibleTrackPoints(tracks: List<Track>) {
        viewModelScope.launch {
            val points = mutableMapOf<Long, List<TrackPoint>>()
            for (track in tracks.filter { it.isVisible }) {
                points[track.id] = trackRepository.getTrackPoints(track.id)
            }
            _uiState.update { it.copy(trackPoints = points) }
        }
    }

    fun locateMe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val loc = locationHelper.getCurrentLocation()
            _uiState.update { it.copy(location = loc, isLocating = false) }
        }
    }

    fun setLayer(layer: ChartLayer) = _uiState.update { it.copy(selectedLayer = layer) }

    fun onInitialCenterDone() = _uiState.update { it.copy(initialCenterDone = true) }

    // ── Waypoint placement ────────────────────────────────────────────────────

    fun startPlacingWaypoint() = _uiState.update { it.copy(isPlacingWaypoint = true) }

    fun cancelPlacingWaypoint() = _uiState.update {
        it.copy(isPlacingWaypoint = false, pendingWaypointLat = null, pendingWaypointLon = null)
    }

    fun confirmCrosshairLocation(lat: Double, lon: Double) = _uiState.update {
        it.copy(
            isPlacingWaypoint  = false,
            pendingWaypointLat = lat,
            pendingWaypointLon = lon,
            showWaypointEditor = true,
        )
    }

    fun saveNewWaypoint(name: String, icon: WaypointIcon) {
        val lat = _uiState.value.pendingWaypointLat ?: return
        val lon = _uiState.value.pendingWaypointLon ?: return
        viewModelScope.launch {
            waypointRepository.save(Waypoint(name = name.ifBlank { "Waypoint" }, lat = lat, lon = lon, icon = icon))
        }
        _uiState.update { it.copy(pendingWaypointLat = null, pendingWaypointLon = null, showWaypointEditor = false) }
    }

    fun dismissWaypointEditor() = _uiState.update {
        it.copy(showWaypointEditor = false, pendingWaypointLat = null, pendingWaypointLon = null)
    }

    // ── Waypoint selection / navigation ───────────────────────────────────────

    fun selectWaypoint(wp: Waypoint) = _uiState.update { it.copy(selectedWaypoint = wp) }

    fun clearSelectedWaypoint() = _uiState.update { it.copy(selectedWaypoint = null) }

    fun navigateTo(wp: Waypoint) = _uiState.update {
        it.copy(activeWaypoint = wp, selectedWaypoint = null)
    }

    fun stopNavigation() = _uiState.update { it.copy(activeWaypoint = null) }

    fun showWaypointList() = _uiState.update { it.copy(showWaypointList = true) }

    fun hideWaypointList() = _uiState.update { it.copy(showWaypointList = false) }

    // ── Waypoint editing ──────────────────────────────────────────────────────

    fun updateWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            waypointRepository.update(waypoint)
            _uiState.update { s ->
                s.copy(
                    activeWaypoint   = if (s.activeWaypoint?.id == waypoint.id) waypoint else s.activeWaypoint,
                    selectedWaypoint = null,
                )
            }
        }
    }

    fun deleteWaypoint(id: Long) {
        viewModelScope.launch {
            waypointRepository.delete(id)
            _uiState.update { s ->
                s.copy(
                    activeWaypoint   = if (s.activeWaypoint?.id == id) null else s.activeWaypoint,
                    selectedWaypoint = null,
                )
            }
        }
    }

    // ── Track recording ───────────────────────────────────────────────────────

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            context.startService(Intent(context, TrackRecordingService::class.java).apply {
                action = TrackRecordingService.ACTION_STOP
            })
        } else {
            context.startForegroundService(Intent(context, TrackRecordingService::class.java).apply {
                action = TrackRecordingService.ACTION_START
            })
        }
    }

    // ── Track list management ─────────────────────────────────────────────────

    fun showTrackList() = _uiState.update { it.copy(showTrackList = true) }

    fun hideTrackList() = _uiState.update { it.copy(showTrackList = false) }

    fun toggleTrackVisibility(track: Track) {
        val updated = track.copy(isVisible = !track.isVisible)
        viewModelScope.launch {
            trackRepository.updateTrack(updated)
        }
    }

    fun renameTrack(track: Track, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { trackRepository.updateTrack(track.copy(name = newName)) }
    }

    fun setTrackColor(track: Track, color: Int) {
        viewModelScope.launch { trackRepository.updateTrack(track.copy(color = color)) }
    }

    fun deleteTrack(id: Long) {
        viewModelScope.launch { trackRepository.deleteTrack(id) }
    }
}
