package com.ngratzi.lumina.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.ui.home.components.*
import com.ngratzi.lumina.ui.theme.LocalSkyTheme

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val skyTheme = LocalSkyTheme.current

    LaunchedEffect(uiState.skyPalette) {
        skyTheme.palette = uiState.skyPalette
    }

    val palette = skyTheme.palette

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

        uiState.error?.let { err ->
            Text(
                text     = err,
                style    = MaterialTheme.typography.bodyMedium,
                color    = palette.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
            return@Box
        }

        LazyColumn(
            modifier      = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Dial hero ─────────────────────────────────────────────────────
            item {
                SkyDial(
                    palette      = palette,
                    skyState     = uiState.skyState,
                    currentTime  = uiState.currentTime,
                    locationName = uiState.locationName,
                    sunTimes     = uiState.sunTimes,
                    moonData     = uiState.moonData,
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
    }
}
