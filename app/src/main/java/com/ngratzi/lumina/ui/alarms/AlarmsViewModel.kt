package com.ngratzi.lumina.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.AlarmConfig
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.data.repository.AlarmRepository
import com.ngratzi.lumina.data.repository.SolarRepository
import com.ngratzi.lumina.service.AlarmScheduler
import com.ngratzi.lumina.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val solarRepository: SolarRepository,
    private val alarmScheduler: AlarmScheduler,
    private val locationHelper: LocationHelper,
) : ViewModel() {

    val alarms: StateFlow<List<AlarmConfig>> = alarmRepository.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(event: SolarEvent, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepository.setEnabled(event, enabled)
            if (enabled) {
                scheduleForToday(event)
            } else {
                alarmScheduler.cancelAlarm(event)
            }
        }
    }

    fun setOffset(event: SolarEvent, offsetMinutes: Int) {
        viewModelScope.launch {
            alarmRepository.setOffset(event, offsetMinutes)
            // Reschedule with new offset if currently enabled
            val config = alarmRepository.getAll().find { it.event == event } ?: return@launch
            if (config.enabled) {
                alarmScheduler.cancelAlarm(event)
                scheduleForToday(event)
            }
        }
    }

    private suspend fun scheduleForToday(event: SolarEvent) {
        val location = locationHelper.getLastLocation() ?: return
        val sunTimes = solarRepository.getSunTimes(
            LocalDate.now(), location.latitude, location.longitude,
        )
        val config = alarmRepository.getAll().find { it.event == event } ?: return
        val time   = event.resolveTime(sunTimes) ?: return
        alarmScheduler.scheduleAlarm(config, time)
    }

    private fun SolarEvent.resolveTime(
        sunTimes: com.ngratzi.lumina.data.model.SunTimes,
    ) = when (this) {
        SolarEvent.ASTRONOMICAL_DAWN   -> sunTimes.astronomicalDawn
        SolarEvent.BLUE_HOUR_MORNING   -> sunTimes.blueHourStart
        SolarEvent.GOLDEN_HOUR_MORNING -> sunTimes.sunrise
        SolarEvent.SUNRISE             -> sunTimes.sunrise
        SolarEvent.GOLDEN_HOUR_EVENING -> sunTimes.goldenHourStart
        SolarEvent.SUNSET              -> sunTimes.sunset
        SolarEvent.BLUE_HOUR_EVENING   -> sunTimes.blueHourEnd
        else                           -> null
    }
}
