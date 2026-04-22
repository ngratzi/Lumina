package com.ngratzi.lumina

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ngratzi.lumina.ui.charts.ChartsViewModel
import com.ngratzi.lumina.ui.home.HomeViewModel
import com.ngratzi.lumina.ui.navigation.LuminaNavGraph
import com.ngratzi.lumina.ui.theme.LuminaTheme
import com.ngratzi.lumina.ui.theme.SkyThemeState
import com.ngratzi.lumina.ui.tides.TidesViewModel
import com.ngratzi.lumina.ui.weather.WeatherViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val weatherViewModel: WeatherViewModel by viewModels()
    private val tidesViewModel: TidesViewModel by viewModels()
    private val chartsViewModel: ChartsViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ViewModel will re-query location after permission grant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            homeViewModel.uiState.value.isLoading ||
            weatherViewModel.uiState.value.isLoading ||
            tidesViewModel.uiState.value.isLoading ||
            chartsViewModel.uiState.value.isLocating
        }

        enableEdgeToEdge()

        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )

        setContent {
            val skyTheme = remember { SkyThemeState() }
            LuminaTheme(skyTheme = skyTheme) {
                LuminaNavGraph(
                    homeViewModel = homeViewModel,
                    weatherViewModel = weatherViewModel,
                    tidesViewModel = tidesViewModel,
                    chartsViewModel = chartsViewModel,
                )
            }
        }
    }
}
