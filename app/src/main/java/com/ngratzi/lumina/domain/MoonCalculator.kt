package com.ngratzi.lumina.domain

import com.ngratzi.lumina.data.model.MoonData
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.*

/**
 * Lunar calculator using the simplified Meeus algorithm (Astronomical Algorithms, Ch. 47).
 * Accuracy: moon position ~1°, rise/set ~5 minutes.
 */
object MoonCalculator {

    private val DEG = PI / 180.0
    private val RAD = 180.0 / PI
    private const val SYNODIC_MONTH = 29.530588853  // days

    // Known new moon: Jan 6, 2000 at 18:14 UTC → JD 2451549.26
    private const val KNOWN_NEW_MOON_JD = 2451549.26

    // Moon rise/set altitude: -0.583° accounts for parallax + refraction
    private const val MOON_HORIZON = -0.5833

    // Average perigee and apogee distances in km
    private const val MEAN_DISTANCE_KM = 384400.0
    private const val PERIGEE_KM = 356500.0
    private const val APOGEE_KM  = 406700.0

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

    // ─── Moon position (ecliptic) ─────────────────────────────────────────────

    /**
     * Returns (longitude°, latitude°, distanceKm) of the moon for the given Julian Day.
     * Uses the simplified Meeus formula — sufficient for rise/set and phase calculations.
     */
    private fun moonEcliptic(jd: Double): Triple<Double, Double, Double> {
        val d = jd - 2451545.0  // days since J2000.0

        // Orbital elements (degrees)
        val L = (218.316 + 13.176396 * d) % 360.0   // mean longitude
        val M = (134.963 + 13.064993 * d) % 360.0   // mean anomaly
        val F = (93.272  + 13.229350 * d) % 360.0   // argument of latitude

        val longitude  = L + 6.289 * sin(M * DEG)
        val latitude   = 5.128 * sin(F * DEG)
        val distance   = MEAN_DISTANCE_KM - 20905.0 * cos(M * DEG)

        return Triple(longitude % 360.0, latitude, distance)
    }

    /** Convert ecliptic (λ, β) to equatorial (RA°, Dec°) using obliquity ε. */
    private fun eclipticToEquatorial(lonDeg: Double, latDeg: Double, jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0
        val epsilon = (23.4393 - 0.0000004 * (jd - 2451545.0)) * DEG
        val lambda = lonDeg * DEG
        val beta   = latDeg * DEG

        val ra  = RAD * atan2(sin(lambda) * cos(epsilon) - tan(beta) * sin(epsilon), cos(lambda))
        val dec = RAD * asin(sin(beta) * cos(epsilon) + cos(beta) * sin(epsilon) * sin(lambda))
        return Pair((ra + 360) % 360.0, dec)
    }

    // ─── Rise / Set ───────────────────────────────────────────────────────────

    private fun hourAngleForAltitude(latDeg: Double, decDeg: Double, altDeg: Double): Double? {
        val cosH = (sin(altDeg * DEG) - sin(latDeg * DEG) * sin(decDeg * DEG)) /
                   (cos(latDeg * DEG) * cos(decDeg * DEG))
        return if (cosH < -1.0 || cosH > 1.0) null
        else RAD * acos(cosH)
    }

    /**
     * Returns UTC minutes from midnight for a moon rise (rising=true) or set event.
     * Uses two-pass refinement (Meeus Ch. 15).
     *
     * GMST is fixed at 0h UT for the date (the standard reference epoch).
     * Only the moon's RA/Dec is refined between passes.
     */
    private fun moonEventUtcMinutes(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        rising: Boolean,
    ): Double? {
        val jdMidnight = julianDay(date)  // 0h UT
        val d0 = jdMidnight - 2451545.0
        // GMST at 0h UT — fixed reference for this date
        val gmst0 = (280.46061837 + 360.98564736629 * d0) % 360.0

        fun eventMinsAt(jd: Double): Double? {
            val (moonLon, moonLat, _) = moonEcliptic(jd)
            val (ra, dec) = eclipticToEquatorial(moonLon, moonLat, jd)
            val ha = hourAngleForAltitude(latDeg, dec, MOON_HORIZON) ?: return null
            val transit = (ra - gmst0 - lonDeg + 720) % 360.0
            val utcTransitMins = transit / (360.0 / 1440.0)
            return if (rising) utcTransitMins - ha * 4 else utcTransitMins + ha * 4
        }

        // Pass 1: evaluate at midnight
        val mins1 = eventMinsAt(jdMidnight) ?: return null
        // Pass 2: refine moon position at estimated event time
        return eventMinsAt(jdMidnight + mins1 / 1440.0)
    }

