package com.ngratzi.lumina.data.repository

import com.ngratzi.lumina.data.model.MoonData
import com.ngratzi.lumina.data.model.SunTimes
import com.ngratzi.lumina.domain.MoonCalculator
import com.ngratzi.lumina.domain.SolarCalculator
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolarRepository @Inject constructor() {

    // Results are pure math — no I/O, no caching needed
    fun getSunTimes(date: LocalDate, lat: Double, lon: Double): SunTimes =
        SolarCalculator.getSunTimes(date, lat, lon, ZoneId.systemDefault())

    fun getMoonData(date: LocalDate, lat: Double, lon: Double): MoonData =
        MoonCalculator.getMoonData(date, lat, lon, ZoneId.systemDefault())
}
