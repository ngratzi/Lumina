package com.ngratzi.lumina.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.MoonData
import com.ngratzi.lumina.data.model.SunTimes
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
fun SolarEventList(
    palette: SkyPalette,
    sunTimes: SunTimes,
    moonData: MoonData?,
    currentTime: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        // ── Solar section ─────────────────────────────────────────────────────
        HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SOLAR", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                SunIcon(color = palette.accent, modifier = Modifier.size(32.dp))
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LUNAR", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                if (moonData != null) {
                    MoonPhaseIcon(phase = moonData.phase, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (moonData == null) {
                Text("No data", style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant)
            } else {
                LunarRow(palette, "Phase",
                    "${moonData.phaseEmoji} ${moonData.phaseName}",
                    valueWeight = FontWeight.Medium)
                LunarRow(palette, "Illumination", "${(moonData.illumination * 100).roundToInt()}%")
                val localMidnight = currentTime.toLocalDate().atStartOfDay(currentTime.zone)
                val moonriseDisplay = moonData.moonrise?.let { rise ->
                    val t = TimeFormatter.formatTime(rise)
                    if (rise.isBefore(localMidnight)) "← $t" else t
                } ?: "—"
                LunarRow(palette, "Rise", moonriseDisplay)
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
private fun SunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.15f), r * 0.95f, Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.35f), r * 0.62f, Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.95f), r * 0.38f, Offset(cx, cy))
    }
}

@Composable
private fun MoonPhaseIcon(phase: Double, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx     = size.width / 2f
        val cy     = size.height / 2f
        val radius = size.minDimension / 2f
        val rect   = Rect(cx - radius, cy - radius, cx + radius, cy + radius)
        val litColor  = Color(0xFFEEF4FF)
        val darkColor = Color(0xFF0A0A1E)

        val illumination = ((1 - cos(2 * PI * phase)) / 2).toFloat()
        if (illumination > 0.05f) {
            drawCircle(litColor.copy(alpha = illumination * 0.20f), radius * 1.25f, Offset(cx, cy))
        }
        drawCircle(darkColor, radius, Offset(cx, cy))

        val terminatorScale = abs(cos(phase * 2 * PI)).toFloat()
        val termRect = Rect(
            cx - terminatorScale * radius, cy - radius,
            cx + terminatorScale * radius, cy + radius,
        )
        if (phase <= 0.5) {
            val litPath = Path().apply { arcTo(rect, -90f, 180f, true); close() }
            clipRect(cx, cy - radius, cx + radius, cy + radius) { drawPath(litPath, litColor) }
            if (terminatorScale > 0.02f) {
                drawPath(Path().apply { addOval(termRect) }, if (phase < 0.25) darkColor else litColor)
            }
        } else {
            val litPath = Path().apply { arcTo(rect, 90f, 180f, true); close() }
            clipRect(cx - radius, cy - radius, cx, cy + radius) { drawPath(litPath, litColor) }
            if (terminatorScale > 0.02f) {
                drawPath(Path().apply { addOval(termRect) }, if (phase < 0.75) litColor else darkColor)
            }
        }
        drawCircle(litColor.copy(alpha = 0.20f), radius, Offset(cx, cy), style = Stroke(1.2f))
    }
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
