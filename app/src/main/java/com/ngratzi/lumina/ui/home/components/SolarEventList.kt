package com.ngratzi.lumina.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.MoonData
import com.ngratzi.lumina.data.model.SunTimes
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun SolarEventList(
    palette: SkyPalette,
    sunTimes: SunTimes,
    moonData: MoonData?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Solar section ─────────────────────────────────────────────────────
        HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            EventSectionHeader(palette, "SOLAR")
            Spacer(Modifier.height(8.dp))
            SolarRow(palette, "Astro dawn",  sunTimes.astronomicalDawn,  sunTimes.nauticalDawn)
            SolarRow(palette, "Nautical",    sunTimes.nauticalDawn,      sunTimes.blueHourStart)
            SolarRow(palette, "Blue hour",   sunTimes.blueHourStart,     sunTimes.sunrise)
            SolarRow(palette, "Sunrise",     sunTimes.sunrise,           accent = true)
            SolarRow(palette, "Golden hr",   sunTimes.sunrise,           sunTimes.goldenHourEnd)
            SolarRow(palette, "Solar noon",  sunTimes.solarNoon)
            SolarRow(palette, "Golden hr",   sunTimes.goldenHourStart,   sunTimes.sunset)
            SolarRow(palette, "Sunset",      sunTimes.sunset,            accent = true)
            SolarRow(palette, "Blue hour",   sunTimes.sunset,            sunTimes.blueHourEnd)
            SolarRow(palette, "Nautical",    sunTimes.blueHourEnd,       sunTimes.nauticalDusk)
            SolarRow(palette, "Astro dusk",  sunTimes.nauticalDusk,      sunTimes.astronomicalDusk)
        }

        // ── Lunar section ─────────────────────────────────────────────────────
        HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            EventSectionHeader(palette, "LUNAR")
            Spacer(Modifier.height(8.dp))
            if (moonData == null) {
                Text("No data", style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant)
            } else {
                LunarRow(palette, "Phase",
                    "${moonData.phaseEmoji} ${moonData.phaseName}",
                    valueWeight = FontWeight.Medium)
                LunarRow(palette, "Illumination", "${(moonData.illumination * 100).roundToInt()}%")
                LunarRow(palette, "Rise",    moonData.moonrise?.let    { TimeFormatter.formatTime(it) } ?: "—")
                LunarRow(palette, "Transit", moonData.moonTransit?.let { TimeFormatter.formatTime(it) } ?: "—")
                LunarRow(palette, "Set",     moonData.moonset?.let     { TimeFormatter.formatTime(it) } ?: "—")
                Spacer(Modifier.height(4.dp))
                LunarRow(palette, "Full moon", "in ${moonData.daysToFullMoon}d")
                LunarRow(palette, "New moon",  "in ${moonData.daysToNewMoon}d")
                if (moonData.isPerigee || moonData.isApogee) {
                    Spacer(Modifier.height(4.dp))
                    val label = if (moonData.isPerigee) "Perigee" else "Apogee"
                    LunarRow(palette, label, "%,.0f km".format(moonData.distanceKm),
                        valueColor = palette.accent)
                }
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun EventSectionHeader(palette: SkyPalette, label: String) {
    Text(text = label, style = MaterialTheme.typography.labelSmall,
        color = palette.onSurfaceVariant)
}

@Composable
private fun SolarRow(
    palette: SkyPalette,
    label: String,
    time: ZonedDateTime?,
    endTime: ZonedDateTime? = null,
    accent: Boolean = false,
) {
    val timeText = when {
        time == null    -> "—"
        endTime != null -> "${TimeFormatter.formatTime(time)} – ${TimeFormatter.formatTime(endTime)}"
        else            -> TimeFormatter.formatTime(time)
    }
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = palette.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text     = timeText,
            style    = if (accent)
                           MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                       else
                           MaterialTheme.typography.bodySmall,
            color    = if (accent) palette.accent else palette.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun LunarRow(
    palette: SkyPalette,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = palette.onSurface,
    valueWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = valueWeight),
            color = valueColor)
    }
}
