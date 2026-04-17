package com.ngratzi.lumina.data.model

enum class SolarEvent(val displayName: String, val defaultOffsetMinutes: Int) {
    ASTRONOMICAL_DAWN("Astronomical Dawn", 0),
    BLUE_HOUR_MORNING("Blue Hour (Morning)", 0),
    GOLDEN_HOUR_MORNING("Golden Hour (Morning)", -15),
    SUNRISE("Sunrise", 0),
    GOLDEN_HOUR_EVENING("Golden Hour (Evening)", 0),
    SUNSET("Sunset", 0),
    BLUE_HOUR_EVENING("Blue Hour (Evening)", 0),
    HIGH_TIDE("High Tide", -30),
    LOW_TIDE("Low Tide", -30),
    SLACK_WATER_FLOOD("Slack Water (→ Flood)", 0),
    SLACK_WATER_EBB("Slack Water (→ Ebb)", 0),
    MOONRISE("Moonrise", 0),
}

data class AlarmConfig(
    val id: Int = 0,
    val event: SolarEvent,
    val enabled: Boolean = false,
    val offsetMinutes: Int = event.defaultOffsetMinutes,
)
