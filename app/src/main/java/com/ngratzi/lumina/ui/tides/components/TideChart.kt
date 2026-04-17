package com.ngratzi.lumina.ui.tides.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.TideEvent
import com.ngratzi.lumina.data.model.TideType
import com.ngratzi.lumina.data.model.WaterLevelSample
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun TideChart(
    palette: SkyPalette,
    predictedSamples: List<WaterLevelSample>,
    verifiedSamples: List<WaterLevelSample>?,
    tideEvents: List<TideEvent>,
    currentTime: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    if (predictedSamples.isEmpty()) return

    // Pulsing ring animation for the "now" dot
    val infiniteTransition = rememberInfiniteTransition(label = "tide_now")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue  = 14f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue  = 0.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    // Current height — interpolated from predicted (or verified if available)
    val sourceSamples = if (verifiedSamples?.isNotEmpty() == true) verifiedSamples else predictedSamples
    val currentHeightFt = remember(sourceSamples, currentTime) {
        interpolateHeight(sourceSamples, currentTime)
    }
    val isRising = remember(predictedSamples, currentTime) {
        isTideRising(predictedSamples, currentTime)
    }
    val nextEvent = remember(tideEvents, currentTime) {
        tideEvents.firstOrNull { it.time.isAfter(currentTime) }
    }

    Column(modifier = modifier) {
        // ── Header row: label + current height summary ────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "WATER LEVEL",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
            )
            if (currentHeightFt != null) {
                Text(
                    text = "${TimeFormatter.heightLabel(currentHeightFt)} ${if (isRising) "↑" else "↓"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
            }
        }

        // ── Chart canvas ──────────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(180.dp),
        ) {
            val w       = size.width
            val h       = size.height
            val padL    = 8f
            val padR    = 8f
            val padTop  = 20f   // space for "NOW" label + floating height label
            val padBot  = 16f

            // Y range
            val allH  = predictedSamples.map { it.heightFt }
            val minH  = (allH.minOrNull() ?: 0.0) - 0.4
            val maxH  = (allH.maxOrNull() ?: 10.0) + 0.4
            val hRange = maxH - minH

            // X range: midnight → midnight
            val startEpoch = predictedSamples.first().time.toLocalDate()
                .atStartOfDay(predictedSamples.first().time.zone).toInstant().toEpochMilli()
            val endEpoch = startEpoch + 24L * 60 * 60 * 1000

            fun xOf(epoch: Long) =
                padL + (w - padL - padR) * ((epoch - startEpoch).toFloat() / (endEpoch - startEpoch))
            fun yOf(ft: Double) =
                h - padBot - ((h - padTop - padBot) * ((ft - minH) / hRange)).toFloat()

            // ── Predicted curve ───────────────────────────────────────────────
            drawTideCurve(
                samples     = predictedSamples,
                xOf         = { xOf(it.time.toInstant().toEpochMilli()) },
                yOf         = { yOf(it.heightFt) },
                color       = palette.accent.copy(alpha = 0.65f),
                fillColor   = palette.accent.copy(alpha = 0.08f),
                bottomY     = yOf(minH),
                strokeWidth = 2f,
            )

            // ── Verified curve overlay (amber) ────────────────────────────────
            verifiedSamples?.takeIf { it.isNotEmpty() }?.let { verified ->
                drawTideCurve(
                    samples     = verified,
                    xOf         = { xOf(it.time.toInstant().toEpochMilli()) },
                    yOf         = { yOf(it.heightFt) },
                    color       = Color(0xFFE8A020).copy(alpha = 0.85f),
                    fillColor   = Color.Transparent,
                    bottomY     = yOf(minH),
                    strokeWidth = 1.5f,
                )
            }

            // ── H/L event dots ────────────────────────────────────────────────
            tideEvents.forEach { event ->
                val ex = xOf(event.time.toInstant().toEpochMilli())
                val ey = yOf(event.heightFt)
                drawCircle(
                    color  = if (event.type == TideType.HIGH) palette.accent else palette.onSurfaceVariant,
                    radius = 3.5f,
                    center = Offset(ex, ey),
                )
            }

            // ── Zero line ─────────────────────────────────────────────────────
            if (minH < 0) {
                val zeroY = yOf(0.0)
                drawLine(
                    color       = palette.onSurfaceVariant.copy(alpha = 0.25f),
                    start       = Offset(padL, zeroY),
                    end         = Offset(w - padR, zeroY),
                    strokeWidth = 0.5f,
                )
            }

            // ── Current time vertical line ────────────────────────────────────
            val nowEpoch = currentTime.toInstant().toEpochMilli()
            val nowX = xOf(nowEpoch).coerceIn(padL, w - padR)

            drawLine(
                color       = Color.White.copy(alpha = 0.55f),
                start       = Offset(nowX, padTop),
                end         = Offset(nowX, h - padBot),
                strokeWidth = 1f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )

            // "NOW" label above the line
            drawContext.canvas.nativeCanvas.drawText(
                "NOW",
                nowX,
                padTop - 4f,
                AndroidPaint().apply {
                    color       = Color.White.copy(alpha = 0.70f).toArgb()
                    textSize    = 18f
                    textAlign   = AndroidPaint.Align.CENTER
                    isAntiAlias = true
                    typeface    = Typeface.DEFAULT_BOLD
                },
            )

            // ── "Now" dot + pulse + height label ─────────────────────────────
            if (currentHeightFt != null) {
                val dotY = yOf(currentHeightFt)

                // Horizontal guide line at current height
                drawLine(
                    color       = palette.accent.copy(alpha = 0.18f),
                    start       = Offset(padL, dotY),
                    end         = Offset(w - padR, dotY),
                    strokeWidth = 0.8f,
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                )

                // Pulse ring
                drawCircle(
                    color  = Color.White.copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = Offset(nowX, dotY),
                )
                // Outer ring
                drawCircle(
                    color  = Color.White.copy(alpha = 0.50f),
                    radius = 6f,
                    center = Offset(nowX, dotY),
                    style  = Stroke(1.5f),
                )
                // Solid dot
                drawCircle(
                    color  = Color.White,
                    radius = 4f,
                    center = Offset(nowX, dotY),
                )

                // Floating height label — above dot if in lower half, below if in upper half
                val labelAbove = dotY > h * 0.5f
                val labelY = if (labelAbove) dotY - 10f else dotY + 22f
                val labelX = (nowX + 10f).coerceIn(padL + 4f, w - padR - 4f)
                val direction = if (isRising) "↑" else "↓"
                drawContext.canvas.nativeCanvas.drawText(
                    "${TimeFormatter.heightLabel(currentHeightFt)} $direction",
                    labelX,
                    labelY,
                    AndroidPaint().apply {
                        color       = Color.White.copy(alpha = 0.92f).toArgb()
                        textSize    = 24f
                        textAlign   = AndroidPaint.Align.LEFT
                        isAntiAlias = true
                        typeface    = Typeface.DEFAULT_BOLD
                    },
                )
            }
        }

        // ── Next event countdown ──────────────────────────────────────────────
        nextEvent?.let { event ->
            val mins  = Duration.between(currentTime, event.time).toMinutes().coerceAtLeast(0)
            val label = if (event.type == TideType.HIGH) "High tide" else "Low tide"
            val dir   = if (isRising) "Rising" else "Falling"
            Text(
                text = "$dir · $label in ${formatCountdown(mins)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
            )
        }

        // ── H/L event labels ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            tideEvents.take(4).forEach { event ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = if (event.type == TideType.HIGH) "H" else "L",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (event.type == TideType.HIGH) palette.accent else palette.onSurfaceVariant,
                    )
                    Text(
                        text  = TimeFormatter.formatTime(event.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurface,
                    )
                    Text(
                        text  = TimeFormatter.heightLabel(event.heightFt),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Legend (only when verified data is present) ───────────────────────
        if (verifiedSamples?.isNotEmpty() == true) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(palette.accent,         "Predicted", palette)
                LegendDot(Color(0xFFE8A020), "Observed",  palette)
            }
        }
    }
}

