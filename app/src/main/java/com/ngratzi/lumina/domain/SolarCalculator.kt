package com.ngratzi.lumina.domain

import com.ngratzi.lumina.data.model.SunTimes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/**
 * Solar position calculator based on the NOAA Solar Calculator algorithm.
 * Reference: https://gml.noaa.gov/grad/solcalc/calcdetails.html
 *
 * Accuracy: sunrise/sunset within ~1 minute for latitudes between ±60°.
 */
object SolarCalculator {

    private val DEG = PI / 180.0
    private val RAD = 180.0 / PI

    // Refraction-corrected horizon altitudes
    private const val SUNRISE_ALTITUDE   = -0.8333  // standard horizon
    private const val CIVIL_ALTITUDE     = -6.0
    private const val NAUTICAL_ALTITUDE  = -12.0
    private const val ASTRO_ALTITUDE     = -18.0
    private const val GOLDEN_HOUR_ALTITUDE = 6.0

    // ─── Julian Day ───────────────────────────────────────────────────────────

    private fun julianDay(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val d = date.dayOfMonth
        if (m <= 2) { y--; m += 12 }
        val a = (y / 100).toLong()
        val b = 2 - a + (a / 4).toLong()
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525.0

    // ─── Sun orbital elements ─────────────────────────────────────────────────

    private fun geomMeanLonSun(t: Double) =
        (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0

    private fun geomMeanAnomalySun(t: Double) =
        357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentricity(t: Double) =
        0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenter(t: Double): Double {
        val m = Math.toRadians(geomMeanAnomalySun(t))
        return (1.914602 - t * (0.004817 + 0.000014 * t)) * sin(m) +
               (0.019993 - 0.000101 * t) * sin(2 * m) +
               0.000289 * sin(3 * m)
    }

    private fun sunApparentLon(t: Double): Double {
        val trueLon = geomMeanLonSun(t) + sunEqOfCenter(t)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        return trueLon - 0.00569 - 0.00478 * sin(omega)
    }

    private fun meanObliquity(t: Double) =
        23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0

    private fun obliquityCorrected(t: Double): Double {
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        return meanObliquity(t) + 0.00256 * cos(omega)
    }

    private fun sunDeclination(t: Double): Double {
        val e = Math.toRadians(obliquityCorrected(t))
        val lambda = Math.toRadians(sunApparentLon(t))
        return RAD * asin(sin(e) * sin(lambda))
    }

    private fun equationOfTime(t: Double): Double {
        val e = Math.toRadians(obliquityCorrected(t))
        val l0 = Math.toRadians(geomMeanLonSun(t))
        val ecc = eccentricity(t)
        val m = Math.toRadians(geomMeanAnomalySun(t))
        val y = tan(e / 2).let { it * it }
        return 4.0 * RAD * (
            y * sin(2 * l0) -
            2 * ecc * sin(m) +
            4 * ecc * y * sin(m) * cos(2 * l0) -
            0.5 * y * y * sin(4 * l0) -
            1.25 * ecc * ecc * sin(2 * m)
        )
    }

    // ─── Hour angle for a target altitude ─────────────────────────────────────

    /**
     * Returns the hour angle (degrees) at which the sun reaches [elevation].
     * Returns null when the sun never reaches that elevation (polar day/night).
     */
    private fun hourAngleForElevation(latDeg: Double, decDeg: Double, elevDeg: Double): Double? {
        val cosH = (sin(elevDeg * DEG) - sin(latDeg * DEG) * sin(decDeg * DEG)) /
                   (cos(latDeg * DEG) * cos(decDeg * DEG))
        return if (cosH < -1.0 || cosH > 1.0) null
        else RAD * acos(cosH)
    }

    // ─── Event time (with one refinement iteration) ──────────────────────────

    /**
     * @param rising  true = morning event (ascending sun), false = evening (descending)
     * @return UTC minutes from midnight
     */
    private fun utcMinutesForElevation(
        jdNoon: Double,
        latDeg: Double,
        lonDeg: Double,
        elevDeg: Double,
        rising: Boolean,
    ): Double? {
        // Pass 1 — use noon JD
        var t = julianCentury(jdNoon)
        var dec = sunDeclination(t)
        var eqT = equationOfTime(t)
        val ha1 = hourAngleForElevation(latDeg, dec, elevDeg) ?: return null
        val utc1 = if (rising) 720 - 4 * (lonDeg + ha1) - eqT
                   else        720 - 4 * (lonDeg - ha1) - eqT

        // Pass 2 — refine with JD at estimated event time
        t = julianCentury(jdNoon + utc1 / 1440.0 - 0.5)
        dec = sunDeclination(t)
        eqT = equationOfTime(t)
        val ha2 = hourAngleForElevation(latDeg, dec, elevDeg) ?: return null
        return if (rising) 720 - 4 * (lonDeg + ha2) - eqT
               else        720 - 4 * (lonDeg - ha2) - eqT
    }

    private fun eventZdt(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        elevDeg: Double,
        rising: Boolean,
        zone: ZoneId,
    ): ZonedDateTime? {
        val jdNoon = julianDay(date) + 0.5
        val utcMins = utcMinutesForElevation(jdNoon, latDeg, lonDeg, elevDeg, rising)
            ?: return null
        val totalSeconds = (utcMins * 60).toLong()
        return date.atStartOfDay(ZoneOffset.UTC)
            .plusSeconds(totalSeconds)
            .withZoneSameInstant(zone)
    }

    private fun solarNoonZdt(
        date: LocalDate,
        lonDeg: Double,
        zone: ZoneId,
    ): ZonedDateTime {
        val jdNoon = julianDay(date) + 0.5
        val t = julianCentury(jdNoon)
        val eqT = equationOfTime(t)
        val utcMins = 720 - 4 * lonDeg - eqT
        return date.atStartOfDay(ZoneOffset.UTC)
            .plusSeconds((utcMins * 60).toLong())
            .withZoneSameInstant(zone)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun getSunTimes(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SunTimes = SunTimes(
        astronomicalDawn    = eventZdt(date, latDeg, lonDeg, ASTRO_ALTITUDE,        true,  zone),
        nauticalDawn        = eventZdt(date, latDeg, lonDeg, NAUTICAL_ALTITUDE,     true,  zone),
        blueHourStart       = eventZdt(date, latDeg, lonDeg, CIVIL_ALTITUDE,        true,  zone),
        sunrise             = eventZdt(date, latDeg, lonDeg, SUNRISE_ALTITUDE,      true,  zone),
        goldenHourEnd       = eventZdt(date, latDeg, lonDeg, GOLDEN_HOUR_ALTITUDE,  true,  zone),
        solarNoon           = solarNoonZdt(date, lonDeg, zone),
        goldenHourStart     = eventZdt(date, latDeg, lonDeg, GOLDEN_HOUR_ALTITUDE,  false, zone),
        sunset              = eventZdt(date, latDeg, lonDeg, SUNRISE_ALTITUDE,      false, zone),
        blueHourEnd         = eventZdt(date, latDeg, lonDeg, CIVIL_ALTITUDE,        false, zone),
        nauticalDusk        = eventZdt(date, latDeg, lonDeg, NAUTICAL_ALTITUDE,     false, zone),
        astronomicalDusk    = eventZdt(date, latDeg, lonDeg, ASTRO_ALTITUDE,        false, zone),
    )

    /**
     * Returns the sun's altitude above the horizon in degrees at the given instant.
     * Negative = below horizon.
     */
    fun getSunAltitude(dateTime: ZonedDateTime, latDeg: Double, lonDeg: Double): Double {
        val utc = dateTime.withZoneSameInstant(ZoneOffset.UTC)
        val fractionalDay = utc.hour / 24.0 + utc.minute / 1440.0 + utc.second / 86400.0
        val jd = julianDay(utc.toLocalDate()) + fractionalDay
        val t = julianCentury(jd)
        val dec = sunDeclination(t) * DEG
        val eqT = equationOfTime(t)
        val trueSolarTime = ((utc.hour * 60.0 + utc.minute + utc.second / 60.0) + eqT + 4 * lonDeg) % 1440.0
        val hourAngle = if (trueSolarTime / 4 < 0) trueSolarTime / 4 + 180 else trueSolarTime / 4 - 180
        val lat = latDeg * DEG
        val ha = hourAngle * DEG
        return RAD * asin(sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(ha))
    }

    /** Returns true if now is past solar noon (sun descending). */
    fun isEvening(dateTime: ZonedDateTime, lonDeg: Double): Boolean {
        val noon = solarNoonZdt(dateTime.toLocalDate(), lonDeg, dateTime.zone)
        return dateTime.isAfter(noon)
    }
}
