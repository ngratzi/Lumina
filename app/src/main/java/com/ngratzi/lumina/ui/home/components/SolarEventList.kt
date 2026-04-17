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
    HorizontalDivider(
        color = palette.outlineColor,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // ── Solar column ──────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            EventSectionHeader(palette, "SOLAR")
            Spacer(Modifier.height(8.dp))
            SolarRow(palette, "Astro dawn",  sunTimes.astronomicalDawn)
            SolarRow(palette, "Nautical",    sunTimes.nauticalDawn)
            SolarRow(palette, "Blue hour",   sunTimes.blueHourStart)
            SolarRow(palette, "Sunrise",     sunTimes.sunrise,       accent = true)
            SolarRow(palette, "Golden hr",   sunTimes.goldenHourEnd)
            SolarRow(palette, "Solar noon",  sunTimes.solarNoon)
            SolarRow(palette, "Golden hr",   sunTimes.goldenHourStart)
            SolarRow(palette, "Sunset",      sunTimes.sunset,        accent = true)
            SolarRow(palette, "Blue hour",   sunTimes.blueHourEnd)
            SolarRow(palette, "Nautical",    sunTimes.nauticalDusk)
            SolarRow(palette, "Astro dusk",  sunTimes.astronomicalDusk)
        }

        // ── Lunar column ──────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            EventSectionHeader(palette, "LUNAR")
            Spacer(Modifier.height(8.dp))
            if (moonData == null) {
                Text(
                    "No data",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            } else {
                // Phase name + emoji
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text  = "${moonData.phaseEmoji} ${moonData.phaseName}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = palette.onSurface,
                    )
                }
                LunarRow(palette, "Illum",   "${(moonData.illumination * 100).roundToInt()}%")
                LunarRow(palette, "Rise",    moonData.moonrise?.let { TimeFormatter.formatTime(it) } ?: "—")
                LunarRow(palette, "Transit", moonData.moonTransit?.let { TimeFormatter.formatTime(it) } ?: "—")
                LunarRow(palette, "Set",     moonData.moonset?.let { TimeFormatter.formatTime(it) } ?: "—")
                Spacer(Modifier.height(4.dp))
                LunarRow(palette, "Full",    "in ${moonData.daysToFullMoon}d")
                LunarRow(palette, "New",     "in ${moonData.daysToNewMoon}d")
                if (moonData.isPerigee || moonData.isApogee) {
                    Spacer(Modifier.height(4.dp))
                    val label  = if (moonData.isPerigee) "Perigee" else "Apogee"
                    val value  = "%,.0f km".format(moonData.distanceKm)
                    LunarRow(palette, label, value, valueColor = palette.accent)
                }
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun EventSectionHeader(palette: SkyPalette, label: String) {
    Text(
        text  = label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.onSurfaceVariant,
    )
}

@Composable
private fun SolarRow(
    palette: SkyPalette,
    label: String,
    time: ZonedDateTime?,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurfaceVariant,
        )
        Text(
            text  = time?.let { TimeFormatter.formatTime(it) } ?: "—",
            style = if (accent)
                        MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    else
                        MaterialTheme.typography.bodySmall,
            color = if (accent) palette.accent else palette.onSurface,
        )
    }
}

@Composable
private fun LunarRow(
    palette: SkyPalette,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = palette.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}
