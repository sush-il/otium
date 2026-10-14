package com.focusblock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.SettingsStore
import com.focusblock.data.TimerDisplay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    val timerDisplay: StateFlow<TimerDisplay?> =
        settings.timerDisplay.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lowTimeAlerts: StateFlow<Boolean?> =
        settings.lowTimeAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayResetHour: StateFlow<Int?> =
        settings.dayResetHour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTimerDisplay(value: TimerDisplay) {
        viewModelScope.launch { settings.setTimerDisplay(value) }
    }

    fun setLowTimeAlerts(value: Boolean) {
        viewModelScope.launch { settings.setLowTimeAlerts(value) }
    }

    fun setDayResetHour(value: Int) {
        viewModelScope.launch { settings.setDayResetHour(value) }
    }
}
