package com.ngratzi.lumina.ui.tides.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

// ─── 3-day scrollable tide chart ──────────────────────────────────────────────

@Composable
fun TideChart(
    palette: SkyPalette,
    predictedSamples: List<WaterLevelSample>,
    verifiedSamples: List<WaterLevelSample>?,
    tideEvents: List<TideEvent>,          // all available events (multi-day)
    currentTime: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    if (predictedSamples.isEmpty()) return

    val infiniteTransition = rememberInfiniteTransition(label = "tide_now")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 5f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    val sourceSamples = if (verifiedSamples?.isNotEmpty() == true) verifiedSamples else predictedSamples
    val currentHeightFt = remember(sourceSamples, currentTime) { interpolateHeight(sourceSamples, currentTime) }
    val isRising        = remember(predictedSamples, currentTime) { isTideRising(predictedSamples, currentTime) }

    val zone          = currentTime.zone
    val today         = currentTime.toLocalDate()
    val chartStartMs  = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val chartEndMs    = today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
    val chartDuration = (chartEndMs - chartStartMs).toFloat()

    // Synthetic samples for days 2 and 3 (cosine-interpolated from H/L events)
    val futureSamples = remember(tideEvents) {
        val predEnd = predictedSamples.lastOrNull()?.time?.toInstant()?.toEpochMilli() ?: chartStartMs
        generateSyntheticSamples(tideEvents, predEnd, chartEndMs, zone)
    }

    // Y range: combine today + future heights for a stable scale
    val allHeights = (predictedSamples.map { it.heightFt } + futureSamples.map { it.heightFt })
    val minH  = (allHeights.minOrNull() ?: 0.0) - 0.5
    val maxH  = (allHeights.maxOrNull() ?: 10.0) + 0.5

    // Upcoming events (next 8, including any that just passed by 30 min)
    val upcomingEvents = remember(tideEvents, currentTime) {
        tideEvents
            .filter { it.time.isAfter(currentTime.minusMinutes(30)) }
            .sortedBy { it.time }
            .take(8)
    }

    Column(modifier = modifier) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("WATER LEVEL", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            if (currentHeightFt != null) {
                Text(
                    text  = "${TimeFormatter.heightLabel(currentHeightFt)} ${if (isRising) "↑" else "↓"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
            }
        }

        // ── Scrollable chart ─────────────────────────────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val viewportPx   = constraints.maxWidth.toFloat()
            val chartWidthDp = maxWidth * 3   // 3 days = 3× screen width
            val scrollState  = rememberScrollState()

            LaunchedEffect(chartStartMs) {
                val nowFrac  = ((currentTime.toInstant().toEpochMilli() - chartStartMs) / chartDuration).coerceIn(0f, 1f)
                val nowPx    = (nowFrac * viewportPx * 3).toInt()
                val target   = (nowPx - (viewportPx * 0.25f).toInt()).coerceAtLeast(0)
                scrollState.scrollTo(target)
            }

            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                Canvas(
                    modifier = Modifier
                        .width(chartWidthDp)
                        .height(200.dp),
                ) {
                    val w      = size.width
                    val h      = size.height
                    val padL   = 8f
                    val padR   = 8f
                    val padTop = 24f
                    val padBot = 22f
                    val hRange = maxH - minH

                    fun xOf(ms: Long)    = padL + (w - padL - padR) * ((ms - chartStartMs) / chartDuration)
                    fun yOf(ft: Double)  = h - padBot - ((h - padTop - padBot) * ((ft - minH) / hRange)).toFloat()

                    val dayLabelPaint = AndroidPaint().apply {
                        textSize    = 24f
                        isAntiAlias = true
                        typeface    = Typeface.DEFAULT_BOLD
                    }
                    val eventLabelPaint = AndroidPaint().apply {
                        textSize    = 22f
                        isAntiAlias = true
                        textAlign   = AndroidPaint.Align.CENTER
                        typeface    = Typeface.DEFAULT_BOLD
                    }

                    // ── Day separator lines + labels ─────────────────────────
                    for (d in 0..2) {
                        val dayMs  = today.plusDays(d.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                        val dx     = xOf(dayMs)
                        drawLine(
                            color       = palette.onSurfaceVariant.copy(alpha = 0.18f),
                            start       = Offset(dx, padTop),
                            end         = Offset(dx, h - padBot),
                            strokeWidth = 0.8f,
                        )
                        val label = when (d) {
                            0    -> "Today"
                            else -> today.plusDays(d.toLong()).dayOfWeek.name
                                .lowercase().replaceFirstChar { it.uppercase() }.take(3)
                        }
                        dayLabelPaint.color = palette.onSurfaceVariant.copy(alpha = 0.55f).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(label, dx + 10f, padTop - 5f, dayLabelPaint)
                    }

                    // ── Future synthetic curve (lighter) ─────────────────────
                    if (futureSamples.size >= 2) {
                        drawTideCurve(
                            samples     = futureSamples,
                            xOf         = { xOf(it.time.toInstant().toEpochMilli()) },
                            yOf         = { yOf(it.heightFt) },
                            color       = palette.accent.copy(alpha = 0.35f),
                            fillColor   = palette.accent.copy(alpha = 0.05f),
                            bottomY     = yOf(minH),
                            strokeWidth = 1.5f,
                        )
                    }

                    // ── Predicted today (solid) ───────────────────────────────
                    drawTideCurve(
                        samples     = predictedSamples,
                        xOf         = { xOf(it.time.toInstant().toEpochMilli()) },
                        yOf         = { yOf(it.heightFt) },
                        color       = palette.accent.copy(alpha = 0.75f),
                        fillColor   = palette.accent.copy(alpha = 0.10f),
                        bottomY     = yOf(minH),
                        strokeWidth = 2.2f,
                    )

                    // ── Verified overlay ─────────────────────────────────────
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

                    // ── H/L event dots + height labels ───────────────────────
                    tideEvents.forEach { event ->
                        val ex    = xOf(event.time.toInstant().toEpochMilli())
                        val ey    = yOf(event.heightFt)
                        val isPast = event.time.isBefore(currentTime)
                        val dotColor = if (event.type == TideType.HIGH)
                            palette.accent.copy(alpha = if (isPast) 0.35f else 1f)
                        else
                            palette.onSurfaceVariant.copy(alpha = if (isPast) 0.30f else 0.80f)

                        drawCircle(color = dotColor, radius = 4.5f, center = Offset(ex, ey))

                        val labelY = if (event.type == TideType.HIGH) ey - 10f else ey + 22f
                        eventLabelPaint.color = dotColor.copy(alpha = (dotColor.alpha * 1.1f).coerceAtMost(1f)).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            TimeFormatter.heightLabel(event.heightFt), ex, labelY, eventLabelPaint,
                        )
                    }

                    // ── Zero line ────────────────────────────────────────────
                    if (minH < 0) {
                        drawLine(
                            color       = palette.onSurfaceVariant.copy(alpha = 0.25f),
                            start       = Offset(padL, yOf(0.0)),
                            end         = Offset(w - padR, yOf(0.0)),
                            strokeWidth = 0.5f,
                        )
                    }

                    // ── Current time + now dot ────────────────────────────────
                    val nowEpoch = currentTime.toInstant().toEpochMilli()
                    if (nowEpoch in chartStartMs..chartEndMs) {
                        val nowX = xOf(nowEpoch)
                        drawLine(
                            color       = Color.White.copy(alpha = 0.55f),
                            start       = Offset(nowX, padTop),
                            end         = Offset(nowX, h - padBot),
                            strokeWidth = 1f,
                            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "NOW", nowX, padTop - 5f,
                            AndroidPaint().apply {
                                color       = Color.White.copy(alpha = 0.75f).toArgb()
                                textSize    = 20f
                                textAlign   = AndroidPaint.Align.CENTER
                                isAntiAlias = true
                                typeface    = Typeface.DEFAULT_BOLD
                            },
                        )
                        if (currentHeightFt != null) {
                            val dotY = yOf(currentHeightFt)
                            drawLine(
                                color       = palette.accent.copy(alpha = 0.18f),
                                start       = Offset(padL, dotY),
                                end         = Offset(w - padR, dotY),
                                strokeWidth = 0.8f,
                                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
                            )
                            drawCircle(Color.White.copy(alpha = pulseAlpha), pulseRadius, Offset(nowX, dotY))
                            drawCircle(Color.White.copy(alpha = 0.50f), 6f, Offset(nowX, dotY), style = Stroke(1.5f))
                            drawCircle(Color.White, 4f, Offset(nowX, dotY))
                        }
                    }
                }
            }
        }

        // ── Upcoming tide cycles ──────────────────────────────────────────────
        if (upcomingEvents.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "UPCOMING",
                style    = MaterialTheme.typography.labelSmall,
                color    = palette.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            Row(
                modifier            = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                upcomingEvents.forEachIndexed { i, event ->
                    TideCycleEventChip(palette, event)
                    if (i < upcomingEvents.lastIndex) {
                        val next     = upcomingEvents[i + 1]
                        val durMins  = Duration.between(event.time, next.time).toMinutes()
                        val incoming = next.type == TideType.HIGH
                        TideCycleConnector(palette, incoming, durMins)
                    }
                }
            }
        }

        // ── Legend (verified overlay) ─────────────────────────────────────────
        if (verifiedSamples?.isNotEmpty() == true) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendDot(palette.accent,       "Predicted", palette)
                LegendDot(Color(0xFFE8A020), "Observed",  palette)
            }
        }
    }
}