    private fun moonEventZdt(
        date: LocalDate,
        latDeg: Double,
        lonDeg: Double,
        rising: Boolean,
        zone: ZoneId,
    ): ZonedDateTime? {
        val mins = moonEventUtcMinutes(date, latDeg, lonDeg, rising) ?: return null
        return date.atStartOfDay(ZoneOffset.UTC)
            .plusSeconds((mins * 60).toLong())
            .withZoneSameInstant(zone)
    }

    // ─── Phase ────────────────────────────────────────────────────────────────

    /** Phase in [0, 1): 0=new, 0.25=first quarter, 0.5=full, 0.75=last quarter */
    fun getPhase(date: LocalDate): Double {
        val jd = julianDay(date) + 0.5
        val daysSinceNewMoon = (jd - KNOWN_NEW_MOON_JD) % SYNODIC_MONTH
        val normalized = if (daysSinceNewMoon < 0) daysSinceNewMoon + SYNODIC_MONTH else daysSinceNewMoon
        return normalized / SYNODIC_MONTH
    }

    fun getIllumination(phase: Double) = (1 - cos(2 * PI * phase)) / 2

    fun getPhaseName(phase: Double) = when {
        phase < 0.0339 || phase >= 0.9661 -> "New Moon"
        phase < 0.2161 -> "Waxing Crescent"
        phase < 0.2839 -> "First Quarter"
        phase < 0.4661 -> "Waxing Gibbous"
        phase < 0.5339 -> "Full Moon"
        phase < 0.7161 -> "Waning Gibbous"
        phase < 0.7839 -> "Last Quarter"
        else           -> "Waning Crescent"
    }

    fun getPhaseEmoji(phase: Double) = when {
        phase < 0.0625 || phase >= 0.9375 -> "🌑"
        phase < 0.1875 -> "🌒"
        phase < 0.3125 -> "🌓"
        phase < 0.4375 -> "🌔"
        phase < 0.5625 -> "🌕"
        phase < 0.6875 -> "🌖"
        phase < 0.8125 -> "🌗"
        else           -> "🌘"
    }

    private fun daysUntil(date: LocalDate, targetPhase: Double): Int {
        val current = getPhase(date)
        var days = ((targetPhase - current + 1.0) % 1.0 * SYNODIC_MONTH).toInt()
        if (days == 0) days = SYNODIC_MONTH.toInt()
        return days
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun getMoonData(date: LocalDate, latDeg: Double, lonDeg: Double, zone: ZoneId = ZoneId.systemDefault()): MoonData {
        val phase = getPhase(date)
        val jd = julianDay(date) + 0.5
        val (lon, lat, distance) = moonEcliptic(jd)

        return MoonData(
            phase            = phase,
            illumination     = getIllumination(phase),
            phaseName        = getPhaseName(phase),
            phaseEmoji       = getPhaseEmoji(phase),
            moonrise         = moonEventZdt(date, latDeg, lonDeg, true,  zone),
            moonTransit      = null, // simplified — transit calc omitted for v1
            moonset          = moonEventZdt(date, latDeg, lonDeg, false, zone),
            distanceKm       = distance,
            isPerigee        = distance < PERIGEE_KM * 1.05,
            isApogee         = distance > APOGEE_KM  * 0.95,
            daysToFullMoon   = daysUntil(date, 0.5),
            daysToNewMoon    = daysUntil(date, 0.0),
        )
    }
}
