package com.focusblock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.InstalledApp
import com.focusblock.data.InstalledAppsRepository
import com.focusblock.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExcludedAppsViewModel(
    installedApps: InstalledAppsRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val apps = MutableStateFlow<List<InstalledApp>?>(null)
    val stored: StateFlow<Set<String>?> =
        settings.excludedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { apps.value = installedApps.allApps() }
    }

    fun save(selected: Set<String>) {
        viewModelScope.launch { settings.setExcludedApps(selected) }
    }
}
