package com.ngratzi.lumina.ui.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngratzi.lumina.data.model.MoonData
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.roundToInt

@Composable
fun MoonCard(
    palette: SkyPalette,
    moonData: MoonData,
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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = moonData.phaseEmoji,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("MOON", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                }
                Text(
                    text = "${(moonData.illumination * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = moonData.phaseName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = palette.onSurface,
            )

            Spacer(Modifier.height(12.dp))
            MoonRow(palette, "Rise",  moonData.moonrise)
            MoonRow(palette, "Set",   moonData.moonset)

            Spacer(Modifier.height(4.dp))
            Divider(color = palette.outlineColor, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Full moon", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Text(
                        "in ${moonData.daysToFullMoon}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("New moon", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Text(
                        "in ${moonData.daysToNewMoon}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurface,
                    )
                }
            }

            if (moonData.isPerigee || moonData.isApogee) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (moonData.isPerigee) "Perigee — ${"%,.0f".format(moonData.distanceKm)} km"
                           else "Apogee — ${"%,.0f".format(moonData.distanceKm)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.accent,
                )
            }
        }
    }
}

@Composable
private fun MoonRow(palette: SkyPalette, label: String, time: ZonedDateTime?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        Text(
            time?.let { TimeFormatter.formatTime(it) } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = palette.onSurface,
        )
    }
}
