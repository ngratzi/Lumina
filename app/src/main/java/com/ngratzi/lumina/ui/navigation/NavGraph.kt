package com.ngratzi.lumina.ui.navigation

import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ngratzi.lumina.ui.charts.ChartsScreen
import com.ngratzi.lumina.ui.home.HomeScreen
import com.ngratzi.lumina.ui.settings.SettingsScreen
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.tides.TidesScreen
import com.ngratzi.lumina.ui.weather.WeatherScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home     : Screen("home",     "Home",    Icons.Rounded.WbSunny)
    data object Tides    : Screen("tides",    "Tides",   Icons.Rounded.Waves)
    data object Weather  : Screen("weather",  "Weather", Icons.Rounded.Cloud)
    data object Charts   : Screen("charts",   "Charts",  Icons.Rounded.Map)
    data object Settings : Screen("settings", "Settings",Icons.Rounded.Settings)
}

private val bottomNavItems = listOf(Screen.Home, Screen.Tides, Screen.Weather, Screen.Charts, Screen.Settings)

@Composable
fun LuminaNavGraph() {
    val navController = rememberNavController()
    val skyTheme = LocalSkyTheme.current
    val palette = skyTheme.palette

    Scaffold(
        containerColor = palette.gradientTop,
        bottomBar = {
            NavigationBar(
                containerColor = palette.surfaceDim,
                tonalElevation = 0.dp,
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.accent,
                            selectedTextColor = palette.accent,
                            unselectedIconColor = palette.onSurfaceVariant,
                            unselectedTextColor = palette.onSurfaceVariant,
                            indicatorColor = palette.surfaceContainer,
                        ),
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route)     { HomeScreen(innerPadding) }
            composable(Screen.Tides.route)    { TidesScreen(innerPadding) }
            composable(Screen.Weather.route)  { WeatherScreen(innerPadding) }
            composable(Screen.Charts.route)   { ChartsScreen(innerPadding) }
            composable(Screen.Settings.route) { SettingsScreen(innerPadding) }
        }
    }
}
