package com.ngratzi.lumina.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.SkyPhase
import com.ngratzi.lumina.data.model.SkyState
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.util.TimeFormatter
import java.time.ZonedDateTime
import kotlin.math.*

@Composable
fun SkyHero(
    palette: SkyPalette,
    skyState: SkyState,
    currentTime: ZonedDateTime,
    locationName: String,
    sunriseEpoch: Long?,
    sunsetEpoch: Long?,
    moonriseEpoch: Long?,
    moonsetEpoch: Long?,
    moonPhase: Double,
    modifier: Modifier = Modifier,
) {
    val nowEpoch = currentTime.toInstant().toEpochMilli()
    val moonIsUp = moonriseEpoch == null ||
        (nowEpoch >= moonriseEpoch && (moonsetEpoch == null || nowEpoch <= moonsetEpoch))
    val minutesUntilMoonrise: Long? = if (!moonIsUp && moonriseEpoch != null && moonriseEpoch > nowEpoch) {
        (moonriseEpoch - nowEpoch) / 60_000L
    } else null
    val (_, moonAlpha) = celestialAlphas(skyState.phase)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.gradientTop, palette.gradientMid, palette.gradientBottom)
                )
            )
    ) {
        CelestialArc(
            palette = palette,
            phase = skyState.phase,
            sunriseEpoch = sunriseEpoch,
            sunsetEpoch = sunsetEpoch,
            moonriseEpoch = moonriseEpoch,
            moonsetEpoch = moonsetEpoch,
            moonPhase = moonPhase,
            nowEpoch = currentTime.toInstant().toEpochMilli(),
            modifier = Modifier.fillMaxSize(),
        )

        // Location row
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = palette.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = locationName.ifBlank { "Locating…" },
                style = MaterialTheme.typography.bodySmall,
                color = palette.onSurfaceVariant,
            )
        }

        // Live clock
        Text(
            text = TimeFormatter.formatTime(currentTime),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
            color = palette.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .statusBarsPadding(),
        )

        // Phase + countdown
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 20.dp),
        ) {
            Text(
                text = skyState.phase.displayName,
                style = MaterialTheme.typography.headlineLarge,
                color = palette.onSurface,
            )
            if (skyState.nextEventName.isNotBlank() && skyState.minutesUntilNextEvent > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${skyState.nextEventName} in ${formatMinutes(skyState.minutesUntilNextEvent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
            }
            if (moonAlpha > 0f && !moonIsUp && minutesUntilMoonrise != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Moonrise in ${formatMinutes(minutesUntilMoonrise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Determine what to show and at what alpha ─────────────────────────────────

private fun celestialAlphas(phase: SkyPhase): Pair<Float, Float> {
    // Returns (sunAlpha, moonAlpha)
    return when (phase) {
        SkyPhase.NIGHT                 -> 0f to 1f
        SkyPhase.ASTRONOMICAL_TWILIGHT -> 0.1f to 0.9f
        SkyPhase.NAUTICAL_TWILIGHT     -> 0.3f to 0.7f
        SkyPhase.BLUE_HOUR_MORNING     -> 0.6f to 0.4f
        SkyPhase.BLUE_HOUR_EVENING     -> 0.4f to 0.6f
        SkyPhase.GOLDEN_HOUR_MORNING   -> 0.9f to 0.1f
        SkyPhase.GOLDEN_HOUR_EVENING   -> 0.9f to 0.1f
        SkyPhase.DAYLIGHT              -> 1f to 0f
    }
}

// ─── Main arc composable ──────────────────────────────────────────────────────

@Composable
private fun CelestialArc(
    palette: SkyPalette,
    phase: SkyPhase,
    sunriseEpoch: Long?,
    sunsetEpoch: Long?,
    moonriseEpoch: Long?,
    moonsetEpoch: Long?,
    moonPhase: Double,
    nowEpoch: Long,
    modifier: Modifier = Modifier,
) {
    val (sunAlpha, moonAlpha) = celestialAlphas(phase)

    // Pulsing glow for the active body
    val infiniteTransition = rememberInfiniteTransition(label = "celestial_pulse")
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    // Star twinkle for night
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "stars",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val arcLeft   = w * 0.08f
        val arcRight  = w * 0.92f
        val arcBottom = h * 0.88f
        val arcCenterX = w / 2f
        val arcRadiusX = (arcRight - arcLeft) / 2f
        val arcRadiusY = h * 0.72f

        fun xOnArc(fraction: Float): Float {
            val angle = PI.toFloat() * (1f - fraction)
            return arcCenterX + arcRadiusX * cos(angle)
        }
        fun yOnArc(fraction: Float): Float {
            val angle = PI.toFloat() * (1f - fraction)
            return arcBottom - arcRadiusY * sin(angle).coerceAtLeast(0f)
        }

        // Draw background stars at night
        if (moonAlpha > 0.3f) {
            drawStars(w, h, moonAlpha * starAlpha)
        }

        // Arc path
        val arcPath = Path().apply {
            val steps = 60
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = xOnArc(t)
                val y = yOnArc(t)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        val arcLineAlpha = if (phase == SkyPhase.DAYLIGHT) 0.3f else 0.15f
        drawPath(
            path = arcPath,
            color = palette.onSurface.copy(alpha = arcLineAlpha),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        )

        // Horizon line
        drawLine(
            color = palette.onSurfaceVariant.copy(alpha = 0.2f),
            start = Offset(arcLeft, arcBottom),
            end = Offset(arcRight, arcBottom),
            strokeWidth = 1f,
        )

        // ── Sun ──
        if (sunAlpha > 0f && sunriseEpoch != null && sunsetEpoch != null) {
            val fraction = ((nowEpoch - sunriseEpoch).toFloat() / (sunsetEpoch - sunriseEpoch))
                .coerceIn(0f, 1f)
            val sx = xOnArc(fraction)
            val sy = yOnArc(fraction)
            drawSun(sx, sy, palette.sunColor, sunAlpha, glowRadius)
        }

        // ── Moon ──
        if (moonAlpha > 0f) {
            val fraction = when {
                moonriseEpoch != null && moonsetEpoch != null -> {
                    ((nowEpoch - moonriseEpoch).toFloat() / (moonsetEpoch - moonriseEpoch))
                        .coerceIn(0f, 1f)
                }
                // Moon is up but we don't have exact times — place at reasonable default
                else -> 0.5f
            }

            // Only draw on arc when moon is above horizon (fraction 0..1)
            val moonVisible = moonriseEpoch == null ||
                (nowEpoch in moonriseEpoch..(moonsetEpoch ?: Long.MAX_VALUE))

            if (moonVisible) {
                val mx = xOnArc(fraction)
                val my = yOnArc(fraction)
                drawMoon(mx, my, moonPhase, moonAlpha, glowRadius)
            }
        }
    }
}

// ─── Sun drawing ──────────────────────────────────────────────────────────────

private fun DrawScope.drawSun(
    cx: Float, cy: Float,
    color: Color,
    alpha: Float,
    glowRadius: Float,
) {
    drawCircle(color.copy(alpha = alpha * 0.20f), glowRadius * 2.2f, Offset(cx, cy))
    drawCircle(color.copy(alpha = alpha * 0.45f), glowRadius * 1.2f, Offset(cx, cy))
    drawCircle(color.copy(alpha = alpha),         6f,                Offset(cx, cy))
}

// ─── Moon drawing ─────────────────────────────────────────────────────────────

private fun DrawScope.drawMoon(
    cx: Float, cy: Float,
    phase: Double,
    alpha: Float,
    glowRadius: Float,
) {
    val radius = 10f
    val illumination = ((1 - cos(2 * PI * phase)) / 2).toFloat()

    val litColor  = Color(0xFFDDE8FF).copy(alpha = alpha)
    val darkColor = Color(0xFF050520).copy(alpha = alpha * 0.95f)

    // Subtle glow scaled by illumination
    if (illumination > 0.1f) {
        drawCircle(
            color = litColor.copy(alpha = alpha * illumination * 0.3f),
            radius = glowRadius * 1.6f * illumination,
            center = Offset(cx, cy),
        )
    }

    // Phase disc
    drawMoonPhaseDisc(Offset(cx, cy), radius, phase, litColor, darkColor)

    // Rim highlight
    drawCircle(
        color = litColor.copy(alpha = alpha * 0.25f),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(0.8f),
    )
}

/**
 * Draws an accurate moon phase disc using two overlapping shapes:
 *  1. A semicircle on the lit side
 *  2. An ellipse terminator (dark for crescent, lit for gibbous)
 *
 * phase:  0 = new, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter
 */
private fun DrawScope.drawMoonPhaseDisc(
    center: Offset,
    radius: Float,
    phase: Double,
    litColor: Color,
    darkColor: Color,
) {
    val rect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

    // Base: full dark circle
    drawCircle(darkColor, radius, center)

    // Terminator ellipse half-width = |cos(phase * 2π)| * radius
    val terminatorScale = abs(cos(phase * 2 * PI)).toFloat()
    val termRect = Rect(
        center.x - terminatorScale * radius,
        center.y - radius,
        center.x + terminatorScale * radius,
        center.y + radius,
    )

    if (phase <= 0.5) {
        // Waxing: right half is lit
        val litPath = Path().apply {
            arcTo(rect, -90f, 180f, true)
            close()
        }
        // Clip to right half of canvas so we don't paint over left
        clipRect(center.x, center.y - radius, center.x + radius, center.y + radius) {
            drawPath(litPath, litColor)
        }

        // Terminator: dark ellipse if crescent (phase<0.25), lit ellipse if gibbous (phase>0.25)
        if (terminatorScale > 0.02f) {
            val termPath = Path().apply { addOval(termRect) }
            val termColor = if (phase < 0.25) darkColor else litColor
            drawPath(termPath, termColor)
        }
    } else {
        // Waning: left half is lit
        val litPath = Path().apply {
            arcTo(rect, 90f, 180f, true)
            close()
        }
        clipRect(center.x - radius, center.y - radius, center.x, center.y + radius) {
            drawPath(litPath, litColor)
        }

        if (terminatorScale > 0.02f) {
            val termPath = Path().apply { addOval(termRect) }
            val termColor = if (phase < 0.75) litColor else darkColor
            drawPath(termPath, termColor)
        }
    }
}

// ─── Background stars ─────────────────────────────────────────────────────────

private val starPositions: List<Pair<Float, Float>> by lazy {
    val rng = java.util.Random(42L)
    List(60) { rng.nextFloat() to rng.nextFloat() }
}

private val starSizes: List<Float> by lazy {
    val rng = java.util.Random(42L)
    List(60) { 0.8f + rng.nextFloat() * 1.8f }
}

private fun DrawScope.drawStars(w: Float, h: Float, alpha: Float) {
    starPositions.forEachIndexed { i, (fx, fy) ->
        // Stars only in upper 70% of hero
        val x = fx * w
        val y = fy * h * 0.70f
        drawCircle(
            color = Color.White.copy(alpha = alpha * (0.4f + starSizes[i] / 4f)),
            radius = starSizes[i] * 0.5f,
            center = Offset(x, y),
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatMinutes(minutes: Long): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
