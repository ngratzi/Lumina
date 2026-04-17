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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.MoonData
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
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
                Column {
                    Text("MOON", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = moonData.phaseName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = palette.onSurface,
                    )
                    Text(
                        text = "${(moonData.illumination * 100).roundToInt()}% illuminated",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.accent,
                    )
                }
                MoonPhaseIcon(phase = moonData.phase, modifier = Modifier.size(52.dp))
            }

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
private fun MoonPhaseIcon(phase: Double, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx     = size.width / 2f
        val cy     = size.height / 2f
        val radius = size.minDimension / 2f
        val rect   = Rect(cx - radius, cy - radius, cx + radius, cy + radius)

        val litColor  = Color(0xFFEEF4FF)
        val darkColor = Color(0xFF0A0A1E)

        // Glow behind lit portion
        val illumination = ((1 - cos(2 * PI * phase)) / 2).toFloat()
        if (illumination > 0.05f) {
            drawCircle(litColor.copy(alpha = illumination * 0.20f), radius * 1.25f, Offset(cx, cy))
        }

        // Dark disc base
        drawCircle(darkColor, radius, Offset(cx, cy))

        val terminatorScale = abs(cos(phase * 2 * PI)).toFloat()
        val termRect = Rect(
            cx - terminatorScale * radius, cy - radius,
            cx + terminatorScale * radius, cy + radius,
        )

        if (phase <= 0.5) {
            // Right half lit
            val litPath = Path().apply { arcTo(rect, -90f, 180f, true); close() }
            clipRect(cx, cy - radius, cx + radius, cy + radius) {
                drawPath(litPath, litColor)
            }
            if (terminatorScale > 0.02f) {
                val termColor = if (phase < 0.25) darkColor else litColor
                drawPath(Path().apply { addOval(termRect) }, termColor)
            }
        } else {
            // Left half lit
            val litPath = Path().apply { arcTo(rect, 90f, 180f, true); close() }
            clipRect(cx - radius, cy - radius, cx, cy + radius) {
                drawPath(litPath, litColor)
            }
            if (terminatorScale > 0.02f) {
                val termColor = if (phase < 0.75) litColor else darkColor
                drawPath(Path().apply { addOval(termRect) }, termColor)
            }
        }

        // Rim
        drawCircle(litColor.copy(alpha = 0.20f), radius, Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
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
