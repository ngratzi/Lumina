package com.ngratzi.lumina.ui.home.components

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.*

@Composable
fun SkyDial(
    palette: SkyPalette,
    skyState: SkyState,
    currentTime: ZonedDateTime,
    locationName: String,
    sunTimes: SunTimes?,
    moonData: MoonData?,
    modifier: Modifier = Modifier,
) {
    val midnight   = currentTime.toLocalDate().atStartOfDay(currentTime.zone)
    val midnightMs = midnight.toInstant().toEpochMilli()
    val dayMs      = 24L * 60 * 60 * 1000
    val nowEpoch   = currentTime.toInstant().toEpochMilli()

    val (sunAlpha, moonAlpha) = dialCelestialAlphas(skyState.phase)
    val segments = if (sunTimes != null) buildDialSegments(sunTimes, midnightMs, dayMs) else emptyList()

    // Compute next event time string
    val nextEventText = if (skyState.nextEventName.isNotBlank() && skyState.minutesUntilNextEvent > 0) {
        val at = currentTime.plusMinutes(skyState.minutesUntilNextEvent)
        "${skyState.nextEventName} in ${dialFormatMins(skyState.minutesUntilNextEvent)} · ${TimeFormatter.formatTime(at)}"
    } else null

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "dial")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "stars",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(460.dp)
            .background(
                Brush.verticalGradient(
                    listOf(palette.gradientTop, palette.gradientMid, palette.gradientBottom)
                )
            ),
    ) {
        // ── Canvas: stars, ring, celestial bodies ─────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w  = size.width
            val h  = size.height
            val cx = w / 2f
            val cy = h / 2f

            val ringRadius = w * 0.38f
            val ringStroke = ringRadius * 0.26f
            val innerR     = ringRadius - ringStroke / 2f
            val outerR     = ringRadius + ringStroke / 2f

            // Stars (night/twilight)
            if (moonAlpha > 0.3f) dialDrawStars(w, h, moonAlpha * starAlpha)

            // ── Ring segments ─────────────────────────────────────────────────
            segments.forEach { seg ->
                val startFrac  = ((seg.startEpoch - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val endFrac    = ((seg.endEpoch   - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val startAngle = -90f + startFrac * 360f
                val sweepAngle = (endFrac - startFrac) * 360f
                if (sweepAngle > 0.01f) {
                    drawArc(
                        color      = seg.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter  = false,
                        topLeft    = Offset(cx - ringRadius, cy - ringRadius),
                        size       = Size(ringRadius * 2f, ringRadius * 2f),
                        style      = Stroke(ringStroke, cap = StrokeCap.Butt),
                    )
                }
            }

            // Outer ring edge lines
            drawCircle(Color.White.copy(alpha = 0.18f), innerR, Offset(cx, cy), style = Stroke(1f))
            drawCircle(Color.White.copy(alpha = 0.18f), outerR, Offset(cx, cy), style = Stroke(1f))

            // ── Inner moon ring ───────────────────────────────────────────────
            val moonRingRadius = ringRadius * 0.74f
            val moonRingStroke = ringStroke * 0.48f
            val moonInnerR     = moonRingRadius - moonRingStroke / 2f
            val moonOuterR     = moonRingRadius + moonRingStroke / 2f

            val moonriseMs = moonData?.moonrise?.toInstant()?.toEpochMilli()
            val moonsetMs  = moonData?.moonset?.toInstant()?.toEpochMilli()

            // Base: dim ring for the full 360°
            drawCircle(
                color  = Color(0xFF121B30),
                radius = moonRingRadius,
                center = Offset(cx, cy),
                style  = Stroke(moonRingStroke, cap = StrokeCap.Butt),
            )

            // Lit arc: moon above horizon (moonrise → moonset clipped to today)
            if (moonriseMs != null) {
                val dayEnd       = midnightMs + dayMs
                // Clamp to today's window — handles "already up at midnight" and "still up tomorrow"
                val clampedRise  = moonriseMs.coerceIn(midnightMs, dayEnd)
                val clampedSet   = (moonsetMs ?: dayEnd).coerceIn(midnightMs, dayEnd)
                val riseFrac     = ((clampedRise - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val setFrac      = ((clampedSet  - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val sweepAngle   = (setFrac - riseFrac) * 360f
                if (sweepAngle > 0.1f) {
                    // Illumination-tinted: brighter = more lit moon
                    val illum      = ((moonData?.illumination ?: 0.5) * 0.5 + 0.3).toFloat()
                    val litColor   = Color(0xFFBBCCDD).copy(alpha = illum)
                    drawArc(
                        color      = litColor,
                        startAngle = -90f + riseFrac * 360f,
                        sweepAngle = sweepAngle,
                        useCenter  = false,
                        topLeft    = Offset(cx - moonRingRadius, cy - moonRingRadius),
                        size       = Size(moonRingRadius * 2f, moonRingRadius * 2f),
                        style      = Stroke(moonRingStroke, cap = StrokeCap.Butt),
                    )
                }
            }

            // Moon ring edge lines
            drawCircle(Color.White.copy(alpha = 0.10f), moonInnerR, Offset(cx, cy), style = Stroke(0.8f))
            drawCircle(Color.White.copy(alpha = 0.10f), moonOuterR, Offset(cx, cy), style = Stroke(0.8f))

            // ── Hour tick marks (every hour except the 4 labelled quarter marks) ─
            for (hour in 0..23) {
                if (hour % 6 == 0) continue  // skip 12a, 6a, 12p, 6p — they get full ticks
                val frac     = hour / 24f
                val angleRad = Math.toRadians((-90.0 + frac * 360.0))
                val cosA     = cos(angleRad).toFloat()
                val sinA     = sin(angleRad).toFloat()
                drawLine(
                    color       = Color.White.copy(alpha = 0.55f),
                    start       = Offset(cx + (outerR + 3f) * cosA, cy + (outerR + 3f) * sinA),
                    end         = Offset(cx + (outerR + 16f) * cosA, cy + (outerR + 16f) * sinA),
                    strokeWidth = 2f,
                )
            }

            // ── Tick marks + labels spanning both rings ───────────────────────
            val labelPaint = AndroidPaint().apply {
                textSize    = 10.dp.toPx()
                textAlign   = AndroidPaint.Align.CENTER
                isAntiAlias = true
                typeface    = Typeface.DEFAULT
                color       = Color.White.copy(alpha = 0.55f).toArgb()
            }
            listOf("12a" to 0f, "6a" to 0.25f, "12p" to 0.5f, "6p" to 0.75f).forEach { (label, frac) ->
                val angleRad = Math.toRadians((-90.0 + frac * 360.0))
                val cosA     = cos(angleRad).toFloat()
                val sinA     = sin(angleRad).toFloat()
                // Tick spans from inside moon ring to outside sun ring
                drawLine(
                    color       = Color.White.copy(alpha = 0.35f),
                    start       = Offset(cx + (moonInnerR - 4f) * cosA, cy + (moonInnerR - 4f) * sinA),
                    end         = Offset(cx + (outerR + 4f) * cosA, cy + (outerR + 4f) * sinA),
                    strokeWidth = 1.5f,
                )
                // Label inside the moon ring
                val labelR = moonInnerR - 13.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    cx + labelR * cosA,
                    cy + labelR * sinA + 4.dp.toPx(),
                    labelPaint,
                )
            }

            // ── Sun on outer ring ─────────────────────────────────────────────
            val nowFrac     = ((nowEpoch - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
            val nowAngleRad = Math.toRadians((-90.0 + nowFrac * 360.0))
            val sunX        = cx + ringRadius * cos(nowAngleRad).toFloat()
            val sunY        = cy + ringRadius * sin(nowAngleRad).toFloat()

            if (sunAlpha > 0f) {
                dialDrawSun(sunX, sunY, palette.sunColor, sunAlpha, glowRadius)
            }

            // ── Moon on inner ring (always at current time; dimmed when below horizon) ──
            if (moonData != null) {
                val moonIsUp  = moonriseMs != null &&
                                nowEpoch >= moonriseMs &&
                                (moonsetMs == null || nowEpoch <= moonsetMs)
                val moonAlpha2 = if (moonIsUp) 0.92f else 0.32f
                val moonFrac  = ((nowEpoch - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val moonAngle = Math.toRadians((-90.0 + moonFrac * 360.0))
                val mx = cx + moonRingRadius * cos(moonAngle).toFloat()
                val my = cy + moonRingRadius * sin(moonAngle).toFloat()
                dialDrawMoon(mx, my, moonData.phase, moonAlpha2, glowRadius * 0.65f)
            }
        }

        // ── Location name ─────────────────────────────────────────────────────
        Text(
            text     = locationName.ifBlank { "Locating…" },
            style    = MaterialTheme.typography.labelMedium,
            color    = palette.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp),
        )

        // ── Center: time + date only (stays inside the inner ring) ──────────
        Column(
            modifier            = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = TimeFormatter.formatTime(currentTime),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light),
                color = palette.onSurface,
            )
            Text(
                text     = TimeFormatter.formatDate(currentTime),
                style    = MaterialTheme.typography.labelMedium,
                color    = palette.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ── Bottom: phase label + event countdown + moon phase ───────────────
        Column(
            modifier            = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = skyState.phase.displayName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.accent,
            )
            nextEventText?.let { txt ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = txt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            }
            if (moonData != null && moonAlpha > 0.5f) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "${moonData.phaseEmoji} ${moonData.phaseName}  ${(moonData.illumination * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant.copy(alpha = moonAlpha.coerceAtMost(0.9f)),
                )
            }
        }
    }
}

// ─── Segment data class ───────────────────────────────────────────────────────

private data class DialSegment(val startEpoch: Long, val endEpoch: Long, val color: Color)

private fun buildDialSegments(
    sunTimes: SunTimes,
    midnightEpoch: Long,
    dayMs: Long,
): List<DialSegment> {
    val endOfDay = midnightEpoch + dayMs
    fun e(zdt: ZonedDateTime?) = zdt?.toInstant()?.toEpochMilli()

    val astroDawn = e(sunTimes.astronomicalDawn)
    val nautDawn  = e(sunTimes.nauticalDawn)
    val blueStart = e(sunTimes.blueHourStart)
    val sunrise   = e(sunTimes.sunrise)
    val ghEnd     = e(sunTimes.goldenHourEnd)
    val ghStart   = e(sunTimes.goldenHourStart)
    val sunset    = e(sunTimes.sunset)
    val blueEnd   = e(sunTimes.blueHourEnd)
    val nautDusk  = e(sunTimes.nauticalDusk)
    val astroDusk = e(sunTimes.astronomicalDusk)

    return buildList {
        var cursor = midnightEpoch
        fun add(end: Long?, color: Color) {
            if (end != null && end > cursor) {
                add(DialSegment(cursor, end, color))
                cursor = end
            }
        }
        add(astroDawn, Color(0xFF010115))  // night
        add(nautDawn,  Color(0xFF030335))  // astro twilight
        add(blueStart, Color(0xFF07074A))  // nautical twilight
        add(sunrise,   Color(0xFF1A3A8F))  // blue hour
        add(ghEnd,     Color(0xFFD46000))  // golden hour morning
        add(ghStart,   Color(0xFF1A7AB5))  // daylight
        add(sunset,    Color(0xFFD46000))  // golden hour evening
        add(blueEnd,   Color(0xFF1A3A8F))  // blue hour evening
        add(nautDusk,  Color(0xFF07074A))  // nautical twilight
        add(astroDusk, Color(0xFF030335))  // astro twilight
        add(endOfDay,  Color(0xFF010115))  // night
    }
}

// ─── Phase → alpha ────────────────────────────────────────────────────────────

private fun dialCelestialAlphas(phase: SkyPhase): Pair<Float, Float> = when (phase) {
    SkyPhase.NIGHT                 -> 0f to 1f
    SkyPhase.ASTRONOMICAL_TWILIGHT -> 0.1f to 0.9f
    SkyPhase.NAUTICAL_TWILIGHT     -> 0.3f to 0.7f
    SkyPhase.BLUE_HOUR_MORNING     -> 0.6f to 0.4f
    SkyPhase.BLUE_HOUR_EVENING     -> 0.4f to 0.6f
    SkyPhase.GOLDEN_HOUR_MORNING   -> 0.9f to 0.1f
    SkyPhase.GOLDEN_HOUR_EVENING   -> 0.9f to 0.1f
    SkyPhase.DAYLIGHT              -> 1f to 0f
}

// ─── Sun drawing ──────────────────────────────────────────────────────────────

private fun DrawScope.dialDrawSun(
    cx: Float, cy: Float,
    color: Color,
    alpha: Float,
    glowRadius: Float,
) {
    drawCircle(color.copy(alpha = alpha * 0.22f), glowRadius * 3.0f, Offset(cx, cy))
    drawCircle(color.copy(alpha = alpha * 0.60f), glowRadius * 1.7f, Offset(cx, cy))
    drawCircle(color.copy(alpha = alpha),         17f,               Offset(cx, cy))
}

// ─── Moon drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.dialDrawMoon(
    cx: Float, cy: Float,
    phase: Double,
    alpha: Float,
    glowRadius: Float,
) {
    val radius      = 22f
    val illumination = ((1 - cos(2 * PI * phase)) / 2).toFloat()
    val litColor    = Color(0xFFEEF4FF).copy(alpha = alpha)
    val darkColor   = Color(0xFF050520).copy(alpha = alpha * 0.95f)

    if (illumination > 0.1f) {
        drawCircle(
            color  = litColor.copy(alpha = alpha * illumination * 0.3f),
            radius = glowRadius * 1.6f * illumination,
            center = Offset(cx, cy),
        )
    }
    dialDrawMoonPhaseDisc(Offset(cx, cy), radius, phase, litColor, darkColor)
    drawCircle(
        color  = litColor.copy(alpha = alpha * 0.25f),
        radius = radius,
        center = Offset(cx, cy),
        style  = Stroke(0.8f),
    )
}

private fun DrawScope.dialDrawMoonPhaseDisc(
    center: Offset,
    radius: Float,
    phase: Double,
    litColor: Color,
    darkColor: Color,
) {
    val rect            = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    val terminatorScale = abs(cos(phase * 2 * PI)).toFloat()
    val termRect        = Rect(
        center.x - terminatorScale * radius,
        center.y - radius,
        center.x + terminatorScale * radius,
        center.y + radius,
    )

    drawCircle(darkColor, radius, center)

    if (phase <= 0.5) {
        val litPath = Path().apply { arcTo(rect, -90f, 180f, true); close() }
        clipRect(center.x, center.y - radius, center.x + radius, center.y + radius) {
            drawPath(litPath, litColor)
        }
        if (terminatorScale > 0.02f) {
            val termPath  = Path().apply { addOval(termRect) }
            val termColor = if (phase < 0.25) darkColor else litColor
            drawPath(termPath, termColor)
        }
    } else {
        val litPath = Path().apply { arcTo(rect, 90f, 180f, true); close() }
        clipRect(center.x - radius, center.y - radius, center.x, center.y + radius) {
            drawPath(litPath, litColor)
        }
        if (terminatorScale > 0.02f) {
            val termPath  = Path().apply { addOval(termRect) }
            val termColor = if (phase < 0.75) litColor else darkColor
            drawPath(termPath, termColor)
        }
    }
}

// ─── Stars ────────────────────────────────────────────────────────────────────

private val dialStarPositions: List<Pair<Float, Float>> by lazy {
    val rng = java.util.Random(99L)
    List(60) { rng.nextFloat() to rng.nextFloat() }
}

private val dialStarSizes: List<Float> by lazy {
    val rng = java.util.Random(99L)
    List(60) { 0.8f + rng.nextFloat() * 1.8f }
}

private fun DrawScope.dialDrawStars(w: Float, h: Float, alpha: Float) {
    dialStarPositions.forEachIndexed { i, (fx, fy) ->
        drawCircle(
            color  = Color.White.copy(alpha = alpha * (0.4f + dialStarSizes[i] / 4f)),
            radius = dialStarSizes[i] * 0.5f,
            center = Offset(fx * w, fy * h * 0.70f),
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun dialFormatMins(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else          -> "${minutes}m"
}
