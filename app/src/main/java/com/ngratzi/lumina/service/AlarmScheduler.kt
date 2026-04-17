package com.ngratzi.lumina.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ngratzi.lumina.data.model.AlarmConfig
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.data.model.SunTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val CHANNEL_ID = "lumina_events"
        const val CHANNEL_NAME = "Solar & Tide Events"
        const val EXTRA_EVENT = "event_name"
        const val EXTRA_LABEL = "event_label"
        const val EXTRA_DETAIL = "event_detail"
        const val ACTION_SOLAR = "com.ngratzi.lumina.SOLAR_ALARM"
        const val ACTION_TIDE = "com.ngratzi.lumina.TIDE_ALARM"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for sunrise, sunset, golden hour, tides, and more"
            enableLights(true)
            enableVibration(true)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun scheduleAlarm(config: AlarmConfig, eventTime: ZonedDateTime, detail: String = "") {
        if (!config.enabled) return
        if (!canScheduleExact()) return

        val fireAt = eventTime.toInstant().toEpochMilli() + config.offsetMinutes * 60_000L
        if (fireAt <= System.currentTimeMillis()) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SOLAR
            putExtra(EXTRA_EVENT, config.event.name)
            putExtra(EXTRA_LABEL, config.event.displayName)
            putExtra(EXTRA_DETAIL, detail)
        }

        val requestCode = config.event.ordinal
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
    }

    fun cancelAlarm(event: SolarEvent) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SOLAR
        }
        val pending = PendingIntent.getBroadcast(
            context, event.ordinal, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let { alarmManager.cancel(it) }
    }

    fun scheduleAll(alarms: List<AlarmConfig>, sunTimes: SunTimes) {
        for (alarm in alarms) {
            val time = alarm.event.resolveTime(sunTimes) ?: continue
            scheduleAlarm(alarm, time)
        }
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun SolarEvent.resolveTime(sunTimes: SunTimes): ZonedDateTime? = when (this) {
        SolarEvent.ASTRONOMICAL_DAWN    -> sunTimes.astronomicalDawn
        SolarEvent.BLUE_HOUR_MORNING    -> sunTimes.blueHourStart
        SolarEvent.GOLDEN_HOUR_MORNING  -> sunTimes.sunrise
        SolarEvent.SUNRISE              -> sunTimes.sunrise
        SolarEvent.GOLDEN_HOUR_EVENING  -> sunTimes.goldenHourStart
        SolarEvent.SUNSET               -> sunTimes.sunset
        SolarEvent.BLUE_HOUR_EVENING    -> sunTimes.blueHourEnd
        SolarEvent.MOONRISE             -> null // set separately
        else                            -> null
    }
}
