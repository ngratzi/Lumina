package com.ngratzi.lumina.ui.alarms

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.AlarmConfig
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.theme.SkyPalette

private val solarGroup = listOf(
    SolarEvent.ASTRONOMICAL_DAWN,
    SolarEvent.BLUE_HOUR_MORNING,
    SolarEvent.GOLDEN_HOUR_MORNING,
    SolarEvent.SUNRISE,
    SolarEvent.GOLDEN_HOUR_EVENING,
    SolarEvent.SUNSET,
    SolarEvent.BLUE_HOUR_EVENING,
    SolarEvent.MOONRISE,
)

private val tideGroup = listOf(
    SolarEvent.HIGH_TIDE,
    SolarEvent.LOW_TIDE,
    SolarEvent.SLACK_WATER_FLOOD,
    SolarEvent.SLACK_WATER_EBB,
)

@Composable
fun AlarmsScreen(
    innerPadding: PaddingValues,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val palette = LocalSkyTheme.current.palette

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "ALARMS",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }

        item {
            AlarmGroup(
                palette = palette,
                title = "Solar & Lunar",
                alarms = alarms.filter { it.event in solarGroup },
                onToggle = { event, enabled -> viewModel.setEnabled(event, enabled) },
                onOffsetChange = { event, offset -> viewModel.setOffset(event, offset) },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            AlarmGroup(
                palette = palette,
                title = "Tides & Currents",
                alarms = alarms.filter { it.event in tideGroup },
                onToggle = { event, enabled -> viewModel.setEnabled(event, enabled) },
                onOffsetChange = { event, offset -> viewModel.setOffset(event, offset) },
            )
        }
    }
}

@Composable
private fun AlarmGroup(
    palette: SkyPalette,
    title: String,
    alarms: List<AlarmConfig>,
    onToggle: (SolarEvent, Boolean) -> Unit,
    onOffsetChange: (SolarEvent, Int) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            alarms.forEachIndexed { index, alarm ->
                AlarmRow(
                    palette = palette,
                    alarm = alarm,
                    onToggle = { onToggle(alarm.event, it) },
                    onOffsetChange = { onOffsetChange(alarm.event, it) },
                )
                if (index < alarms.lastIndex) {
                    Divider(color = palette.outlineColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    palette: SkyPalette,
    alarm: AlarmConfig,
    onToggle: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
) {
    var showOffsetPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                alarm.event.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (alarm.enabled) palette.onSurface else palette.onSurfaceVariant,
            )
            if (alarm.offsetMinutes != 0) {
                val sign = if (alarm.offsetMinutes < 0) "-" else "+"
                Text(
                    "${sign}${Math.abs(alarm.offsetMinutes)} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
        }

        TextButton(
            onClick = { showOffsetPicker = true },
            enabled = alarm.enabled,
        ) {
            Text(
                "offset",
                style = MaterialTheme.typography.labelSmall,
                color = if (alarm.enabled) palette.accent else palette.outlineColor,
            )
        }

        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.gradientTop,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.onSurfaceVariant,
                uncheckedTrackColor = palette.outlineColor,
            ),
        )
    }

    if (showOffsetPicker) {
        OffsetPickerDialog(
            palette = palette,
            currentOffset = alarm.offsetMinutes,
            onConfirm = { offset ->
                onOffsetChange(offset)
                showOffsetPicker = false
            },
            onDismiss = { showOffsetPicker = false },
        )
    }
}

@Composable
private fun OffsetPickerDialog(
    palette: SkyPalette,
    currentOffset: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val offsets = listOf(-60, -45, -30, -20, -15, -10, -5, 0, 5, 10, 15, 20, 30)
    var selected by remember { mutableIntStateOf(currentOffset) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceContainer,
        title = {
            Text("Notify me", style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
        },
        text = {
            Column {
                offsets.forEach { offset ->
                    val label = when {
                        offset < 0 -> "${-offset} min before"
                        offset == 0 -> "At event time"
                        else -> "$offset min after"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = offset == selected,
                            onClick = { selected = offset },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.accent),
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Set", color = palette.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.onSurfaceVariant)
            }
        },
    )
}
