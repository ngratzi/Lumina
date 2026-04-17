package com.ngratzi.lumina.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.ui.alarms.AlarmGroup
import com.ngratzi.lumina.ui.alarms.AlarmsViewModel
import com.ngratzi.lumina.ui.alarms.solarGroup
import com.ngratzi.lumina.ui.alarms.tideGroup
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.theme.SkyPalette

@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
    alarmsViewModel: AlarmsViewModel = hiltViewModel(),
) {
    val palette = LocalSkyTheme.current.palette
    val tailingThreshold by viewModel.tailingTideThreshold.collectAsStateWithLifecycle()
    val alarms by alarmsViewModel.alarms.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS at runtime (required Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled silently; user can manage in system settings */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }

        item {
            SettingsGroup(palette = palette, title = "Location") {
                SettingsRow(palette, "Solar location", "GPS (auto)")
                SettingsRow(palette, "Active tide station", "Tap Tides screen to change")
            }
        }

        item {
            SettingsGroup(palette = palette, title = "Units") {
                SettingsToggleRow(palette, "24-hour time", false, {})
                SettingsToggleRow(palette, "Metric heights (m)", false, {})
                SettingsToggleRow(palette, "Wind in mph", false, {})
            }
        }

        item {
            SettingsGroup(palette = palette, title = "Display") {
                SettingsToggleRow(palette, "Show verified tide overlay", true, {})
                SettingsToggleRow(palette, "Show current overlay on chart", true, {})
            }
        }

        item {
            SettingsGroup(palette = palette, title = "Tides") {
                SettingsSliderRow(
                    palette     = palette,
                    label       = "Tailing tide indicator",
                    description = "Show fish tail on HIGH tides above this height",
                    value       = tailingThreshold.toFloat(),
                    onValueChange = { viewModel.setTailingTideThreshold(it.toDouble()) },
                    valueRange  = 3f..12f,
                    valueLabel  = "${"%.1f".format(tailingThreshold)} ft",
                )
            }
        }

        item {
            SettingsGroup(palette = palette, title = "Tide Stations") {
                SettingsRow(palette, "Manage stations", "Up to 5 saved")
            }
        }

        item {
            Text(
                "ALARMS",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
            )
        }

        item {
            AlarmGroup(
                palette = palette,
                title = "Solar & Lunar",
                alarms = alarms.filter { it.event in solarGroup },
                onToggle = { event, enabled -> alarmsViewModel.setEnabled(event, enabled) },
                onOffsetChange = { event, offset -> alarmsViewModel.setOffset(event, offset) },
            )
        }

        item {
            AlarmGroup(
                palette = palette,
                title = "Tides & Currents",
                alarms = alarms.filter { it.event in tideGroup },
                onToggle = { event, enabled -> alarmsViewModel.setEnabled(event, enabled) },
                onOffsetChange = { event, offset -> alarmsViewModel.setOffset(event, offset) },
            )
        }

        item {
            Text(
                "QA / TESTING",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp),
            )
        }

        item {
            SettingsGroup(palette = palette, title = "Force notifications") {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Fires a test notification immediately regardless of alarm toggle state. " +
                        "Use to verify notifications are working.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val qaEvents = listOf(
                        SolarEvent.SUNRISE,
                        SolarEvent.GOLDEN_HOUR_MORNING,
                        SolarEvent.SUNSET,
                        SolarEvent.GOLDEN_HOUR_EVENING,
                        SolarEvent.BLUE_HOUR_MORNING,
                        SolarEvent.MOONRISE,
                    )
                    qaEvents.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { event ->
                                OutlinedButton(
                                    onClick = { viewModel.fireTestAlarm(event) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = palette.accent,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp, palette.accent.copy(alpha = 0.5f),
                                    ),
                                ) {
                                    Text(
                                        event.displayName.replace(" (Morning)", "")
                                            .replace(" (Evening)", ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Setting group container ──────────────────────────────────────────────────

@Composable
private fun SettingsGroup(
    palette: SkyPalette,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(0.5.dp, palette.outlineColor, shape),
            color = palette.surfaceDim,
            shape = shape,
        ) {
            Column(modifier = Modifier.padding(4.dp), content = content)
        }
    }
}

// ─── Row types ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(palette: SkyPalette, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggleRow(
    palette: SkyPalette,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = palette.gradientTop,
                checkedTrackColor   = palette.accent,
                uncheckedThumbColor = palette.onSurfaceVariant,
                uncheckedTrackColor = palette.outlineColor,
            ),
        )
    }
}

@Composable
private fun SettingsSliderRow(
    palette: SkyPalette,
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
            Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = palette.accent)
        }
        Text(
            text     = description,
            style    = MaterialTheme.typography.bodySmall,
            color    = palette.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            modifier      = Modifier.fillMaxWidth(),
            colors        = SliderDefaults.colors(
                thumbColor          = palette.accent,
                activeTrackColor    = palette.accent,
                inactiveTrackColor  = palette.outlineColor,
            ),
        )
    }
}
