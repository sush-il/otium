package com.focusblock.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.data.InstalledApp
import com.focusblock.data.InstalledAppsRepository
import com.focusblock.data.SettingsStore
import com.focusblock.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageBlockedAppsViewModel(
    installedApps: InstalledAppsRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val apps = MutableStateFlow<List<InstalledApp>?>(null)
    val blocked: StateFlow<Set<String>?> =
        settings.quickBlockedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { apps.value = installedApps.allApps() }
    }

    fun save(selected: Set<String>) {
        viewModelScope.launch { settings.setQuickBlockedApps(selected) }
    }
}

/** Pick apps to block directly (always blocked), without making a profile. */
@Composable
fun ManageBlockedAppsScreen(onDone: () -> Unit) {
    val container = appContainer()
    val vm: ManageBlockedAppsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ManageBlockedAppsViewModel(container.installedAppsRepository, container.settingsStore) }
        },
    )
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stored by vm.blocked.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(stored) { if (selected == null && stored != null) selected = stored }

    AppPicker(
        title = "Blocked apps",
        apps = apps,
        selected = selected ?: emptySet(),
        onToggle = { pkg -> selected = (selected ?: emptySet()).let { if (pkg in it) it - pkg else it + pkg } },
        onDone = {
            selected?.let { vm.save(it) }
            onDone()
        },
    )
}
