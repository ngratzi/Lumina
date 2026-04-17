package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.model.WindForecast
import com.ngratzi.lumina.data.model.WindObservation
import com.ngratzi.lumina.data.model.WindSource
import com.ngratzi.lumina.data.remote.NoaaApiService
import com.ngratzi.lumina.data.remote.OpenMeteoApiService
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WindRepository @Inject constructor(
    private val noaaApi: NoaaApiService,
    private val openMeteoApi: OpenMeteoApiService,
) {
    private val noaaDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val noaaParseFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val meteoIsoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    /** Observed wind from NOAA met station. Returns null if station has no wind sensor. */
    suspend fun getNoaaWindObservations(stationId: String, date: LocalDate): List<WindObservation>? {
        return try {
            val begin = noaaDateFmt.format(date)
            val end = begin
            val response = noaaApi.getWind(stationId = stationId, beginDate = begin, endDate = end)
            if (response.error != null) return null
            response.data?.mapNotNull { sample ->
                val speed = sample.s.toDoubleOrNull() ?: return@mapNotNull null
                val dir = sample.d.toDoubleOrNull() ?: return@mapNotNull null
                val gust = sample.g.toDoubleOrNull()
                val time = try {
                    ZonedDateTime.of(
                        java.time.LocalDateTime.parse(sample.t, noaaParseFmt),
                        ZoneId.systemDefault()
                    )
                } catch (e: Exception) { return@mapNotNull null }
                WindObservation(
                    time = time,
                    speedKnots = speed,
                    gustKnots = gust,
                    directionDeg = dir,
                    source = WindSource.NOAA_STATION,
                )
            }?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /** 12-hour wind forecast from Open-Meteo. Always available. */
    suspend fun getOpenMeteoForecast(lat: Double, lon: Double): WindForecast? {
        return try {
            val response = openMeteoApi.getWindForecast(lat, lon)
            val hourly = response.hourly ?: return null
            val observations = hourly.time.indices.mapNotNull { i ->
                val speed = hourly.windspeed[i] ?: return@mapNotNull null
                val dir = hourly.winddirection[i] ?: return@mapNotNull null
                val gust = hourly.windgusts[i]
                val time = try {
                    ZonedDateTime.of(
                        java.time.LocalDateTime.parse(hourly.time[i], meteoIsoFmt),
                        ZoneId.of("UTC")
                    ).withZoneSameInstant(ZoneId.systemDefault())
                } catch (e: Exception) { return@mapNotNull null }
                WindObservation(
                    time = time,
                    speedKnots = speed,
                    gustKnots = gust,
                    directionDeg = dir,
                    source = WindSource.OPEN_METEO,
                )
            }
            WindForecast(hourly = observations)
        } catch (e: Exception) {
            null
        }
    }
}
