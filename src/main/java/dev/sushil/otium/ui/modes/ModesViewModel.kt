package dev.sushil.otium.ui.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.otium.data.ModeStore
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.LockOptions
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.StrictOptions
import dev.sushil.otium.service.ModeKeeper
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

    /** One tap: Lock mode on again with the saved length and options. */
    fun relock() {
        viewModelScope.launch { store.update { if (it.hasLock) it.copy(mode = Mode.LOCK, relockAt = null) else it } }
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
