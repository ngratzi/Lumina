package com.ngratzi.lumina.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.data.repository.UserPreferencesRepository
import com.ngratzi.lumina.service.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val alarmScheduler: AlarmScheduler,
) : ViewModel() {

    val tailingTideThreshold: StateFlow<Double> = prefs.tailingTideThresholdFt
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UserPreferencesRepository.DEFAULT_TAILING_THRESHOLD,
        )

    fun setTailingTideThreshold(ft: Double) {
        viewModelScope.launch { prefs.setTailingTideThreshold(ft) }
    }

    fun fireTestAlarm(event: SolarEvent) {
        alarmScheduler.fireTestNotification(event)
    }
}