// ─── Cycle list sub-components ────────────────────────────────────────────────

@Composable
private fun TideCycleEventChip(palette: SkyPalette, event: TideEvent) {
    val isHigh = event.type == TideType.HIGH
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isHigh) palette.accent.copy(alpha = 0.20f) else palette.outlineColor.copy(alpha = 0.50f),
        ) {
            Text(
                text     = if (isHigh) "H" else "L",
                style    = MaterialTheme.typography.labelSmall,
                color    = if (isHigh) palette.accent else palette.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
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

@Composable
private fun TideCycleConnector(palette: SkyPalette, incoming: Boolean, durationMins: Long) {
    val h = durationMins / 60
    val m = durationMins % 60
    val durLabel = if (h > 0) "${h}h ${m}m" else "${m}m"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text  = if (incoming) "↑" else "↓",
            style = MaterialTheme.typography.bodyMedium,
            color = if (incoming) palette.accent else palette.onSurfaceVariant,
        )
        Text(
            text  = durLabel,
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant.copy(alpha = 0.75f),
        )
        Text(
            text  = if (incoming) "incoming" else "outgoing",
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant.copy(alpha = 0.50f),
        )
    }
}

// ─── Synthetic sample generation ─────────────────────────────────────────────

/**
 * Cosine-interpolates water level between H/L events for any time range beyond
 * the accurate predicted samples. Produces samples at 30-min intervals.
 */
