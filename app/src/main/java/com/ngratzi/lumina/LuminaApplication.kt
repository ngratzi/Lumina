package com.ngratzi.lumina

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ngratzi.lumina.service.AlarmScheduler
import com.ngratzi.lumina.service.DailyAlarmWorker
import com.ngratzi.lumina.service.TrackRecordingService
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LuminaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private fun createTrackingNotificationChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            TrackRecordingService.CHANNEL_ID,
            "Track Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Live track recording status" }
        nm.createNotificationChannel(channel)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        alarmScheduler.createNotificationChannel()
        createTrackingNotificationChannel()
        DailyAlarmWorker.schedule(this)
    }
}
