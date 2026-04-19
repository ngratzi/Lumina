package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.model.*
import com.ngratzi.lumina.data.remote.OpenMeteoApiService
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class WeatherRepository @Inject constructor(
    private val openMeteoApi: OpenMeteoApiService,
) {
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherData? {
        return try {
            val resp = openMeteoApi.getWeatherForecast(lat, lon)
            val current  = resp.current  ?: return null
            val hourlyDto = resp.hourly  ?: return null
            val dailyDto  = resp.daily   ?: return null
            val zone = ZoneId.systemDefault()

            // ── Parse all hourly entries ────────────────────────────────────
            val allHourly = hourlyDto.time.indices.mapNotNull { i ->
                val speed = hourlyDto.windspeed[i]     ?: return@mapNotNull null
                val dir   = hourlyDto.winddirection[i] ?: return@mapNotNull null
                val time  = parseTime(hourlyDto.time[i], zone) ?: return@mapNotNull null
                HourlyWeather(
                    time              = time,
                    temperature       = hourlyDto.temperature[i] ?: 0.0,
                    weatherCode       = hourlyDto.weatherCode[i] ?: 0,
                    precipProbability = hourlyDto.precipProbability[i] ?: 0,
                    surfacePressure   = hourlyDto.surfacePressure[i] ?: 0.0,
                    windSpeedKnots    = speed,
                    windGustKnots     = hourlyDto.windgusts[i],
                    windDirectionDeg  = dir,
                )
            }

            val now = ZonedDateTime.now(zone)

            // Next 24 hours for display
            val next12h = allHourly
                .filter { !it.time.isBefore(now) }
                .take(24)

            // 24-hour pressure history (internal — used only for trend computation)
            val pressureHistory = allHourly
                .filter { it.time.isBefore(now) || it.time == now }
                .takeLast(24)

            // 48-hour pressure window for hourly chart (24h past + 24h future)
            val pressureHourly = allHourly
                .filter { it.time.isAfter(now.minusHours(25)) && it.time.isBefore(now.plusHours(25)) }
                .map { it.time to it.surfacePressure }

            // ── Pressure trend detection ────────────────────────────────────
            // Compare current pressure to 3 hours ago; maritime standard: >3 hPa/3h = rapid
            val currentPressure = current.surfacePressure
            val pressureThreeHoursAgo = pressureHistory
                .lastOrNull { it.time.isBefore(now.minusHours(3).plusMinutes(30)) }
                ?.surfacePressure
            val pressureTrend = if (pressureThreeHoursAgo != null) {
                val delta = currentPressure - pressureThreeHoursAgo
                when {
                    delta >  3.0 -> PressureTrend.RISING_FAST
                    delta >  1.0 -> PressureTrend.RISING
                    delta < -3.0 -> PressureTrend.FALLING_FAST
                    delta < -1.0 -> PressureTrend.FALLING
                    else         -> PressureTrend.STEADY
                }
            } else PressureTrend.STEADY

            // ── Current weather ─────────────────────────────────────────────
            val currentWeather = CurrentWeather(
                temperature      = current.temperature,
                feelsLike        = current.apparentTemperature,
                weatherCode      = current.weatherCode,
                condition        = wmoCondition(current.weatherCode),
                surfacePressure  = currentPressure,
                pressureTrend    = pressureTrend,
                windSpeedKnots   = current.windspeed,
                windGustKnots    = current.windgusts.takeIf { it > 0 },
                windDirectionDeg = current.winddirection,
            )

            // ── Daily forecast ──────────────────────────────────────────────
            val daily = dailyDto.time.indices.mapNotNull { i ->
                val date = try {
                    java.time.LocalDate.parse(dailyDto.time[i])
                } catch (e: Exception) { return@mapNotNull null }
                DailyForecast(
                    date              = date,
                    weatherCode       = dailyDto.weatherCode[i] ?: 0,
                    condition         = wmoCondition(dailyDto.weatherCode[i] ?: 0),
                    tempMax           = dailyDto.tempMax[i] ?: 0.0,
                    tempMin           = dailyDto.tempMin[i] ?: 0.0,
                    precipProbability = dailyDto.precipProbability[i] ?: 0,
                    windSpeedMaxKnots = dailyDto.windspeedMax[i] ?: 0.0,
                )
            }

            // 7-day daily pressure: noon hourly value per forecast day
            val pressureDaily = daily.mapNotNull { day ->
                val noonEntry = allHourly
                    .filter { it.time.toLocalDate() == day.date }
                    .minByOrNull { abs(it.time.hour - 12) }
                noonEntry?.let { day.date to it.surfacePressure }
            }

            // 7-day daily wind: max speed from daily DTO, direction from noon hourly
            val windDailySummaries = daily.mapNotNull { day ->
                val noonEntry = allHourly
                    .filter { it.time.toLocalDate() == day.date }
                    .minByOrNull { abs(it.time.hour - 12) }
                noonEntry?.let {
                    WindDailySummary(
                        date          = day.date,
                        maxSpeedKnots = day.windSpeedMaxKnots,
                        directionDeg  = noonEntry.windDirectionDeg,
                    )
                }
            }

            WeatherData(
                current             = currentWeather,
                hourly              = next12h,
                daily               = daily,
                pressureHourly      = pressureHourly,
                pressureDaily       = pressureDaily,
                windDailySummaries  = windDailySummaries,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTime(s: String, zone: ZoneId): ZonedDateTime? = try {
        ZonedDateTime.of(LocalDateTime.parse(s, isoFmt), ZoneId.of("UTC"))
            .withZoneSameInstant(zone)
    } catch (e: Exception) { null }
}
