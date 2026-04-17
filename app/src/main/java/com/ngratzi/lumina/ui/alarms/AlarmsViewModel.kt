package com.ngratzi.lumina.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ngratzi.lumina.data.model.AlarmConfig
import com.ngratzi.lumina.data.model.SolarEvent
import com.ngratzi.lumina.data.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
) : ViewModel() {

    val alarms: StateFlow<List<AlarmConfig>> = alarmRepository.observeAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setEnabled(event: SolarEvent, enabled: Boolean) {
        viewModelScope.launch {
            alarmRepository.setEnabled(event, enabled)
        }
    }

    fun setOffset(event: SolarEvent, offsetMinutes: Int) {
        viewModelScope.launch {
            alarmRepository.setOffset(event, offsetMinutes)
        }
    }
}
