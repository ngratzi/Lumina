package com.ngratzi.lumina.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.TideType
import com.ngratzi.lumina.ui.home.components.*
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.tides.TidesViewModel
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
    tidesViewModel: TidesViewModel? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val skyTheme = LocalSkyTheme.current

    LaunchedEffect(uiState.skyPalette) {
        skyTheme.palette = uiState.skyPalette
    }

    val palette = skyTheme.palette
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) { if (pullState.isRefreshing) viewModel.refresh() }
    LaunchedEffect(uiState.isLoading) { if (!uiState.isLoading) pullState.endRefresh() }

    val tidesState = tidesViewModel?.uiState?.collectAsStateWithLifecycle()?.value

    // Compute tide display strings from live tides state
    val tideHeightText = tidesState?.let { tides ->
        if (tides.activeStation == null || tides.predictedCurve.isEmpty()) return@let null
        val now = tides.currentTime
        val height = tides.predictedCurve
            .minByOrNull { kotlin.math.abs(it.time.toEpochSecond() - now.toEpochSecond()) }
            ?.heightFt ?: return@let null
        val prevTide = tides.tideEvents.lastOrNull { !it.time.isAfter(now) }
        val nextTide = tides.tideEvents.firstOrNull { it.time.isAfter(now) }
        val isRising = nextTide?.type == TideType.HIGH
        val arrow = if (isRising) "↑" else "↓"

        // % of current phase (flood or ebb) already completed, height-based
        val phaseStr = if (prevTide != null && nextTide != null) {
            val range = kotlin.math.abs(nextTide.heightFt - prevTide.heightFt)
            if (range > 0.1) {
                val pct = if (isRising)
                    ((height - prevTide.heightFt) / range * 100).toInt().coerceIn(0, 100)
                else
                    ((prevTide.heightFt - height) / range * 100).toInt().coerceIn(0, 100)
                val label = if (isRising) "flood" else "ebb"
                "  ·  $pct% $label"
            } else null
        } else null

        "${"%.1f".format(height)} ft $arrow${phaseStr ?: ""}"
    }

    val nextTideText = tidesState?.let { tides ->
        if (tides.activeStation == null || tides.tideEvents.isEmpty()) return@let null
        val now = tides.currentTime
        val nextTide = tides.tideEvents.firstOrNull { it.time.isAfter(now) } ?: return@let null
        val dur = Duration.between(now, nextTide.time)
        val h = dur.toHours()
        val m = dur.toMinutes() % 60
        val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
        val label = if (nextTide.type == TideType.HIGH) "High" else "Low"
        "$label tide in $timeStr"
    }

    // Full-screen spinner only on initial load (no data yet)
    if (uiState.isLoading && uiState.sunTimes == null) {
        Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
        }
        return
    }

    uiState.error?.let { err ->
        if (uiState.sunTimes == null) {
            Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
                Text(
                    text     = err,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = palette.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            return
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            .nestedScroll(pullState.nestedScrollConnection),
    ) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Dial hero ─────────────────────────────────────────────────────
            item {
                SkyDial(
                    palette        = palette,
                    skyState       = uiState.skyState,
                    currentTime    = uiState.currentTime,
                    locationName   = uiState.locationName,
                    sunTimes       = uiState.sunTimes,
                    moonData       = uiState.moonData,
                    tideHeightText = tideHeightText,
                    nextTideText   = nextTideText,
                )
            }

            // ── Dense solar / lunar event list ────────────────────────────────
            uiState.sunTimes?.let { sunTimes ->
                item {
                    SolarEventList(
                        palette  = palette,
                        sunTimes = sunTimes,
                        moonData = uiState.moonData,
                    )
                }
            }
        }
        PullToRefreshContainer(pullState, Modifier.align(Alignment.TopCenter))
    }
}
