package com.focusblock.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.ModeStore
import com.focusblock.data.ProfileRepository
import com.focusblock.data.SettingsStore
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.Profile
import com.focusblock.ui.common.EditCheck
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The dashboard: current mode, quick controls, and profiles. */
class ProfileListViewModel(
    private val repository: ProfileRepository,
    private val settings: SettingsStore,
    appLimits: com.focusblock.data.AppLimitRepository,
    siteLimits: com.focusblock.data.SiteLimitRepository,
    modeStore: ModeStore,
    private val guard: EditGuard,
) : ViewModel() {

    private fun <T> stateOf(flow: Flow<T>, initial: T): StateFlow<T> =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val profiles: StateFlow<List<Profile>?> = stateOf(repository.profiles, null)
    val mode: StateFlow<Mode?> = stateOf(modeStore.state.map { it.mode }, null)

    // Distinct apps/hosts that have any rule (always or limit-based)
    val appsBlockedCount: StateFlow<Int?> = stateOf(appLimits.limits.map { it.map { r -> r.packageName }.distinct().size }, null)
    val sitesBlockedCount: StateFlow<Int?> = stateOf(siteLimits.limits.map { it.map { r -> r.host }.distinct().size }, null)

    val keywordsOn: StateFlow<Boolean?> = stateOf(settings.keywordBlockingEnabled.map { it }, null)
    val adultOn: StateFlow<Boolean?> = stateOf(settings.blockAdult.map { it }, null)
    val reelsOn: StateFlow<Boolean?> = stateOf(settings.reelsBlockingEnabled.map { it }, null)

    /** Message when the current mode refuses a change. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Non-null while the Lock-mode password dialog is open; runs after a correct password. */
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    enum class Quick { KEYWORDS, ADULT, REELS }

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
        Quick.KEYWORDS -> settings.setKeywordBlockingEnabled(enabled)
        Quick.ADULT -> settings.setBlockAdult(enabled)
        Quick.REELS -> settings.setReelsBlockingEnabled(enabled)
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
                EditCheck.Allowed -> repository.setPausedUntil(profile.id, LocalDateTime.now().plusMinutes(minutes))
                EditCheck.NeedsPassword -> passwordNeededFor = { pause(profile, minutes) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    fun resume(profile: Profile) {
        viewModelScope.launch { repository.setPausedUntil(profile.id, null) }
    }

    fun onPasswordAccepted() {
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
