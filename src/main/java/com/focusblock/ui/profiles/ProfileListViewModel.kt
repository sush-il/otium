package com.focusblock.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.ProfileRepository
import com.focusblock.data.SettingsStore
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.Profile
import com.focusblock.ui.common.EditCheck
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The home screen: profiles, the current mode, and the quick on/off controls. */
class ProfileListViewModel(
    private val repository: ProfileRepository,
    private val settings: SettingsStore,
    modeStore: com.focusblock.data.ModeStore,
    private val guard: EditGuard,
) : ViewModel() {

    private fun <T> stateOf(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // null = still loading
    val profiles: StateFlow<List<Profile>?> = stateOf(repository.profiles, null)
    val mode: StateFlow<Mode?> = stateOf(modeStore.state.map { it.mode }, null)

    val appBlocking: StateFlow<Boolean?> = stateOf(settings.appBlockingEnabled.map { it }, null)
    val siteBlocking: StateFlow<Boolean?> = stateOf(settings.siteBlockingEnabled.map { it }, null)
    val reelsBlocking: StateFlow<Boolean?> = stateOf(settings.reelsBlockingEnabled.map { it }, null)
    val adultBlocking: StateFlow<Boolean?> = stateOf(settings.blockAdult.map { it }, null)

    /** Message when the current mode refuses a change. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Non-null while the Lock-mode password dialog is open; runs after a correct password. */
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    enum class Quick { APPS, SITES, REELS, ADULT }

    fun setQuick(which: Quick, enabled: Boolean) {
        // Turning protection ON is always fine; turning OFF is loosening and is guarded
        if (enabled) {
            viewModelScope.launch { apply(which, true) }
            return
        }
        viewModelScope.launch {
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> apply(which, false)
                EditCheck.NeedsPassword -> passwordNeededFor = { setQuick(which, false) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    private suspend fun apply(which: Quick, enabled: Boolean) = when (which) {
        Quick.APPS -> settings.setAppBlockingEnabled(enabled)
        Quick.SITES -> settings.setSiteBlockingEnabled(enabled)
        Quick.REELS -> settings.setReelsBlockingEnabled(enabled)
        Quick.ADULT -> settings.setBlockAdult(enabled)
    }

    fun setEnabled(profile: Profile, enabled: Boolean) {
        viewModelScope.launch {
            when (val check = guard.check(profile, profile.copy(enabled = enabled))) {
                EditCheck.Allowed -> repository.setEnabled(profile.id, enabled)
                EditCheck.NeedsPassword -> passwordNeededFor = { setEnabled(profile, enabled) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    fun pause(profile: Profile, minutes: Long) {
        viewModelScope.launch {
            when (val check = guard.checkPause(profile)) {
                EditCheck.Allowed -> repository.setPausedUntil(profile.id, java.time.LocalDateTime.now().plusMinutes(minutes))
                EditCheck.NeedsPassword -> passwordNeededFor = { pause(profile, minutes) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    fun resume(profile: Profile) {
        viewModelScope.launch { repository.setPausedUntil(profile.id, null) }
    }

    fun onPasswordAccepted() {
        guard.grantProfiles()
        val retry = passwordNeededFor
        passwordNeededFor = null
        retry?.invoke()
    }

    fun dismissPassword() {
        passwordNeededFor = null
    }

    fun dismissError() {
        error = null
    }
}
