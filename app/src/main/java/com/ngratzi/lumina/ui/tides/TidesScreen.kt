package com.ngratzi.lumina.ui.tides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.TideStation
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.tides.components.*
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidesScreen(
    innerPadding: PaddingValues,
    viewModel: TidesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalSkyTheme.current.palette
    var showStationPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = palette.accent,
            )
            return@Box
        }

        if (uiState.activeStation == null) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No tide station selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showStationPicker = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) {
                    Text("Add a station", color = palette.gradientTop)
                }
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Station header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiState.activeStation?.customLabel ?: uiState.activeStation?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            uiState.activeStation?.state ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showStationPicker = true }) {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = "Switch station", tint = palette.onSurfaceVariant)
                    }
                }
            }

            // Tide chart
            item {
                TideChart(
                    palette = palette,
                    predictedSamples = uiState.predictedCurve,
                    verifiedSamples = uiState.verifiedCurve,
                    tideEvents = uiState.tideEvents,
                    currentTime = uiState.currentTime,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // Tidal current card (only when data available)
            uiState.currents?.takeIf { it.isNotEmpty() }?.let { currents ->
                item {
                    CurrentCard(
                        palette = palette,
                        currents = currents,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // 7-day tide list
            item {
                TideEventList(
                    palette = palette,
                    events = uiState.tideEvents,
                    tailingTideThreshold = uiState.tailingTideThreshold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    // Station picker bottom sheet
    if (showStationPicker) {
        LaunchedEffect(Unit) {
            if (uiState.nearbyStations.isEmpty()) viewModel.searchNearbyStations()
        }
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.setStationSearchQuery("")
                showStationPicker = false
            },
            containerColor = palette.surfaceContainer,
        ) {
            StationPickerSheet(
                palette = palette,
                savedStations = uiState.savedStations,
                nearbyStations = uiState.nearbyStations,
                isSearching = uiState.isSearchingStations,
                searchError = uiState.searchError,
                activeStationId = uiState.activeStation?.stationId,
                searchQuery = uiState.stationSearchQuery,
                searchResults = uiState.stationSearchResults,
                userLat = uiState.userLat,
                userLon = uiState.userLon,
                onSearchQueryChange = { viewModel.setStationSearchQuery(it) },
                onSelectSaved = { stationId ->
                    viewModel.setActiveStation(stationId)
                    showStationPicker = false
                },
                onAddNearby = { station ->
                    viewModel.addAndActivateStation(station)
                    viewModel.setStationSearchQuery("")
                    showStationPicker = false
                },
                onRefreshSearch = { viewModel.searchNearbyStations() },
            )
        }
    }
}

// ─── Station picker sheet ──────────────────────────────────────────────────────

@Composable
private fun StationPickerSheet(
    palette: SkyPalette,
    savedStations: List<TideStation>,
    nearbyStations: List<TideStation>,
    isSearching: Boolean,
    searchError: String?,
    activeStationId: String?,
    searchQuery: String,
    searchResults: List<TideStation>,
    userLat: Double,
    userLon: Double,
    onSearchQueryChange: (String) -> Unit,
    onSelectSaved: (String) -> Unit,
    onAddNearby: (TideStation) -> Unit,
    onRefreshSearch: () -> Unit,
) {
    val savedIds = savedStations.map { it.stationId }.toSet()
    val isSearchActive = searchQuery.isNotBlank()

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Text(
            "Tide Stations",
            style = MaterialTheme.typography.titleMedium,
            color = palette.onSurface,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // ── Search field ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search by name or state…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = palette.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = palette.accent,
                unfocusedBorderColor  = palette.outlineColor,
                focusedTextColor      = palette.onSurface,
                unfocusedTextColor    = palette.onSurface,
                cursorColor           = palette.accent,
                focusedContainerColor = palette.surfaceDim,
                unfocusedContainerColor = palette.surfaceDim,
            ),
        )

        Spacer(Modifier.height(20.dp))

        // ── Saved stations (always shown) ────────────────────────────────────
        if (savedStations.isNotEmpty()) {
            SectionHeader(palette, "SAVED")
            savedStations.forEach { station ->
                val isActive = station.stationId == activeStationId
                StationRow(
                    palette      = palette,
                    name         = station.customLabel ?: station.name,
                    subtitle     = buildSubtitle(station.state, station.lat, station.lon, userLat, userLon),
                    isActive     = isActive,
                    actionLabel  = if (isActive) "Active" else "Select",
                    actionEnabled = !isActive,
                    onAction     = { onSelectSaved(station.stationId) },
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Search results or nearby ─────────────────────────────────────────
        if (isSearchActive) {
            SectionHeader(palette, "RESULTS")
            Spacer(Modifier.height(6.dp))
            when {
                isSearching -> LoadingRow(palette, "Searching…")
                searchResults.isEmpty() -> Text(
                    "No stations found for \"$searchQuery\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
                else -> searchResults.forEach { station ->
                    val alreadySaved = station.stationId in savedIds
                    StationRow(
                        palette       = palette,
                        name          = station.name,
                        subtitle      = buildSubtitle(station.state, station.lat, station.lon, userLat, userLon),
                        isActive      = false,
                        actionLabel   = if (alreadySaved) "Saved" else "Add",
                        actionEnabled = !alreadySaved,
                        onAction      = { if (!alreadySaved) onAddNearby(station) },
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(palette, "NEARBY")
                if (!isSearching) {
                    TextButton(onClick = onRefreshSearch, contentPadding = PaddingValues(0.dp)) {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall, color = palette.accent)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                isSearching -> LoadingRow(palette, "Finding stations near you…")
                searchError != null -> {
                    Text(searchError, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefreshSearch) {
                        Text("Try again", color = palette.accent)
                    }
                }
                nearbyStations.isEmpty() -> Text(
                    "Tap Refresh to find nearby stations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
                else -> nearbyStations.forEach { station ->
                    val alreadySaved = station.stationId in savedIds
                    StationRow(
                        palette       = palette,
                        name          = station.name,
                        subtitle      = buildSubtitle(station.state, station.lat, station.lon, userLat, userLon),
                        isActive      = false,
                        actionLabel   = if (alreadySaved) "Saved" else "Add",
                        actionEnabled = !alreadySaved,
                        onAction      = { if (!alreadySaved) onAddNearby(station) },
                    )
                }
            }
        }
    }
}

// ─── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(palette: SkyPalette, label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.onSurfaceVariant,
    )
}

@Composable
private fun LoadingRow(palette: SkyPalette, message: String) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = palette.accent,
            strokeWidth = 2.dp,
        )
        Text(message, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
    }
}

@Composable
private fun StationRow(
    palette: SkyPalette,
    name: String,
    subtitle: String,
    isActive: Boolean,
    actionLabel: String,
    actionEnabled: Boolean = true,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isActive) palette.accent else palette.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onAction, enabled = actionEnabled) {
            Text(
                actionLabel,
                color = if (actionEnabled) palette.accent else palette.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Builds the subtitle for a station row: "SC · 12 mi" or just "SC" if location unknown.
 */
private fun buildSubtitle(
    state: String,
    stationLat: Double,
    stationLon: Double,
    userLat: Double,
    userLon: Double,
): String {
    val dist = if (userLat != 0.0 || userLon != 0.0) {
        val mi = haversineKm(userLat, userLon, stationLat, stationLon) * 0.621371
        if (mi < 10) " · ${"%.1f".format(mi)} mi" else " · ${"%.0f".format(mi)} mi"
    } else ""
    return if (state.isNotBlank()) "$state$dist" else dist.trimStart(' ', '·', ' ')
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r    = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = sin(dLat / 2).pow(2) +
               cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
