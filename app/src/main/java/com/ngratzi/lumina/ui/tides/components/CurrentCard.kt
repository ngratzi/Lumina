package com.ngratzi.lumina.ui.tides.components

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.CurrentState
import com.ngratzi.lumina.data.model.TidalCurrent
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter

@Composable
fun CurrentCard(
    palette: SkyPalette,
    currents: List<TidalCurrent>,
    modifier: Modifier = Modifier,
) {
    val now = java.time.ZonedDateTime.now()
    val current = currents.lastOrNull { it.time.isBefore(now) } ?: currents.firstOrNull()
    val next = currents.firstOrNull { it.time.isAfter(now) }

    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TIDAL CURRENT", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            current?.let { c ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        val stateLabel = when (c.state) {
                            CurrentState.FLOOD -> "Flooding"
                            CurrentState.EBB   -> "Ebbing"
                            CurrentState.SLACK -> "Slack"
                        }
                        val stateColor = when (c.state) {
                            CurrentState.FLOOD -> Color(0xFF4499CC)
                            CurrentState.EBB   -> Color(0xFFCC8833)
                            CurrentState.SLACK -> palette.onSurfaceVariant
                        }
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = stateColor,
                        )
                        Text(
                            text = "${"%.1f".format(c.velocityKnots)} kt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurface,
                        )
                    }

                    // Current direction compass
                    c.directionDeg?.let { dir ->
                        CurrentCompass(
                            directionDeg = dir,
                            state = c.state,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                next?.let { n ->
                    Spacer(Modifier.height(12.dp))
                    Divider(color = palette.outlineColor, thickness = 0.5.dp)
                    Spacer(Modifier.height(8.dp))
                    val nextLabel = when (n.state) {
                        CurrentState.SLACK -> "Slack water"
                        CurrentState.FLOOD -> "Max flood"
                        CurrentState.EBB   -> "Max ebb"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(nextLabel, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                        Text(
                            TimeFormatter.formatTime(n.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurface,
                        )
                    }
                }
            } ?: Text(
                "No current data for today",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentCompass(
    directionDeg: Double,
    state: CurrentState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        CurrentState.FLOOD -> Color(0xFF4499CC)
        CurrentState.EBB   -> Color(0xFFCC8833)
        CurrentState.SLACK -> Color(0xFF888888)
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 - 4f

        drawCircle(color.copy(alpha = 0.12f), radius = r, center = Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.5f), radius = r, center = Offset(cx, cy), style = Stroke(1f))

        // N label tick
        drawLine(
            color = color.copy(alpha = 0.5f),
            start = Offset(cx, cy - r + 2f),
            end = Offset(cx, cy - r + 6f),
            strokeWidth = 1.5f,
        )

        rotate(directionDeg.toFloat(), Offset(cx, cy)) {
            val arrow = Path().apply {
                moveTo(cx, cy - r * 0.65f)
                lineTo(cx - r * 0.22f, cy + r * 0.3f)
                lineTo(cx, cy + r * 0.1f)
                lineTo(cx + r * 0.22f, cy + r * 0.3f)
                close()
            }
            drawPath(arrow, color)
        }
    }
}
