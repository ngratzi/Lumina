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
                WindForecastChart(
                    palette = palette,
                    observations = hourly.take(12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
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
    val gridSpeed = when {
        maxSpeed > 25 -> 20.0
        maxSpeed > 12 -> 10.0
        else          ->  5.0
    }
    val showEvery = if (n <= 6) 1 else 3   // how many hourly slots between displayed labels

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Zone heights (px)
        val arrowZoneH  = 38f   // direction arrow + speed label
        val timeLabelH  = 18f   // hour labels
        val chartTop    = arrowZoneH
        val chartBottom = h - timeLabelH
        val chartH      = chartBottom - chartTop

        // Coordinate helpers
        fun xOf(i: Int)         = if (n <= 1) w / 2f else i.toFloat() / (n - 1) * w
        fun yOf(spd: Double)    = chartBottom - (chartH * (spd / maxSpeed)).toFloat()

        val speedPts = List(n) { i -> Offset(xOf(i), yOf(observations[i].speedKnots)) }
        val gustPts  = List(n) { i ->
            Offset(xOf(i), yOf(observations[i].gustKnots ?: observations[i].speedKnots))
        }

        // ── Subtle grid reference line ──────────────────────────────────────
        val gridY = yOf(gridSpeed)
        if (gridY in chartTop..chartBottom) {
            drawLine(
                color       = palette.onSurfaceVariant.copy(alpha = 0.13f),
                start       = Offset(0f, gridY),
                end         = Offset(w, gridY),
                strokeWidth = 0.8f,
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${gridSpeed.toInt()} kt",
                6f, gridY - 5f,
                AndroidPaint().apply {
                    color       = palette.onSurfaceVariant.copy(alpha = 0.40f).toArgb()
                    textSize    = 20f
                    isAntiAlias = true
                },
            )
        }

        // ── Gust band (shaded area between gust and speed curves) ──────────
        val hasGusts = observations.any { (it.gustKnots ?: 0.0) > it.speedKnots + 0.5 }
        if (hasGusts) {
            val band = Path().apply {
                catmullRomTo(gustPts, moveFirst = true)
                val rev = speedPts.reversed()
                lineTo(rev.first().x, rev.first().y)
                catmullRomTo(rev, moveFirst = false)
                close()
            }
            drawPath(band, palette.accent.copy(alpha = 0.11f))
        }

        // ── Speed area fill (gradient) ──────────────────────────────────────
        val areaPath = Path().apply {
            moveTo(speedPts.first().x, chartBottom)
            lineTo(speedPts.first().x, speedPts.first().y)
            catmullRomTo(speedPts, moveFirst = false)
            lineTo(speedPts.last().x, chartBottom)
            close()
        }
        drawPath(
            path  = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(palette.accent.copy(alpha = 0.20f), Color.Transparent),
                startY = chartTop,
                endY   = chartBottom,
            ),
        )

        // ── Speed line ──────────────────────────────────────────────────────
        drawPath(
            path  = Path().apply { catmullRomTo(speedPts, moveFirst = true) },
            color = palette.accent.copy(alpha = 0.85f),
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // ── Per-slot: direction arrow + speed label + time label ───────────
        val arrowPaint = AndroidPaint().apply {
            textSize    = 19f
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
            typeface    = android.graphics.Typeface.DEFAULT
        }
        val timePaint = AndroidPaint().apply {
            textSize    = 21f
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
        }

        observations.forEachIndexed { i, obs ->
            if (i % showEvery != 0) return@forEachIndexed
            val x      = xOf(i)
            val bColor = beaufortColor(obs.beaufortForce)

            // ─ Direction arrow ─────────────────────────────────────────────
            val arrowCY = 14f
            val arrowR  = 8f
            drawCircle(bColor.copy(alpha = 0.15f), arrowR + 3f, Offset(x, arrowCY))
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

            // ─ Speed label ─────────────────────────────────────────────────
            arrowPaint.color = bColor.copy(alpha = 0.85f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                TimeFormatter.speedLabel(obs.speedKnots),
                x, arrowZoneH - 3f,
                arrowPaint,
            )

            // ─ Time label ──────────────────────────────────────────────────
            val hour  = obs.time.hour
            val label = when {
                hour == 0  -> "12a"
                hour < 12  -> "${hour}a"
                hour == 12 -> "12p"
                else       -> "${hour - 12}p"
            }
            timePaint.color = palette.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, x, h - 3f, timePaint)
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
