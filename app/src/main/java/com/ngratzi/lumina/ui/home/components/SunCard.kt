package com.ngratzi.lumina.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.SunTimes
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime

@Composable
fun SunCard(
    palette: SkyPalette,
    sunTimes: SunTimes,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("SUN", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                SunIcon(color = palette.accent, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            SunRow(palette, "Rise",    sunTimes.sunrise)
            SunRow(palette, "Set",     sunTimes.sunset)
            SunRow(palette, "Noon",    sunTimes.solarNoon)
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))
            Text("Golden hour", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            sunTimes.sunrise?.let { rise ->
                sunTimes.goldenHourEnd?.let { end ->
                    Text(
                        "${TimeFormatter.formatTime(rise)} – ${TimeFormatter.formatTime(end)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurface,
                    )
                }
            }
            sunTimes.goldenHourStart?.let { start ->
                sunTimes.sunset?.let { set ->
                    Text(
                        "${TimeFormatter.formatTime(start)} – ${TimeFormatter.formatTime(set)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Blue hour", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            sunTimes.blueHourStart?.let { start ->
                sunTimes.sunrise?.let { rise ->
                    Text(
                        "${TimeFormatter.formatTime(start)} – ${TimeFormatter.formatTime(rise)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurface,
                    )
                }
            }
            sunTimes.sunset?.let { set ->
                sunTimes.blueHourEnd?.let { end ->
                    Text(
                        "${TimeFormatter.formatTime(set)} – ${TimeFormatter.formatTime(end)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f
        // Outer glow
        drawCircle(color.copy(alpha = 0.15f), r * 0.95f, Offset(cx, cy))
        // Mid glow
        drawCircle(color.copy(alpha = 0.35f), r * 0.62f, Offset(cx, cy))
        // Core
        drawCircle(color.copy(alpha = 0.95f), r * 0.38f, Offset(cx, cy))
    }
}

@Composable
private fun SunRow(palette: SkyPalette, label: String, time: ZonedDateTime?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
        Text(
            time?.let { TimeFormatter.formatTime(it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurface,
        )
    }
}
