package com.ngratzi.lumina.ui.weather

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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

@Composable
fun WeatherScreen(
    innerPadding: PaddingValues,
    viewModel: WeatherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalSkyTheme.current.palette

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding()),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = palette.accent,
            )
            return@Box
        }

        if (uiState.error != null || uiState.weather == null) {
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
            return@Box
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
                }
            )
        }

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
                    palette  = palette,
                    current  = weather.current.surfacePressure,
                    trend    = weather.current.pressureTrend,
                    history  = weather.pressureHistory,
                    modifier = Modifier
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
                    palette  = palette,
                    daily    = weather.daily,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
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

@Composable
private fun PressureCard(
    palette: SkyPalette,
    current: Double,
    trend: PressureTrend,
    history: List<Pair<java.time.ZonedDateTime, Double>>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = palette.surfaceDim,
        shape    = shape,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PRESSURE", style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Column {
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
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val trendColor = when (trend) {
                            PressureTrend.RISING_FAST, PressureTrend.RISING    -> palette.accent
                            PressureTrend.FALLING_FAST, PressureTrend.FALLING  -> palette.onSurface.copy(alpha = 0.65f)
                            PressureTrend.STEADY                                -> palette.onSurfaceVariant
                        }
                        Text(
                            "${trend.arrow} ${trend.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = trendColor,
                        )
                    }
                }
                if (history.size >= 2) {
                    PressureSparkline(
                        palette  = palette,
                        history  = history.map { it.second },
                        modifier = Modifier.size(width = 120.dp, height = 56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureSparkline(
    palette: SkyPalette,
    history: List<Double>,
    modifier: Modifier = Modifier,
) {
    val accent = palette.accent
    val outline = palette.onSurfaceVariant
    Canvas(modifier = modifier) {
        val n = history.size
        if (n < 2) return@Canvas
        val minP = history.min()
        val maxP = history.max()
        val range = (maxP - minP).coerceAtLeast(1.0)
        val w = size.width
        val h = size.height

        fun x(i: Int) = i.toFloat() / (n - 1) * w
        fun y(p: Double) = h - ((p - minP) / range * h).toFloat()

        val pts = history.mapIndexed { i, p -> Offset(x(i), y(p)) }

        // Area fill
        drawPath(
            path = Path().apply {
                moveTo(pts.first().x, h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, h)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
            ),
        )

        // Line
        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path  = linePath,
            color = accent.copy(alpha = 0.80f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // End dot
        drawCircle(accent, radius = 3.5f, center = pts.last())
    }
}

// ─── 7-day Forecast Card ───────────────────────────────────────────────────────

@Composable
private fun WeekForecastCard(
    palette: SkyPalette,
    daily: List<DailyForecast>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.clip(shape).border(0.5.dp, palette.outlineColor, shape),
        color    = palette.surfaceDim,
        shape    = shape,
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            daily.forEachIndexed { index, day ->
                DayForecastRow(palette = palette, day = day)
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
) {
    val today = LocalDate.now()
    val dayLabel = when (day.date) {
        today            -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else             -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel,
            style    = MaterialTheme.typography.bodyMedium,
            color    = palette.onSurface,
            modifier = Modifier.width(80.dp),
        )
        Text(
            wmoIcon(day.weatherCode),
            style    = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(36.dp),
        )
        if (day.precipProbability > 0) {
            Text(
                "${day.precipProbability}%",
                style = MaterialTheme.typography.bodySmall,
                color = palette.accent.copy(alpha = 0.80f),
                modifier = Modifier.width(40.dp),
            )
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "${day.tempMin.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${day.tempMax.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurface,
        )
    }
}
