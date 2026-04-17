package com.ngratzi.lumina.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ngratzi.lumina.data.repository.AlarmRepository
import com.ngratzi.lumina.data.repository.SolarRepository
import com.ngratzi.lumina.util.LocationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyAlarmWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val alarmRepository: AlarmRepository,
    private val solarRepository: SolarRepository,
    private val alarmScheduler: AlarmScheduler,
    private val locationHelper: LocationHelper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val location = locationHelper.getLastLocation() ?: return Result.retry()
        val today = LocalDate.now()
        val sunTimes = solarRepository.getSunTimes(today, location.latitude, location.longitude)
        val alarms = alarmRepository.getAll()
        alarmScheduler.scheduleAll(alarms, sunTimes)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "lumina_daily_alarms"

        fun schedule(context: Context) {
            // Calculate delay until next midnight
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault())
            val delayMs = midnight.toInstant().toEpochMilli() - System.currentTimeMillis()

            val request = PeriodicWorkRequestBuilder<DailyAlarmWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
