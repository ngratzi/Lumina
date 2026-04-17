package com.ngratzi.lumina.util

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val time12 = DateTimeFormatter.ofPattern("h:mm a")
    private val time24 = DateTimeFormatter.ofPattern("HH:mm")
    private val dayMonth = DateTimeFormatter.ofPattern("EEE MMM d")

    fun formatTime(time: ZonedDateTime, use24h: Boolean = false): String =
        time.format(if (use24h) time24 else time12)

    fun formatDate(time: ZonedDateTime): String = time.format(dayMonth)

    fun countdown(to: ZonedDateTime, from: ZonedDateTime = ZonedDateTime.now()): String {
        val duration = Duration.between(from, to)
        if (duration.isNegative) return ""
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    fun heightLabel(ft: Double, useMetric: Boolean = false): String =
        if (useMetric) "${"%.2f".format(ft * 0.3048)} m"
        else "${"%.1f".format(ft)} ft"

    fun speedLabel(knots: Double, useMph: Boolean = false): String =
        if (useMph) "${"%.0f".format(knots * 1.15078)} mph"
        else "${"%.0f".format(knots)} kt"
}