// ─── Curve drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawTideCurve(
    samples: List<WaterLevelSample>,
    xOf: (WaterLevelSample) -> Float,
    yOf: (WaterLevelSample) -> Float,
    color: Color,
    fillColor: Color,
    bottomY: Float,
    strokeWidth: Float,
) {
    if (samples.size < 2) return

    val points = samples.map { Offset(xOf(it), yOf(it)) }
    val path   = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points.getOrElse(i - 1) { points[i] }
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points.getOrElse(i + 2) { p2 }
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y,
            )
        }
    }

    if (fillColor != Color.Transparent) {
        val fill = Path().apply {
            addPath(path)
            lineTo(points.last().x, bottomY)
            lineTo(points.first().x, bottomY)
            close()
        }
        drawPath(fill, fillColor)
    }

    drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/** Linearly interpolates tide height at [now] from the nearest bracketing samples. */
private fun interpolateHeight(samples: List<WaterLevelSample>, now: ZonedDateTime): Double? {
    val epoch  = now.toInstant().toEpochMilli()
    val before = samples.lastOrNull  { it.time.toInstant().toEpochMilli() <= epoch } ?: return null
    val after  = samples.firstOrNull { it.time.toInstant().toEpochMilli() >  epoch } ?: return before.heightFt
    val t0     = before.time.toInstant().toEpochMilli().toDouble()
    val t1     = after.time.toInstant().toEpochMilli().toDouble()
    val frac   = ((epoch - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
    return before.heightFt + (after.heightFt - before.heightFt) * frac
}

/** True if the tide is currently rising (next sample higher than current). */
private fun isTideRising(samples: List<WaterLevelSample>, now: ZonedDateTime): Boolean {
    val epoch  = now.toInstant().toEpochMilli()
    val before = samples.lastOrNull  { it.time.toInstant().toEpochMilli() <= epoch } ?: return true
    val after  = samples.firstOrNull { it.time.toInstant().toEpochMilli() >  epoch } ?: return false
    return after.heightFt > before.heightFt
}

private fun formatCountdown(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else          -> "${minutes}m"
}

@Composable
private fun LegendDot(color: Color, label: String, palette: SkyPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color, radius = size.minDimension / 2)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
    }
}
