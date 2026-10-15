package dev.sushil.otium.ui.apps

import dev.sushil.otium.ui.common.BackButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.data.AppLimitRepository
import dev.sushil.otium.data.InstalledApp
import dev.sushil.otium.data.InstalledAppsRepository
import dev.sushil.otium.domain.model.AppLimit
import dev.sushil.otium.data.SettingsStore
import dev.sushil.otium.ui.common.AppIcon
import dev.sushil.otium.ui.common.EditGuard
import dev.sushil.otium.ui.common.LimitPause
import dev.sushil.otium.ui.common.PauseButton
import dev.sushil.otium.ui.common.PauseDialogs
import dev.sushil.otium.ui.common.appPauseKey
import dev.sushil.otium.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ManageBlockedAppsViewModel(
    installedApps: InstalledAppsRepository,
    appLimits: AppLimitRepository,
    settings: SettingsStore,
    guard: EditGuard,
) : ViewModel() {
    val pause = LimitPause(settings, guard, viewModelScope)
    val apps = MutableStateFlow<List<InstalledApp>?>(null)
    val limitsByPkg: StateFlow<Map<String, List<AppLimit>>> =
        appLimits.limits.map { all -> all.groupBy { it.packageName } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { apps.value = installedApps.allApps() }
    }
}

/** List every app; tap one to set its stacked limits. Blocked apps show their rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBlockedAppsScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val container = appContainer()
    val vm: ManageBlockedAppsViewModel = viewModel(
        factory = viewModelFactory { initializer { ManageBlockedAppsViewModel(container.installedAppsRepository, container.appLimitRepository, container.settingsStore, container.editGuard) } },
    )
    val apps by vm.apps.collectAsStateWithLifecycle()
    val limits by vm.limitsByPkg.collectAsStateWithLifecycle()
    val paused by vm.pause.paused.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    PauseDialogs(vm.pause)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked apps") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            // Blocked apps first, then the rest; filtered by search
            val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<InstalledApp> { it.packageName in limits }.thenBy { it.label.lowercase() })
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    val appRules = limits[app.packageName]
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenApp(app.packageName) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            if (!appRules.isNullOrEmpty()) {
                                Text(
                                    (if (appPauseKey(app.packageName) in paused) "Paused · " else "") + appRules.joinToString(" · ") { it.summary() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (!appRules.isNullOrEmpty()) {
                            val key = appPauseKey(app.packageName)
                            PauseButton(key in paused) { vm.pause.toggle(key) }
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
