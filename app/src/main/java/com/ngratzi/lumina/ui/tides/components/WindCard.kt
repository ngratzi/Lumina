package com.ngratzi.lumina.ui.tides.components

import android.graphics.Paint as AndroidPaint
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.WindForecast
import com.ngratzi.lumina.data.model.WindObservation
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.theme.beaufortColor
import com.ngratzi.lumina.util.TimeFormatter

@Composable
fun WindCard(
    palette: SkyPalette,
    currentObservation: WindObservation?,
    forecast: WindForecast?,
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
            Text("WIND", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)

            Spacer(Modifier.height(12.dp))

            // ── Current conditions ──────────────────────────────────────────
            currentObservation?.let { obs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = TimeFormatter.speedLabel(obs.speedKnots),
                                style = MaterialTheme.typography.headlineSmall,
                                color = palette.onSurface,
                            )
                            Spacer(Modifier.width(4.dp))
                            obs.gustKnots?.let { gust ->
                                Text(
                                    text = "G${TimeFormatter.speedLabel(gust)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "${obs.beaufortLabel} · ${obs.compassDirection}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurfaceVariant,
                        )
                    }
                    WindDirectionArrow(
                        directionDeg = obs.directionDeg,
                        color = beaufortColor(obs.beaufortForce),
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Beaufort ${obs.beaufortForce} · ${obs.source.name.replace("_", " ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                )
            } ?: Text(
                "No current observation",
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )

            // ── 12-hour forecast ────────────────────────────────────────────
            forecast?.hourly?.takeIf { it.isNotEmpty() }?.let { hourly ->
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "12-HOUR FORECAST",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    LegendItem(color = palette.accent, label = "Wind")
                    if (hourly.take(12).any { (it.gustKnots ?: 0.0) > it.speedKnots + 0.5 }) {
                        LegendItem(color = palette.accent.copy(alpha = 0.40f), label = "Gusts", dashed = true)
                    }
                }

                WindForecastChart(
                    palette = palette,
                    observations = hourly.take(12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }
    }
}

// ─── Direction rose (current conditions) ──────────────────────────────────────

@Composable
private fun WindDirectionArrow(
    directionDeg: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r  = size.minDimension / 2 - 4f

        drawCircle(color.copy(alpha = 0.15f), radius = r, center = Offset(cx, cy))
        drawCircle(color.copy(alpha = 0.40f), radius = r, style = Stroke(1f))

        rotate(degrees = directionDeg.toFloat(), pivot = Offset(cx, cy)) {
            drawPath(
                path = Path().apply {
                    moveTo(cx, cy - r * 0.70f)
                    lineTo(cx - r * 0.25f, cy + r * 0.30f)
                    lineTo(cx, cy + r * 0.10f)
                    lineTo(cx + r * 0.25f, cy + r * 0.30f)
                    close()
                },
                color = color,
            )
        }
    }
}

// ─── Legend item ──────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 2.dp)) {
            drawLine(
                color       = color,
                start       = Offset(0f, size.height / 2),
                end         = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx(),
                pathEffect  = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ─── Forecast chart ────────────────────────────────────────────────────────────

@Composable
private fun WindForecastChart(
    palette: SkyPalette,
    observations: List<WindObservation>,
    modifier: Modifier = Modifier,
) {
    if (observations.isEmpty()) return
    val n = observations.size
    val maxSpeed = observations
        .maxOf { maxOf(it.speedKnots, it.gustKnots ?: 0.0) }
        .coerceAtLeast(10.0)

    // Choose round grid step: 5, 10, or 20 kt
    val gridStep = when {
        maxSpeed > 30 -> 20.0
        maxSpeed > 15 -> 10.0
        else          ->  5.0
    }
    // Grid lines at each step up to maxSpeed
    val gridLines = generateSequence(gridStep) { it + gridStep }
        .takeWhile { it < maxSpeed }
        .toList()

    val showEvery = if (n <= 6) 1 else 2

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Layout zones
        val yAxisW     = 36f   // left margin for Y-axis labels
        val arrowZoneH = 40f   // top: direction arrows + speed labels
        val timeLabelH = 22f   // bottom: hour labels
        val chartLeft  = yAxisW
        val chartTop   = arrowZoneH
        val chartBottom = h - timeLabelH
        val chartH     = chartBottom - chartTop
        val chartW     = w - chartLeft

        fun xOf(i: Int)      = chartLeft + (if (n <= 1) chartW / 2f else i.toFloat() / (n - 1) * chartW)
        fun yOf(spd: Double) = chartBottom - (chartH * (spd / maxSpeed)).toFloat()

        val speedPts = List(n) { i -> Offset(xOf(i), yOf(observations[i].speedKnots)) }
        val gustPts  = List(n) { i ->
            Offset(xOf(i), yOf(observations[i].gustKnots ?: observations[i].speedKnots))
        }

        val yAxisPaint = AndroidPaint().apply {
            textSize    = 30f
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.RIGHT
            color       = palette.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
        }
        val timePaint = AndroidPaint().apply {
            textSize    = 30f
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
            color       = palette.onSurfaceVariant.copy(alpha = 0.75f).toArgb()
        }
        val speedPaint = AndroidPaint().apply {
            textSize    = 28f
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
        }

        // ── Y-axis label ("kt") at top-left ────────────────────────────────
        // Y-axis unit label
        drawContext.canvas.nativeCanvas.drawText(
            "kt",
            yAxisW - 4f,
            chartTop - 6f,
            yAxisPaint,
        )

        // ── Grid lines + Y-axis labels ──────────────────────────────────────
        gridLines.forEach { spd ->
            val y = yOf(spd)
            if (y < chartTop || y > chartBottom) return@forEach
            drawLine(
                color       = palette.onSurfaceVariant.copy(alpha = 0.15f),
                start       = Offset(chartLeft, y),
                end         = Offset(w, y),
                strokeWidth = 0.8f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )
            drawContext.canvas.nativeCanvas.drawText(
                spd.toInt().toString(),
                yAxisW - 4f,
                y - 5f,
                yAxisPaint,
            )
        }

        // ── Gust band ───────────────────────────────────────────────────────
        val hasGusts = observations.any { (it.gustKnots ?: 0.0) > it.speedKnots + 0.5 }
        if (hasGusts) {
            val band = Path().apply {
                catmullRomTo(gustPts, moveFirst = true)
                val rev = speedPts.reversed()
                lineTo(rev.first().x, rev.first().y)
                catmullRomTo(rev, moveFirst = false)
                close()
            }
            drawPath(band, palette.accent.copy(alpha = 0.13f))
            // Dashed gust top line
            drawPath(
                path  = Path().apply { catmullRomTo(gustPts, moveFirst = true) },
                color = palette.accent.copy(alpha = 0.45f),
                style = Stroke(
                    width     = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                ),
            )
        }

        // ── Speed area fill ─────────────────────────────────────────────────
        drawPath(
            path = Path().apply {
                moveTo(speedPts.first().x, chartBottom)
                lineTo(speedPts.first().x, speedPts.first().y)
                catmullRomTo(speedPts, moveFirst = false)
                lineTo(speedPts.last().x, chartBottom)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(palette.accent.copy(alpha = 0.22f), Color.Transparent),
                startY = chartTop,
                endY   = chartBottom,
            ),
        )

        // ── Speed line ───────────────────────────────────────────────────────
        drawPath(
            path  = Path().apply { catmullRomTo(speedPts, moveFirst = true) },
            color = palette.accent.copy(alpha = 0.90f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── Per-slot: arrow + speed + time ──────────────────────────────────
        observations.forEachIndexed { i, obs ->
            if (i % showEvery != 0) return@forEachIndexed
            val x      = xOf(i)
            val bColor = beaufortColor(obs.beaufortForce)

            // Direction arrow
            val arrowCY = 14f
            val arrowR  = 9f
            drawCircle(bColor.copy(alpha = 0.18f), arrowR + 3f, Offset(x, arrowCY))
            rotate(obs.directionDeg.toFloat(), Offset(x, arrowCY)) {
                drawPath(
                    path = Path().apply {
                        moveTo(x, arrowCY - arrowR)
                        lineTo(x - arrowR * 0.42f, arrowCY + arrowR * 0.52f)
                        lineTo(x, arrowCY + arrowR * 0.18f)
                        lineTo(x + arrowR * 0.42f, arrowCY + arrowR * 0.52f)
                        close()
                    },
                    color = bColor,
                )
            }

            // Speed label below arrow
            speedPaint.color = bColor.copy(alpha = 0.90f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                TimeFormatter.speedLabel(obs.speedKnots),
                x, arrowZoneH - 4f,
                speedPaint,
            )

            // Time label
            val hour = obs.time.hour
            val label = when {
                hour == 0  -> "12a"
                hour < 12  -> "${hour}a"
                hour == 12 -> "12p"
                else       -> "${hour - 12}p"
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, h - 4f, timePaint)
        }
    }
}

// ─── Catmull-Rom helper ────────────────────────────────────────────────────────

/**
 * Appends a Catmull-Rom spline through [points] to this Path.
 * [moveFirst] = true → moveTo the first point (starts a new contour).
 * [moveFirst] = false → lineTo the first point (continues an existing contour).
 */
private fun Path.catmullRomTo(points: List<Offset>, moveFirst: Boolean) {
    if (points.isEmpty()) return
    if (moveFirst) moveTo(points.first().x, points.first().y)
    else           lineTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f,  p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,  p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}
