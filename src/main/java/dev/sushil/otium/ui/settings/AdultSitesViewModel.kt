package dev.sushil.otium.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.otium.data.ModeStore
import dev.sushil.otium.data.SettingsStore
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.ui.common.EditCheck
import dev.sushil.otium.ui.common.EditGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdultSitesViewModel(
    private val settings: SettingsStore,
    private val modeStore: ModeStore,
    private val guard: EditGuard,
) : ViewModel() {

    val blocked: StateFlow<Boolean?> = settings.blockAdult.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val modeState: StateFlow<ModeState?> = modeStore.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var error by mutableStateOf<String?>(null)
        private set
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun setBlocked(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settings.setBlockAdult(true)
                return@launch
            }
            when (val check = guard.checkLoosenAdult()) {
                EditCheck.Allowed -> settings.setBlockAdult(false)
                EditCheck.NeedsPassword -> passwordNeededFor = { setBlocked(false) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    /** Starts adult sites' own Strict window. Also turns adult blocking on, since Strict on an "off" setting means nothing. */
    fun startStrict(until: Long?, schedule: Schedule?) {
        viewModelScope.launch {
            settings.setBlockAdult(true)
            modeStore.update { it.copy(adultStrictUntil = until, adultStrictSchedule = schedule) }
        }
    }

    fun onPasswordAccepted() {
        val retry = passwordNeededFor
        passwordNeededFor = null
        retry?.invoke()
    }

    fun dismissPassword() { passwordNeededFor = null }
    fun dismissError() { error = null }
}
