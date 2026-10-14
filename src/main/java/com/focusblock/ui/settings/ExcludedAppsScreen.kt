package com.focusblock.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.ui.apps.AppPicker
import com.focusblock.ui.common.appContainer

/** Pick apps a whole-phone profile should never block. Saved when the user taps Done. */
@Composable
fun ExcludedAppsScreen(onDone: () -> Unit) {
    val container = appContainer()
    val vm: ExcludedAppsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExcludedAppsViewModel(container.installedAppsRepository, container.settingsStore) }
        },
    )
    val apps by vm.apps.collectAsStateWithLifecycle()
    val stored by vm.stored.collectAsStateWithLifecycle()

    // Start from the saved set once it loads, then edit locally
    var selected by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(stored) { if (selected == null && stored != null) selected = stored }

    AppPicker(
        apps = apps,
        selected = selected ?: emptySet(),
        onToggle = { pkg -> selected = (selected ?: emptySet()).let { if (pkg in it) it - pkg else it + pkg } },
        onDone = {
            selected?.let { vm.save(it) }
            onDone()
        },
    )
}
