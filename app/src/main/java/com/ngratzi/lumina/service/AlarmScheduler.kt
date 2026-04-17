package com.ngratzi.lumina.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ngratzi.lumina.MainActivity
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
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID   = "lumina_events"
        const val CHANNEL_NAME = "Solar & Tide Events"
        const val EXTRA_EVENT  = "event_name"
        const val EXTRA_LABEL  = "event_label"
        const val EXTRA_BODY   = "event_body"
        const val ACTION_SOLAR = "com.ngratzi.lumina.SOLAR_ALARM"
        const val ACTION_TIDE  = "com.ngratzi.lumina.TIDE_ALARM"

        // Each event gets two slots: *2 = 10-min-before, *2+1 = at-event
        private fun warningCode(event: SolarEvent) = event.ordinal * 2
        private fun eventCode(event: SolarEvent)   = event.ordinal * 2 + 1
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for sunrise, sunset, golden hour, tides, and more"
            enableLights(true)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Schedules two notifications for [config]:
     *  • T-10 min  — "Sunrise in 10 minutes"
     *  • T+offset  — "Sunrise · now" (respects user offset)
     */
    fun scheduleAlarm(config: AlarmConfig, eventTime: ZonedDateTime) {
        if (!config.enabled) return
        if (!canScheduleExact()) return

        val eventMs  = eventTime.toInstant().toEpochMilli()
        val now      = System.currentTimeMillis()
        val label    = config.event.displayName

        // ── 10-min warning ────────────────────────────────────────────────────
        val warningMs = eventMs - 10 * 60_000L
        if (warningMs > now) {
            scheduleExact(
                requestCode = warningCode(config.event),
                fireAtMs    = warningMs,
                eventName   = config.event.name,
                label       = label,
                body        = "In 10 minutes",
            )
        }

        // ── At-event (+ user offset) ──────────────────────────────────────────
        val atMs = eventMs + config.offsetMinutes * 60_000L
        if (atMs > now) {
            val body = when {
                config.offsetMinutes < 0 -> "${-config.offsetMinutes} min before · now"
                config.offsetMinutes > 0 -> "${config.offsetMinutes} min after · now"
                else                     -> "Now"
            }
            scheduleExact(
                requestCode = eventCode(config.event),
                fireAtMs    = atMs,
                eventName   = config.event.name,
                label       = label,
                body        = body,
            )
        }
    }

    fun cancelAlarm(event: SolarEvent) {
        cancelPending(warningCode(event))
        cancelPending(eventCode(event))
    }

    fun scheduleAll(alarms: List<AlarmConfig>, sunTimes: SunTimes) {
        for (alarm in alarms) {
            val time = alarm.event.resolveTime(sunTimes) ?: continue
            scheduleAlarm(alarm, time)
        }
    }

    /** Fires a notification immediately — no AlarmManager, used for QA only. */
    fun fireTestNotification(event: SolarEvent) {
        showNotification(
            id    = eventCode(event) + 10_000,
            title = "[TEST] ${event.displayName}",
            body  = "This is a test notification",
        )
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    // ─── Internals ─────────────────────────────────────────────────────────────

    private fun scheduleExact(
        requestCode: Int,
        fireAtMs: Long,
        eventName: String,
        label: String,
        body: String,
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SOLAR
            putExtra(EXTRA_EVENT, eventName)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_BODY,  body)
        }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pending)
    }

    private fun cancelPending(requestCode: Int) {
        val intent  = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_SOLAR }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pending?.let { alarmManager.cancel(it) }
    }

    internal fun showNotification(id: Int, title: String, body: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun SolarEvent.resolveTime(sunTimes: SunTimes): ZonedDateTime? = when (this) {
        SolarEvent.ASTRONOMICAL_DAWN   -> sunTimes.astronomicalDawn
        SolarEvent.BLUE_HOUR_MORNING   -> sunTimes.blueHourStart
        SolarEvent.GOLDEN_HOUR_MORNING -> sunTimes.sunrise
        SolarEvent.SUNRISE             -> sunTimes.sunrise
        SolarEvent.GOLDEN_HOUR_EVENING -> sunTimes.goldenHourStart
        SolarEvent.SUNSET              -> sunTimes.sunset
        SolarEvent.BLUE_HOUR_EVENING   -> sunTimes.blueHourEnd
        SolarEvent.MOONRISE            -> null // set separately via moon calculator
        else                           -> null // tide events resolved separately
    }
}
