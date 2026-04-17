package com.ngratzi.lumina

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.ngratzi.lumina.ui.navigation.LuminaNavGraph
import com.ngratzi.lumina.ui.theme.LuminaTheme
import com.ngratzi.lumina.ui.theme.SkyThemeState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ViewModel will re-query location after permission grant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                LuminaNavGraph()
            }
        }
    }
}
