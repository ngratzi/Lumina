package com.ngratzi.lumina.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngratzi.lumina.data.model.SunTimes
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.theme.SkyThemeTokens
import java.time.ZonedDateTime

@Composable
fun DayTimeline(
    palette: SkyPalette,
    sunTimes: SunTimes,
    currentTime: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val midnight   = currentTime.toLocalDate().atStartOfDay(currentTime.zone)
    val midnightMs = midnight.toInstant().toEpochMilli()
    val dayMs      = 24L * 60 * 60 * 1000

    val segments   = buildTimelineSegments(sunTimes, midnightMs, dayMs)

    // Key event epochs for tick marks
    val tickEpochs = listOfNotNull(
        sunTimes.sunrise?.toInstant()?.toEpochMilli(),
        sunTimes.goldenHourEnd?.toInstant()?.toEpochMilli(),
        sunTimes.goldenHourStart?.toInstant()?.toEpochMilli(),
        sunTimes.sunset?.toInstant()?.toEpochMilli(),
    )

    val nowFrac = ((currentTime.toInstant().toEpochMilli() - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "TODAY",
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Color bar — clip handles the rounded corners properly
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .height(44.dp),
        ) {
            val w = size.width
            val h = size.height

            // Background (night)
            drawRect(color = Color(0xFF010115), size = Size(w, h))

            // Segments
            segments.forEach { seg ->
                val startFrac = ((seg.startEpoch - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val endFrac   = ((seg.endEpoch   - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                val segLeft   = w * startFrac
                val segWidth  = w * (endFrac - startFrac)
                if (segWidth > 0f) {
                    drawRect(
                        color   = seg.color,
                        topLeft = Offset(segLeft, 0f),
                        size    = Size(segWidth, h),
                    )
                }
            }

            // Hour dots (1–23)
            for (hour in 1..23) {
                val x = w * (hour / 24f)
                drawCircle(
                    color  = Color.White.copy(alpha = 0.22f),
                    radius = 2f,
                    center = Offset(x, h - 6f),
                )
            }

            // Solar event tick marks (sunrise, golden hour transitions, sunset)
            tickEpochs.forEach { epochMs ->
                val x = w * ((epochMs - midnightMs).toFloat() / dayMs).coerceIn(0f, 1f)
                drawLine(
                    color       = Color.White.copy(alpha = 0.30f),
                    start       = Offset(x, 0f),
                    end         = Offset(x, h),
                    strokeWidth = 1f,
                )
            }

            // Current time marker
            val nowX = w * nowFrac
            drawLine(
                color       = Color.White.copy(alpha = 0.90f),
                start       = Offset(nowX, 0f),
                end         = Offset(nowX, h),
                strokeWidth = 2f,
            )
            drawCircle(
                color  = Color.White,
                radius = 4.5f,
                center = Offset(nowX, h / 2f),
            )
        }

        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("12a", "6a", "12p", "6p", "12a").forEach { label ->
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

private data class TimelineSegment(val startEpoch: Long, val endEpoch: Long, val color: Color)

private fun buildTimelineSegments(
    sunTimes: SunTimes,
    midnightEpoch: Long,
    dayMillis: Long,
): List<TimelineSegment> {
    val endOfDay = midnightEpoch + dayMillis

    fun epoch(zdt: ZonedDateTime?) = zdt?.toInstant()?.toEpochMilli()

    val astroDawn = epoch(sunTimes.astronomicalDawn)
    val nautDawn  = epoch(sunTimes.nauticalDawn)
    val blueStart = epoch(sunTimes.blueHourStart)
    val sunrise   = epoch(sunTimes.sunrise)
    val ghEnd     = epoch(sunTimes.goldenHourEnd)
    val ghStart   = epoch(sunTimes.goldenHourStart)
    val sunset    = epoch(sunTimes.sunset)
    val blueEnd   = epoch(sunTimes.blueHourEnd)
    val nautDusk  = epoch(sunTimes.nauticalDusk)
    val astroDusk = epoch(sunTimes.astronomicalDusk)

    return buildList {
        var cursor = midnightEpoch
        fun add(end: Long?, color: Color) {
            if (end != null && end > cursor) {
                add(TimelineSegment(cursor, end, color))
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
