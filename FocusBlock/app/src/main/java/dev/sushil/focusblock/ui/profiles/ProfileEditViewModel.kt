package dev.sushil.focusblock.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.InstalledApp
import dev.sushil.focusblock.data.InstalledAppsRepository
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.domain.model.Profile
import kotlinx.coroutines.launch

/** Holds the form while creating ([profileId] = 0) or editing a profile. */
class ProfileEditViewModel(
    private val profileId: Long,
    private val profiles: ProfileRepository,
    private val installedApps: InstalledAppsRepository,
) : ViewModel() {

    val isNew = profileId == 0L

    var name by mutableStateOf("")
    var selected by mutableStateOf(emptySet<String>())
        private set
    var apps by mutableStateOf<List<InstalledApp>?>(null) // null = loading
        private set

    private var enabled = true

    init {
        viewModelScope.launch {
            if (!isNew) profiles.get(profileId)?.let {
                name = it.name
                selected = it.packages
                enabled = it.enabled
            }
            apps = installedApps.launchableApps()
        }
    }

    val canSave: Boolean get() = name.isNotBlank() && selected.isNotEmpty()

    fun toggleApp(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            profiles.save(Profile(id = profileId, name = name.trim(), enabled = enabled, packages = selected))
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            profiles.delete(profileId)
            onDeleted()
        }
    }
}
