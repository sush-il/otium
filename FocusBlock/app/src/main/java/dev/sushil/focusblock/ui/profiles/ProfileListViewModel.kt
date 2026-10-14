package dev.sushil.focusblock.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.ui.common.EditCheck
import dev.sushil.focusblock.ui.common.EditGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileListViewModel(
    private val repository: ProfileRepository,
    private val guard: EditGuard,
) : ViewModel() {

    // null = still loading
    val profiles: StateFlow<List<Profile>?> =
        repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Message when the current mode refuses a change. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Non-null while the Lock-mode password dialog is open; runs after a correct password. */
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun setEnabled(profile: Profile, enabled: Boolean) {
        viewModelScope.launch {
            when (val check = guard.check(profile, profile.copy(enabled = enabled))) {
                EditCheck.Allowed -> repository.setEnabled(profile.id, enabled)
                EditCheck.NeedsPassword -> passwordNeededFor = { setEnabled(profile, enabled) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
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
