package com.focusblock.ui.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.ModeStore
import com.focusblock.domain.model.EmergencyState
import com.focusblock.domain.model.LockOptions
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.ModeState
import com.focusblock.domain.model.Schedule
import com.focusblock.domain.model.StrictOptions
import com.focusblock.service.ModeKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModesViewModel(private val store: ModeStore) : ViewModel() {

    val state: StateFlow<ModeState?> = store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Turns Lock mode on; unlocking will ask for a typing challenge of [length] digits. */
    fun confirmLock(length: Int, options: LockOptions) {
        viewModelScope.launch {
            store.update { it.copy(mode = Mode.LOCK, lockLength = length, lock = options) }
        }
    }

    fun setLockOptions(options: LockOptions) {
        viewModelScope.launch { store.update { it.copy(lock = options) } }
    }

    fun switchToNormal() {
        viewModelScope.launch { store.update { ModeKeeper.leaveToNormal(it) } }
    }

    fun startStrict(until: Long?, schedule: Schedule?, options: StrictOptions) {
        viewModelScope.launch {
            store.update {
                it.copy(
                    mode = Mode.STRICT,
                    strictUntil = until,
                    strictSchedule = schedule,
                    strict = options,
                    emergency = EmergencyState(history = it.emergency.history),
                )
            }
        }
    }

}
