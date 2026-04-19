package com.ngratzi.lumina.ui.weather

import android.graphics.Paint as AndroidPaint
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.ui.theme.LocalSkyTheme
import com.ngratzi.lumina.ui.theme.SkyPalette
import com.ngratzi.lumina.ui.tides.components.WindCard
import com.ngratzi.lumina.util.TimeFormatter
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    innerPadding: PaddingValues,
    viewModel: WeatherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalSkyTheme.current.palette
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) { if (pullState.isRefreshing) viewModel.refresh() }
    LaunchedEffect(uiState.isLoading) { if (!uiState.isLoading) pullState.endRefresh() }

    // Full-screen spinner only on initial load
    if (uiState.isLoading && uiState.weather == null) {
        Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = palette.accent)
        }
        return
    }

    // Error state with no existing data
    if (uiState.weather == null) {
        Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    uiState.error ?: "No data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                ) {
                    Text("Retry", color = palette.gradientTop)
                }
            }
        }
        return
    }

    val weather = uiState.weather!!

    // Build WindObservation + WindForecast from WeatherData for the WindCard
    val currentWind = remember(weather) {
            val c = weather.current
            WindObservation(
                time            = java.time.ZonedDateTime.now(),
                speedKnots      = c.windSpeedKnots,
                gustKnots       = c.windGustKnots,
                directionDeg    = c.windDirectionDeg,
                source          = WindSource.OPEN_METEO,
            )
        }
    val windForecast = remember(weather) {
        WindForecast(
            hourly = weather.hourly.map { h ->
                WindObservation(
                    time         = h.time,
                    speedKnots   = h.windSpeedKnots,
                    gustKnots    = h.windGustKnots,
                    directionDeg = h.windDirectionDeg,
                    source       = WindSource.OPEN_METEO,
                )
            },
            daily = weather.windDailySummaries,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            .nestedScroll(pullState.nestedScrollConnection),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    "WEATHER",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 16.dp, bottom = 12.dp),
                )
            }

            // Current conditions
            item {
                CurrentConditionsCard(
                    palette = palette,
                    current = weather.current,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Wind
            item {
                WindCard(
                    palette            = palette,
                    currentObservation = currentWind,
                    forecast           = windForecast,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Pressure
            item {
                PressureCard(
                    palette        = palette,
                    current        = weather.current.surfacePressure,
                    trend          = weather.current.pressureTrend,
                    pressureHourly = weather.pressureHourly,
                    pressureDaily  = weather.pressureDaily,
                    modifier       = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 7-day forecast header
            item {
                Text(
                    "7-DAY FORECAST",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                )
            }

            // 7-day forecast rows
            item {
                WeekForecastCard(
                    palette     = palette,
                    daily       = weather.daily,
                    jeepConfig  = uiState.jeepConfig,
                    modifier    = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        PullToRefreshContainer(pullState, Modifier.align(Alignment.TopCenter))
    }
}

// ─── Current Conditions Card ──────────────────────────────────────────────────

@Composable
private fun CurrentConditionsCard(
    palette: SkyPalette,
    current: CurrentWeather,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = palette.surfaceDim,
        shape    = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("NOW", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${current.temperature.toInt()}°",
                            style = MaterialTheme.typography.displaySmall,
                            color = palette.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Feels ${current.feelsLike.toInt()}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        current.condition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onSurface,
                    )
                }
                Text(
                    wmoIcon(current.weatherCode),
                    style = MaterialTheme.typography.displayMedium,
                )
            }
        }
    }
}

// ─── Pressure Card ─────────────────────────────────────────────────────────────

private enum class PressureRange { HOURLY, DAILY }

@Composable
private fun PressureCard(
    palette: SkyPalette,
    current: Double,
    trend: PressureTrend,
    pressureHourly: List<Pair<java.time.ZonedDateTime, Double>>,
    pressureDaily: List<Pair<java.time.LocalDate, Double>>,
    modifier: Modifier = Modifier,
) {
    var range by remember { mutableStateOf(PressureRange.HOURLY) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = palette.surfaceDim,
        shape    = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text("PRESSURE", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PressureRangeChip("24H",    range == PressureRange.HOURLY, palette) { range = PressureRange.HOURLY }
                    PressureRangeChip("7 DAY",  range == PressureRange.DAILY,  palette) { range = PressureRange.DAILY  }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Current value + trend
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${"%.1f".format(current)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "hPa",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.width(12.dp))
                val trendColor = when (trend) {
                    PressureTrend.RISING_FAST, PressureTrend.RISING    -> palette.accent
                    PressureTrend.FALLING_FAST, PressureTrend.FALLING  -> palette.onSurface.copy(alpha = 0.65f)
                    PressureTrend.STEADY                                -> palette.onSurfaceVariant
                }
                Text(
                    "${trend.arrow} ${trend.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            when (range) {
                PressureRange.HOURLY -> PressureHourlyChart(
                    palette = palette,
                    data    = pressureHourly,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                PressureRange.DAILY -> PressureDailyChart(
                    palette = palette,
                    data    = pressureDaily,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
        }
    }
}

@Composable
private fun PressureRangeChip(
    label: String,
    selected: Boolean,
    palette: SkyPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick      = onClick,
        shape        = shape,
        color        = if (selected) palette.accent.copy(alpha = 0.15f) else Color.Transparent,
        border       = BorderStroke(0.5.dp, if (selected) palette.accent else palette.outlineColor),
        modifier     = Modifier.height(24.dp),
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

@Composable
private fun PressureHourlyChart(
    palette: SkyPalette,
    data: List<Pair<java.time.ZonedDateTime, Double>>,
    modifier: Modifier = Modifier,
) {
    if (data.size < 2) return
    val accent      = palette.accent
    val gridColor   = palette.outlineColor
    val labelColor  = palette.onSurfaceVariant
    val nowColor    = palette.accent.copy(alpha = 0.55f)
    val now         = java.time.ZonedDateTime.now()
    val hourFmt     = DateTimeFormatter.ofPattern("ha")

    Canvas(modifier = modifier) {
        val lp = 44.dp.toPx()
        val rp =  8.dp.toPx()
        val tp =  6.dp.toPx()
        val bp = 22.dp.toPx()
        val cw = size.width - lp - rp
        val ch = size.height - tp - bp

        val pressures = data.map { it.second }
        val rawMin = pressures.min(); val rawMax = pressures.max()
        val pad = ((rawMax - rawMin) * 0.15).coerceAtLeast(2.0)
        val minP = floor((rawMin - pad) / 2) * 2
        val maxP = ceil((rawMax + pad)  / 2) * 2
        val pRange = (maxP - minP).coerceAtLeast(4.0)

        val startEpoch = data.first().first.toEpochSecond().toFloat()
        val endEpoch   = data.last().first.toEpochSecond().toFloat()
        val totalSec   = (endEpoch - startEpoch).coerceAtLeast(1f)

        fun xFor(t: java.time.ZonedDateTime) =
            lp + (t.toEpochSecond() - startEpoch) / totalSec * cw

        fun yFor(p: Double) =
            tp + ch - ((p - minP) / pRange * ch).toFloat()

        val labelPaint = AndroidPaint().apply {
            isAntiAlias = true
            textSize    = 9.5.dp.toPx()
            textAlign   = AndroidPaint.Align.RIGHT
            color       = labelColor.toArgb()
        }
        val xLabelPaint = AndroidPaint().apply {
            isAntiAlias = true
            textSize    = 9.5.dp.toPx()
            textAlign   = AndroidPaint.Align.CENTER
            color       = labelColor.toArgb()
        }

        // Y gridlines + labels (4 lines)
        val ySteps = 4
        repeat(ySteps + 1) { i ->
            val p = minP + pRange / ySteps * i
            val y = yFor(p)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(lp, y), Offset(lp + cw, y), 0.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(p), lp - 4.dp.toPx(), y + labelPaint.textSize * 0.35f, labelPaint,
            )
        }

        // X gridlines + labels every 6h
        val start = data.first().first
        val step  = (6 - start.hour % 6).let { if (it == 6) 0 else it }.toLong()
        var tick  = start.plusHours(step)
            .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        while (!tick.isAfter(data.last().first)) {
            val x = xFor(tick)
            if (x >= lp - 1f && x <= lp + cw + 1f) {
                drawLine(gridColor.copy(alpha = 0.18f), Offset(x, tp), Offset(x, tp + ch), 0.5.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    tick.format(hourFmt).lowercase(),
                    x, tp + ch + bp - 4.dp.toPx(), xLabelPaint,
                )
            }
            tick = tick.plusHours(6)
        }

        // "Now" dashed line
        val nowX = xFor(now).coerceIn(lp, lp + cw)
        drawLine(
            color       = nowColor,
            start       = Offset(nowX, tp),
            end         = Offset(nowX, tp + ch),
            strokeWidth = 1.dp.toPx(),
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
        )

        // Build point list
        val pts = data.map { (t, p) -> Offset(xFor(t), yFor(p)) }

        // Area fill
        drawPath(
            path = Path().apply {
                moveTo(pts.first().x, tp + ch)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, tp + ch)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                startY = tp, endY = tp + ch,
            ),
        )

        // Line
        drawPath(
            path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            },
            color = accent.copy(alpha = 0.85f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // End dot
        drawCircle(accent, radius = 3.dp.toPx(), center = pts.last())
    }
}

@Composable
private fun PressureDailyChart(
    palette: SkyPalette,
    data: List<Pair<java.time.LocalDate, Double>>,
    modifier: Modifier = Modifier,
) {
    if (data.size < 2) return
    val accent     = palette.accent
    val gridColor  = palette.outlineColor
    val labelColor = palette.onSurfaceVariant
    val today      = java.time.LocalDate.now()
    val dayFmt     = DateTimeFormatter.ofPattern("EEE")

    Canvas(modifier = modifier) {
        val lp = 44.dp.toPx()
        val rp =  8.dp.toPx()
        val tp =  6.dp.toPx()
        val bp = 22.dp.toPx()
        val cw = size.width - lp - rp
        val ch = size.height - tp - bp
        val n  = data.size

        val pressures = data.map { it.second }
        val rawMin = pressures.min(); val rawMax = pressures.max()
        val pad = ((rawMax - rawMin) * 0.20).coerceAtLeast(2.0)
        val minP = floor((rawMin - pad) / 2) * 2
        val maxP = ceil((rawMax + pad)  / 2) * 2
        val pRange = (maxP - minP).coerceAtLeast(4.0)

        fun xFor(i: Int) = lp + i.toFloat() / (n - 1) * cw
        fun yFor(p: Double) = tp + ch - ((p - minP) / pRange * ch).toFloat()

        val labelPaint = AndroidPaint().apply {
            isAntiAlias = true
            textSize    = 9.5.dp.toPx()
            textAlign   = AndroidPaint.Align.RIGHT
            color       = labelColor.toArgb()
        }
        val xLabelPaint = AndroidPaint().apply {
            isAntiAlias = true
            textSize    = 9.5.dp.toPx()
            textAlign   = AndroidPaint.Align.CENTER
            color       = labelColor.toArgb()
        }

        // Y gridlines + labels
        val ySteps = 4
        repeat(ySteps + 1) { i ->
            val p = minP + pRange / ySteps * i
            val y = yFor(p)
            drawLine(gridColor.copy(alpha = 0.25f), Offset(lp, y), Offset(lp + cw, y), 0.5.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(p), lp - 4.dp.toPx(), y + labelPaint.textSize * 0.35f, labelPaint,
            )
        }

        // Points + X labels
        val pts = data.mapIndexed { i, (date, p) ->
            val x = xFor(i)
            val label = if (date == today) "Today" else date.format(dayFmt)
            drawContext.canvas.nativeCanvas.drawText(
                label, x, tp + ch + bp - 4.dp.toPx(), xLabelPaint,
            )
            Offset(x, yFor(p))
        }

        // Vertical gridlines at each day
        pts.forEach { pt ->
            drawLine(gridColor.copy(alpha = 0.18f), Offset(pt.x, tp), Offset(pt.x, tp + ch), 0.5.dp.toPx())
        }

        // Area fill
        drawPath(
            path = Path().apply {
                moveTo(pts.first().x, tp + ch)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, tp + ch)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                startY = tp, endY = tp + ch,
            ),
        )

        // Line
        drawPath(
            path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            },
            color = accent.copy(alpha = 0.85f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Dots at each day
        pts.forEach { pt -> drawCircle(accent, radius = 3.dp.toPx(), center = pt) }
    }
}

// ─── 7-day Forecast Card ───────────────────────────────────────────────────────

@Composable
private fun WeekForecastCard(
    palette: SkyPalette,
    daily: List<DailyForecast>,
    jeepConfig: ToplessJeepConfig,
    modifier: Modifier = Modifier,
) {
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = palette.surfaceDim,
        shape    = shape,
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            daily.forEachIndexed { index, day ->
                val isExpanded  = expandedDate == day.date
                val isTopless   = jeepConfig.isToplessDay(day.tempMin, day.tempMax, day.precipProbability)
                DayForecastRow(
                    palette    = palette,
                    day        = day,
                    isExpanded = isExpanded,
                    isTopless  = isTopless,
                    onClick    = { expandedDate = if (isExpanded) null else day.date },
                )
                if (index < daily.lastIndex) {
                    HorizontalDivider(
                        color     = palette.outlineColor,
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayForecastRow(
    palette: SkyPalette,
    day: DailyForecast,
    isExpanded: Boolean,
    isTopless: Boolean,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val dayLabel = when (day.date) {
        today             -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else              -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
    ) {
        // ── Summary row ──────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dayLabel,
                style    = MaterialTheme.typography.bodyMedium,
                color    = palette.onSurface,
                modifier = Modifier.width(80.dp),
            )
            // Icon area — Jeep label when topless, weather emoji otherwise
            Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                if (isTopless) {
                    Text(
                        "🚙",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(wmoIcon(day.weatherCode), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (day.precipProbability > 0) {
                Text(
                    "${day.precipProbability}%",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = palette.accent.copy(alpha = 0.80f),
                    modifier = Modifier.width(40.dp),
                )
            } else {
                Spacer(Modifier.width(40.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("${day.tempMin.toInt()}°", style = MaterialTheme.typography.bodyMedium, color = palette.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("${day.tempMax.toInt()}°", style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
        }

        // ── Expanded detail ──────────────────────────────────────────────
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HorizontalDivider(color = palette.outlineColor, thickness = 0.5.dp)
                Spacer(Modifier.height(2.dp))
                if (isTopless) {
                    Text(
                        "🚙 Jeep Top Down",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = palette.accent,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                DetailRow(palette, "Condition", day.condition)
                DetailRow(palette, "High / Low", "${day.tempMax.toInt()}° / ${day.tempMin.toInt()}°")
                DetailRow(palette, "Rain chance", "${day.precipProbability}%")
                DetailRow(palette, "Max wind", "${day.windSpeedMaxKnots.toInt()} kt")
            }
        }
    }
}

@Composable
private fun DetailRow(palette: SkyPalette, label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = palette.onSurface)
    }
}

