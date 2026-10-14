package dev.sushil.focusblock.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.domain.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileListViewModel(private val repository: ProfileRepository) : ViewModel() {

    // null = still loading
    val profiles: StateFlow<List<Profile>?> =
        repository.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(profile: Profile, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(profile.id, enabled) }
    }
}
