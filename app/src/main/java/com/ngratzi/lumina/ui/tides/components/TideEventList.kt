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
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.TideEvent
import com.ngratzi.lumina.data.model.TideType
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.LocalDate

@Composable
fun TideEventList(
    palette: SkyPalette,
    events: List<TideEvent>,
    tailingTideThreshold: Double = 5.8,
    modifier: Modifier = Modifier,
) {
    val byDay = events.groupBy { it.time.toLocalDate() }

    Column(modifier = modifier) {
        Text(
            "7-DAY TIDES",
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        byDay.entries.sortedBy { it.key }.forEach { (date, dayEvents) ->
            TideDaySection(palette = palette, date = date, events = dayEvents, tailingTideThreshold = tailingTideThreshold)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TideDaySection(
    palette: SkyPalette,
    date: LocalDate,
    events: List<TideEvent>,
    tailingTideThreshold: Double,
) {
    val isToday = date == LocalDate.now()
    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (isToday) "Today · ${TimeFormatter.formatDate(events.first().time)}"
                       else TimeFormatter.formatDate(events.first().time),
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) palette.accent else palette.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            events.sortedBy { it.time }.forEach { event ->
                TideEventRow(palette = palette, event = event, tailingTideThreshold = tailingTideThreshold)
            }
        }
    }
}

@Composable
private fun TideEventRow(
    palette: SkyPalette,
    event: TideEvent,
    tailingTideThreshold: Double,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // H / L badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (event.type == TideType.HIGH)
                    palette.accent.copy(alpha = 0.2f)
                else
                    palette.outlineColor.copy(alpha = 0.5f),
            ) {
                Text(
                    text = if (event.type == TideType.HIGH) "H" else "L",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.type == TideType.HIGH) palette.accent else palette.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = TimeFormatter.formatTime(event.time),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurface,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (event.type == TideType.HIGH && event.heightFt > tailingTideThreshold) {
                FishTailIcon(color = palette.accent)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = TimeFormatter.heightLabel(event.heightFt),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onSurface,
            )
            if (event.isVerified) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "obs",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Fish tail indicator ───────────────────────────────────────────────────────

/**
 * A small forked fish-tail shape — two curved fins meeting at a central notch,
 * pointing left (as if the body is to the right, tail fanning out to the left).
 */
@Composable
private fun FishTailIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(width = 20.dp, height = 16.dp),
    ) {
        val w = size.width
        val h = size.height
        val notchX = w * 0.42f   // the center notch where the two fins meet
        val notchY = h / 2f

        val path = Path().apply {
            // Top fin: from notch → upper-right tip → curves back in
            moveTo(notchX, notchY)
            cubicTo(
                notchX + w * 0.15f, notchY - h * 0.25f,
                w,                  0f,
                w * 0.72f,          notchY - h * 0.08f,
            )
            // Back to notch
            lineTo(notchX, notchY)
            // Bottom fin
            cubicTo(
                notchX + w * 0.15f, notchY + h * 0.25f,
                w,                  h,
                w * 0.72f,          notchY + h * 0.08f,
            )
            close()
        }

        drawPath(path, color.copy(alpha = 0.85f))
        drawPath(path, color, style = Stroke(width = 0.8f))
    }
}
