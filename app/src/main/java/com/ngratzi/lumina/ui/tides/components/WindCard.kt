package com.ngratzi.lumina.ui.tides.components

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.WindDailySummary
import com.ngratzi.lumina.data.model.WindForecast
import com.ngratzi.lumina.data.model.WindObservation
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.theme.beaufortColor
import com.ngratzi.lumina.util.TimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class WindRange { HOURLY, DAILY }

@Composable
fun WindCard(
    palette: SkyPalette,
    currentObservation: WindObservation?,
    forecast: WindForecast?,
    modifier: Modifier = Modifier,
) {
    val hasDailyData = (forecast?.daily?.isNotEmpty()) == true
    var range by remember { mutableStateOf(WindRange.HOURLY) }

    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(0.5.dp, palette.outlineColor, shape),
        color = palette.surfaceDim,
        shape = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("WIND", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                if (hasDailyData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WindRangeChip("24H",   range == WindRange.HOURLY, palette) { range = WindRange.HOURLY }
                        WindRangeChip("7 DAY", range == WindRange.DAILY,  palette) { range = WindRange.DAILY  }
                    }
                }
            }

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

            // ── Forecast chart ──────────────────────────────────────────────
            when {
                range == WindRange.HOURLY && forecast?.hourly?.isNotEmpty() == true -> {
                    val hourly = forecast.hourly
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))
                    // Legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        LegendItem(color = palette.accent, label = "Wind")
                        if (hourly.any { (it.gustKnots ?: 0.0) > it.speedKnots + 0.5 }) {
                            LegendItem(color = palette.accent.copy(alpha = 0.40f), label = "Gusts", dashed = true)
                        }
                    }
                    WindForecastChart(
                        palette      = palette,
                        observations = hourly,
                        modifier     = Modifier.fillMaxWidth().height(180.dp),
                    )
                }
                range == WindRange.DAILY && hasDailyData -> {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        LegendItem(color = palette.accent, label = "Max wind")
                    }
                    WindDailyChart(
                        palette   = palette,
                        summaries = forecast!!.daily,
                        modifier  = Modifier.fillMaxWidth().height(180.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WindRangeChip(
    label: String,
    selected: Boolean,
    palette: SkyPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick  = onClick,
        shape    = shape,
        color    = if (selected) palette.accent.copy(alpha = 0.15f) else Color.Transparent,
        border   = BorderStroke(0.5.dp, if (selected) palette.accent else palette.outlineColor),
        modifier = Modifier.height(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) palette.accent else palette.onSurfaceVariant,
            )
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

    val gridStep = when {
        maxSpeed > 40 -> 10.0
        else          ->  5.0
    }
    val gridLines = generateSequence(gridStep) { it + gridStep }
        .takeWhile { it < maxSpeed }
        .toList()

    // With 24 slots show every 3rd; with ≤8 show every other; ≤4 show all
    val showEvery = when {
        n > 16 -> 3
        n >  4 -> 2
        else   -> 1
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val yAxisW      = 38.dp.toPx()
        val arrowZoneH  = 52.dp.toPx()   // direction arrow + speed label
        val timeLabelH  = 20.dp.toPx()
        val slotPad     = 20.dp.toPx()   // inset first/last slot from y-axis and right edge
        val chartLeft   = yAxisW
        val chartTop    = arrowZoneH
        val chartBottom = h - timeLabelH
        val chartH      = chartBottom - chartTop
        val chartW      = w - chartLeft
        val plotW       = chartW - 2 * slotPad

        fun xOf(i: Int)      = chartLeft + slotPad + (if (n <= 1) plotW / 2f else i.toFloat() / (n - 1) * plotW)
        fun yOf(spd: Double) = chartBottom - (chartH * (spd / maxSpeed)).toFloat()

        val speedPts = List(n) { i -> Offset(xOf(i), yOf(observations[i].speedKnots)) }
        val gustPts  = List(n) { i ->
            Offset(xOf(i), yOf(observations[i].gustKnots ?: observations[i].speedKnots))
        }

        val labelTs = 11.dp.toPx()
        val yAxisPaint = AndroidPaint().apply {
            textSize    = labelTs
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.RIGHT
            color       = palette.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
        }
        val timePaint = AndroidPaint().apply {
            textSize    = labelTs
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
            color       = palette.onSurfaceVariant.copy(alpha = 0.75f).toArgb()
        }
        val speedPaint = AndroidPaint().apply {
            textSize    = 11.dp.toPx()
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
        }

        // Y-axis unit
        drawContext.canvas.nativeCanvas.drawText("kt", yAxisW - 4.dp.toPx(), chartTop - 6.dp.toPx(), yAxisPaint)

        // Grid lines + Y labels
        gridLines.forEach { spd ->
            val y = yOf(spd)
            if (y < chartTop || y > chartBottom) return@forEach
            drawLine(
                color       = palette.onSurfaceVariant.copy(alpha = 0.15f),
                start       = Offset(chartLeft, y),
                end         = Offset(w, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )
            drawContext.canvas.nativeCanvas.drawText(
                spd.toInt().toString(), yAxisW - 4.dp.toPx(), y - 3.dp.toPx(), yAxisPaint,
            )
        }

        // Gust band
        val hasGusts = observations.any { (it.gustKnots ?: 0.0) > it.speedKnots + 0.5 }
        if (hasGusts) {
            drawPath(
                path = Path().apply {
                    catmullRomTo(gustPts, moveFirst = true)
                    val rev = speedPts.reversed()
                    lineTo(rev.first().x, rev.first().y)
                    catmullRomTo(rev, moveFirst = false)
                    close()
                },
                color = palette.accent.copy(alpha = 0.13f),
            )
            drawPath(
                path  = Path().apply { catmullRomTo(gustPts, moveFirst = true) },
                color = palette.accent.copy(alpha = 0.45f),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
            )
        }

        // Speed area fill
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
                startY = chartTop, endY = chartBottom,
            ),
        )

        // Speed line
        drawPath(
            path  = Path().apply { catmullRomTo(speedPts, moveFirst = true) },
            color = palette.accent.copy(alpha = 0.90f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Per-slot: arrow + speed label + time label
        val arrowR  = 9.dp.toPx()
        val arrowCY = arrowR + 4.dp.toPx()
        observations.forEachIndexed { i, obs ->
            if (i % showEvery != 0) return@forEachIndexed
            val x      = xOf(i)
            val bColor = beaufortColor(obs.beaufortForce)

            // Direction arrow
            drawCircle(bColor.copy(alpha = 0.18f), arrowR + 3.dp.toPx(), Offset(x, arrowCY))
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

            // Speed label
            speedPaint.color = bColor.copy(alpha = 0.90f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                TimeFormatter.speedLabel(obs.speedKnots), x, arrowZoneH - 4.dp.toPx(), speedPaint,
            )

            // Time label
            val hour = obs.time.hour
            val label = when {
                hour == 0  -> "12a"
                hour < 12  -> "${hour}a"
                hour == 12 -> "12p"
                else       -> "${hour - 12}p"
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, h - 4.dp.toPx(), timePaint)
        }

    }
}

// ─── 7-day daily wind chart ───────────────────────────────────────────────────

@Composable
private fun WindDailyChart(
    palette: SkyPalette,
    summaries: List<WindDailySummary>,
    modifier: Modifier = Modifier,
) {
    if (summaries.isEmpty()) return
    val n       = summaries.size
    val today   = LocalDate.now()
    val dayFmt  = DateTimeFormatter.ofPattern("EEE")

    val maxSpeed = summaries.maxOf { it.maxSpeedKnots }.coerceAtLeast(10.0)
    val gridStep = when {
        maxSpeed > 30 -> 20.0
        maxSpeed > 15 -> 10.0
        else          ->  5.0
    }
    val gridLines = generateSequence(gridStep) { it + gridStep }
        .takeWhile { it < maxSpeed }
        .toList()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val yAxisW      = 38.dp.toPx()
        val arrowZoneH  = 52.dp.toPx()
        val timeLabelH  = 20.dp.toPx()
        val chartLeft   = yAxisW
        val chartTop    = arrowZoneH
        val chartBottom = h - timeLabelH
        val chartH      = chartBottom - chartTop
        val chartW      = w - chartLeft

        fun xOf(i: Int)      = chartLeft + (if (n <= 1) chartW / 2f else i.toFloat() / (n - 1) * chartW)
        fun yOf(spd: Double) = chartBottom - (chartH * (spd / maxSpeed)).toFloat()

        val speedPts = List(n) { i -> Offset(xOf(i), yOf(summaries[i].maxSpeedKnots)) }

        val labelTs = 11.dp.toPx()
        val yAxisPaint = AndroidPaint().apply {
            textSize    = labelTs
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.RIGHT
            color       = palette.onSurfaceVariant.copy(alpha = 0.65f).toArgb()
        }
        val timePaint = AndroidPaint().apply {
            textSize    = labelTs
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
            color       = palette.onSurfaceVariant.copy(alpha = 0.75f).toArgb()
        }
        val speedPaint = AndroidPaint().apply {
            textSize    = 11.dp.toPx()
            isAntiAlias = true
            textAlign   = AndroidPaint.Align.CENTER
        }

        // Y-axis unit
        drawContext.canvas.nativeCanvas.drawText("kt", yAxisW - 4.dp.toPx(), chartTop - 6.dp.toPx(), yAxisPaint)

        // Grid lines + Y labels
        gridLines.forEach { spd ->
            val y = yOf(spd)
            if (y < chartTop || y > chartBottom) return@forEach
            drawLine(
                color       = palette.onSurfaceVariant.copy(alpha = 0.15f),
                start       = Offset(chartLeft, y),
                end         = Offset(w, y),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
            )
            drawContext.canvas.nativeCanvas.drawText(
                spd.toInt().toString(), yAxisW - 4.dp.toPx(), y - 3.dp.toPx(), yAxisPaint,
            )
        }

        // Speed area fill
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
                startY = chartTop, endY = chartBottom,
            ),
        )

        // Speed line
        drawPath(
            path  = Path().apply { catmullRomTo(speedPts, moveFirst = true) },
            color = palette.accent.copy(alpha = 0.90f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val arrowR  = 9.dp.toPx()
        val arrowCY = arrowR + 4.dp.toPx()

        // Per-day: dot + arrow + speed label + day label
        summaries.forEachIndexed { i, summary ->
            val x      = xOf(i)
            val bForce = WindObservation(
                time         = java.time.ZonedDateTime.now(),
                speedKnots   = summary.maxSpeedKnots,
                gustKnots    = null,
                directionDeg = summary.directionDeg,
                source       = com.ngratzi.lumina.data.model.WindSource.OPEN_METEO,
            ).beaufortForce
            val bColor = beaufortColor(bForce)

            // Dot on line
            drawCircle(palette.accent, radius = 3.dp.toPx(), center = speedPts[i])

            // Direction arrow
            drawCircle(bColor.copy(alpha = 0.18f), arrowR + 3.dp.toPx(), Offset(x, arrowCY))
            rotate(summary.directionDeg.toFloat(), Offset(x, arrowCY)) {
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

            // Max speed label
            speedPaint.color = bColor.copy(alpha = 0.90f).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                TimeFormatter.speedLabel(summary.maxSpeedKnots), x, arrowZoneH - 4.dp.toPx(), speedPaint,
            )

            // Day label
            val dayLabel = when (summary.date) {
                today             -> "Today"
                today.plusDays(1) -> "Tmrw"
                else              -> summary.date.format(dayFmt)
            }
            drawContext.canvas.nativeCanvas.drawText(dayLabel, x, h - 4.dp.toPx(), timePaint)
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