private fun generateSyntheticSamples(
    events: List<TideEvent>,
    fromMs: Long,
    toMs: Long,
    zone: ZoneId,
): List<WaterLevelSample> {
    val sorted = events.sortedBy { it.time.toInstant().toEpochMilli() }
    if (sorted.size < 2) return emptyList()
    val result = mutableListOf<WaterLevelSample>()

    for (i in 0 until sorted.size - 1) {
        val a  = sorted[i]
        val b  = sorted[i + 1]
        val t0 = a.time.toInstant().toEpochMilli()
        val t1 = b.time.toInstant().toEpochMilli()
        // Start sampling from the later of (segment start, fromMs)
        var t  = maxOf(t0, fromMs + 30 * 60_000L)
        while (t <= t1 && t <= toMs) {
            val frac  = ((t - t0).toDouble() / (t1 - t0)).coerceIn(0.0, 1.0)
            val cosT  = (1.0 - cos(PI * frac)) / 2.0
            val h     = a.heightFt + (b.heightFt - a.heightFt) * cosT
            result.add(
                WaterLevelSample(
                    time       = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone),
                    heightFt   = h,
                    isVerified = false,
                )
            )
            t += 30 * 60_000L
        }
    }
    return result
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
        drawPath(
            Path().apply {
                addPath(path)
                lineTo(points.last().x, bottomY)
                lineTo(points.first().x, bottomY)
                close()
            },
            fillColor,
        )
    }
    drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

private fun interpolateHeight(samples: List<WaterLevelSample>, now: ZonedDateTime): Double? {
    val epoch  = now.toInstant().toEpochMilli()
    val before = samples.lastOrNull  { it.time.toInstant().toEpochMilli() <= epoch } ?: return null
    val after  = samples.firstOrNull { it.time.toInstant().toEpochMilli() >  epoch } ?: return before.heightFt
    val t0     = before.time.toInstant().toEpochMilli().toDouble()
    val t1     = after.time.toInstant().toEpochMilli().toDouble()
    val frac   = ((epoch - t0) / (t1 - t0)).coerceIn(0.0, 1.0)
    return before.heightFt + (after.heightFt - before.heightFt) * frac
}

private fun isTideRising(samples: List<WaterLevelSample>, now: ZonedDateTime): Boolean {
    val epoch  = now.toInstant().toEpochMilli()
    val before = samples.lastOrNull  { it.time.toInstant().toEpochMilli() <= epoch } ?: return true
    val after  = samples.firstOrNull { it.time.toInstant().toEpochMilli() >  epoch } ?: return false
    return after.heightFt > before.heightFt
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
