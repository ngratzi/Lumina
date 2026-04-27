package com.ngratzi.lumina.ui.tides.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import android.graphics.Paint as AndroidPaint
import android.graphics.Color as AndroidColor
import com.ngratzi.lumina.data.model.CurrentState
import com.ngratzi.lumina.data.model.TidalCurrent
import com.ngratzi.lumina.data.model.WaterLevelSample
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

@Composable
fun CurrentCard(
    palette: SkyPalette,
    currents: List<TidalCurrent>?,
    predictedCurve: List<WaterLevelSample> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val now = java.time.ZonedDateTime.now()
    val shape = RoundedCornerShape(16.dp)

    // Build estimated curve from tide derivative when no station current data
    val estimatedCurve = remember(predictedCurve) {
        if (currents.isNullOrEmpty()) buildEstimatedCurve(predictedCurve) else emptyList()
    }
    val nowEstimated = remember(estimatedCurve, now) {
        estimatedCurve.minByOrNull { abs(it.first.toEpochSecond() - now.toEpochSecond()) }?.second
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val headerLabel = if (currents.isNullOrEmpty()) "TIDAL CURRENT  ·  EST." else "TIDAL CURRENT"
            Text(headerLabel, style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            if (!currents.isNullOrEmpty()) {
                // ── Real NOAA current data ─────────────────────────────────────
                val current = currents.lastOrNull { it.time.isBefore(now) } ?: currents.first()
                val next = currents.firstOrNull { it.time.isAfter(now) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        val stateLabel = when (current.state) {
                            CurrentState.FLOOD -> "Flooding"
                            CurrentState.EBB   -> "Ebbing"
                            CurrentState.SLACK -> "Slack"
                        }
                        val stateColor = when (current.state) {
                            CurrentState.FLOOD -> Color(0xFF4499CC)
                            CurrentState.EBB   -> Color(0xFFCC8833)
                            CurrentState.SLACK -> palette.onSurfaceVariant
                        }
                        Text(stateLabel, style = MaterialTheme.typography.headlineSmall, color = stateColor)
                        Text("${"%.1f".format(current.velocityKnots)} kt", style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                    }
                    current.directionDeg?.let { dir ->
                        CurrentCompass(directionDeg = dir, state = current.state, modifier = Modifier.size(56.dp))
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nextLabel, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
                        Text(TimeFormatter.formatTime(n.time), style = MaterialTheme.typography.bodySmall, color = palette.onSurface)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = palette.outlineColor, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                CurrentStrengthChart(
                    palette = palette,
                    curve = remember(currents) { buildCurrentCurve(currents, now.toLocalDate(), now.zone) },
                    isEstimated = false,
                    now = now,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )

            } else if (estimatedCurve.isNotEmpty()) {
                // ── Estimated from tide curve ──────────────────────────────────
                val label = nowEstimated?.let { estimatedStrengthLabel(it) } ?: "Calculating…"
                val isFlooding = (nowEstimated ?: 0.0) >= 0
                val labelColor = when {
                    nowEstimated == null || abs(nowEstimated) < 0.1 -> palette.onSurfaceVariant
                    isFlooding -> Color(0xFF4499CC)
                    else       -> Color(0xFFCC8833)
                }
                Text(label, style = MaterialTheme.typography.headlineSmall, color = labelColor)
                Spacer(Modifier.height(4.dp))
                Text("Estimated from tide curve", style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant.copy(alpha = 0.6f))

                Spacer(Modifier.height(16.dp))
                Divider(color = palette.outlineColor, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                CurrentStrengthChart(
                    palette = palette,
                    curve = estimatedCurve,
                    isEstimated = true,
                    now = now,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )

            } else {
                Text(
                    "No tide data available to estimate current",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Current strength chart ────────────────────────────────────────────────────

@Composable
private fun CurrentStrengthChart(
    palette: SkyPalette,
    curve: List<Pair<ZonedDateTime, Double>>,
    isEstimated: Boolean,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val today = now.toLocalDate()
    val zone = now.zone

    val floodColor = Color(0xFF4499CC)
    val ebbColor   = Color(0xFFCC8833)
    val gridColor  = Color.White.copy(alpha = 0.10f)
    val labelColor = Color.White.copy(alpha = 0.45f)

    val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayMs      = 24L * 60 * 60 * 1000
    val nowFrac    = ((now.toInstant().toEpochMilli() - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)

    val maxVel = (curve.maxOfOrNull { abs(it.second) } ?: 1.0).coerceAtLeast(if (isEstimated) 1.0 else 0.5)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val yPad = 16f
        val xPadL = 36f   // space for Y labels
        val xPadR = 8f
        val plotW = w - xPadL - xPadR
        val plotH = h - yPad * 2f
        val zeroY = h / 2f   // zero line is centered

        fun xOf(frac: Float) = xPadL + frac * plotW
        fun yOf(vel: Double) = zeroY - (vel / maxVel * (plotH / 2f)).toFloat()

        // ── Grid: zero line + ±max gridlines ─────────────────────────────────
        listOf(0.0, maxVel, -maxVel).forEach { v ->
            val y = yOf(v)
            drawLine(gridColor, Offset(xPadL, y), Offset(w - xPadR, y), strokeWidth = 1f)
        }

        // ── X gridlines at 6h intervals ───────────────────────────────────────
        listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { frac ->
            val x = xOf(frac)
            drawLine(gridColor, Offset(x, yPad), Offset(x, h - yPad), strokeWidth = 1f)
        }

        // ── Filled areas (flood above zero = blue, ebb below = orange) ────────
        if (curve.size >= 2) {
            // Positive (flood) fill
            val floodPath = Path()
            var inFlood = false
            curve.forEachIndexed { i, (t, v) ->
                val frac = ((t.toInstant().toEpochMilli() - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val x = xOf(frac)
                val y = yOf(v.coerceAtLeast(0.0))
                if (i == 0 || !inFlood) {
                    floodPath.moveTo(x, zeroY)
                    floodPath.lineTo(x, y)
                    inFlood = true
                } else {
                    floodPath.lineTo(x, y)
                }
            }
            floodPath.lineTo(xOf(1f), zeroY)
            floodPath.close()
            drawPath(floodPath, floodColor.copy(alpha = 0.18f))

            // Negative (ebb) fill
            val ebbPath = Path()
            var inEbb = false
            curve.forEachIndexed { i, (t, v) ->
                val frac = ((t.toInstant().toEpochMilli() - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val x = xOf(frac)
                val y = yOf(v.coerceAtMost(0.0))
                if (i == 0 || !inEbb) {
                    ebbPath.moveTo(x, zeroY)
                    ebbPath.lineTo(x, y)
                    inEbb = true
                } else {
                    ebbPath.lineTo(x, y)
                }
            }
            ebbPath.lineTo(xOf(1f), zeroY)
            ebbPath.close()
            drawPath(ebbPath, ebbColor.copy(alpha = 0.18f))

            // ── Line ──────────────────────────────────────────────────────────
            val linePath = Path()
            curve.forEachIndexed { i, (t, v) ->
                val frac = ((t.toInstant().toEpochMilli() - dayStartMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val x = xOf(frac)
                val y = yOf(v)
                val lineColor = if (v >= 0) floodColor else ebbColor
                if (i == 0) {
                    linePath.moveTo(x, y)
                } else {
                    val prevV = curve[i - 1].second
                    if ((v >= 0) != (prevV >= 0)) {
                        // Color changes at zero-crossing — draw segment up to this point
                        drawPath(linePath, if (prevV >= 0) floodColor else ebbColor,
                            style = Stroke(1.5f, cap = StrokeCap.Round))
                        linePath.reset()
                        linePath.moveTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                    }
                    if (i == curve.lastIndex) {
                        drawPath(linePath, lineColor, style = Stroke(1.5f, cap = StrokeCap.Round))
                    }
                }
            }
        }

        // ── Now line ──────────────────────────────────────────────────────────
        val nowX = xOf(nowFrac)
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(nowX, yPad),
            end   = Offset(nowX, h - yPad),
            strokeWidth = 1f,
        )

        // ── Y labels ─────────────────────────────────────────────────────────
        val androidLabelColor = AndroidColor.argb(
            (labelColor.alpha * 255).toInt(),
            (labelColor.red * 255).toInt(),
            (labelColor.green * 255).toInt(),
            (labelColor.blue * 255).toInt(),
        )
        val labelPaint = AndroidPaint().apply {
            textSize    = 9.dp.toPx()
            textAlign   = AndroidPaint.Align.RIGHT
            isAntiAlias = true
            color       = androidLabelColor
        }
        if (isEstimated) {
            drawContext.canvas.nativeCanvas.drawText("Peak",  xPadL - 4f, yOf(maxVel) + 4f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("Slack",  xPadL - 4f, zeroY + 4f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("Peak",  xPadL - 4f, yOf(-maxVel) + 4f, labelPaint)
        } else {
            val maxLabel = "${"%.1f".format(maxVel)}kt"
            drawContext.canvas.nativeCanvas.drawText(maxLabel,  xPadL - 4f, yOf(maxVel) + 4f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("0",        xPadL - 4f, zeroY + 4f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText("-${"%.1f".format(maxVel)}kt", xPadL - 4f, yOf(-maxVel) + 4f, labelPaint)
        }

        // ── X labels (6h) ─────────────────────────────────────────────────────
        val xLabelPaint = AndroidPaint().apply {
            textSize    = 9.dp.toPx()
            textAlign   = AndroidPaint.Align.CENTER
            isAntiAlias = true
            color       = androidLabelColor
        }
        listOf("12a" to 0f, "6a" to 0.25f, "12p" to 0.5f, "6p" to 0.75f).forEach { (label, frac) ->
            drawContext.canvas.nativeCanvas.drawText(label, xOf(frac), h - 2f, xLabelPaint)
        }
    }
}

// ─── Estimation helpers ────────────────────────────────────────────────────────

/**
 * Derives a normalized current-strength curve from the tide height derivative.
 * Positive = flooding (tide rising), negative = ebbing. Range [-1, +1].
 */
internal fun buildEstimatedCurve(
    predictedCurve: List<WaterLevelSample>,
): List<Pair<ZonedDateTime, Double>> {
    if (predictedCurve.size < 3) return emptyList()
    val raw = mutableListOf<Pair<ZonedDateTime, Double>>()
    for (i in 1 until predictedCurve.size - 1) {
        val dh = predictedCurve[i + 1].heightFt - predictedCurve[i - 1].heightFt
        raw.add(predictedCurve[i].time to dh)
    }
    val maxAbs = raw.maxOfOrNull { abs(it.second) }?.takeIf { it > 0.0 } ?: return emptyList()
    return raw.map { (t, v) -> t to (v / maxAbs).coerceIn(-1.0, 1.0) }
}

/** Maps a normalized velocity [-1,+1] to a human-readable strength label. */
fun estimatedStrengthLabel(normalizedVel: Double): String {
    val a = abs(normalizedVel)
    val dir = if (normalizedVel >= 0) "Flood" else "Ebb"
    return when {
        a < 0.10 -> "Slack"
        a < 0.35 -> "Light $dir"
        a < 0.65 -> "Moderate $dir"
        a < 0.85 -> "Strong $dir"
        else     -> "Peak $dir"
    }
}

private fun buildCurrentCurve(
    currents: List<TidalCurrent>,
    date: LocalDate,
    zone: ZoneId,
): List<Pair<ZonedDateTime, Double>> {
    if (currents.isEmpty()) return emptyList()
    val dayStart = date.atStartOfDay(zone)
    val dayEnd   = dayStart.plusDays(1)

    // Assign signed velocities: FLOOD = positive, EBB = negative, SLACK = 0
    val events = currents
        .sortedBy { it.time.toInstant() }
        .map { c ->
            val vel = when (c.state) {
                CurrentState.FLOOD -> +c.velocityKnots
                CurrentState.EBB   -> -c.velocityKnots
                CurrentState.SLACK -> 0.0
            }
            c.time to vel
        }

    val samples = mutableListOf<Pair<ZonedDateTime, Double>>()
    var t = dayStart
    while (!t.isAfter(dayEnd)) {
        val epochMs = t.toInstant().toEpochMilli()
        val before  = events.lastOrNull  { it.first.toInstant().toEpochMilli() <= epochMs }
        val after   = events.firstOrNull { it.first.toInstant().toEpochMilli() > epochMs }
        val vel = when {
            before == null -> after?.second ?: 0.0
            after  == null -> before.second
            else -> {
                val t0   = before.first.toInstant().toEpochMilli().toDouble()
                val t1   = after.first.toInstant().toEpochMilli().toDouble()
                val frac = ((epochMs - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
                val cosT = (1.0 - cos(PI * frac)) / 2.0
                before.second + (after.second - before.second) * cosT
            }
        }
        samples.add(t to vel)
        t = t.plusMinutes(10)
    }
    return samples
}

// ─── Current compass ───────────────────────────────────────────────────────────

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
