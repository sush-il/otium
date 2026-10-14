package dev.sushil.focusblock.ui.modes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.ModeStore
import dev.sushil.focusblock.domain.PasswordHasher
import dev.sushil.focusblock.domain.model.EmergencyState
import dev.sushil.focusblock.domain.model.LockOptions
import dev.sushil.focusblock.domain.model.Mode
import dev.sushil.focusblock.domain.model.ModeState
import dev.sushil.focusblock.domain.model.Schedule
import dev.sushil.focusblock.domain.model.StrictOptions
import dev.sushil.focusblock.service.ModeKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModesViewModel(private val store: ModeStore) : ViewModel() {

    val state: StateFlow<ModeState?> = store.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Hashes the new password and a fresh recovery code. [onReady] gets the code to show
     * once; Lock mode only turns on when the user confirms they saved it ([confirmLock]).
     */
    fun prepareLock(password: String, onReady: (PendingLock) -> Unit) {
        viewModelScope.launch {
            val pending = withContext(Dispatchers.Default) {
                val code = PasswordHasher.newRecoveryCode()
                PendingLock(
                    recoveryCode = code,
                    passwordHash = PasswordHasher.hash(password),
                    recoveryHash = PasswordHasher.hash(PasswordHasher.normalizeRecoveryCode(code)),
                )
            }
            onReady(pending)
        }
    }

    fun confirmLock(pending: PendingLock, options: LockOptions) {
        viewModelScope.launch {
            store.update {
                it.copy(mode = Mode.LOCK, passwordHash = pending.passwordHash, recoveryHash = pending.recoveryHash, lock = options)
            }
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

    data class PendingLock(val recoveryCode: String, val passwordHash: String, val recoveryHash: String)
}
