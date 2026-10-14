package com.focusblock.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.Profile
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog
import com.focusblock.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModes: () -> Unit,
) {
    val container = appContainer()
    val vm: ProfileListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileListViewModel(container.profileRepository, container.settingsStore, container.modeStore, container.editGuard)
            }
        },
    )
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog(
            message = "Lock mode: this change needs your password.",
            onUnlocked = { vm.onPasswordAccepted() },
            onDismiss = vm::dismissPassword,
        )
    }

    val context = LocalContext.current
    var blockerReady by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        blockerReady = Permissions.hasRequired(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Block") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewProfile) { Text("New profile") }
        },
    ) { padding ->
        val list = profiles
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!blockerReady) item { BlockerOffBanner(onOpenSetup) }

            item { ModeCard(mode, onOpenModes) }
            item { QuickControls(vm) }

            item {
                Text("Profiles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                list.isEmpty() -> item {
                    Text(
                        "No profiles yet. Tap \"New profile\" to choose apps to block.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                else -> items(list, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onOpenProfile(profile.id) },
                        onToggle = { vm.setEnabled(profile, it) },
                        onPause = { minutes -> vm.pause(profile, minutes) },
                        onResume = { vm.resume(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: Mode?, onOpenModes: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenModes)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mode.NORMAL to "Normal", Mode.LOCK to "Lock", Mode.STRICT to "Strict").forEach { (value, label) ->
                    FilterChip(selected = mode == value, onClick = onOpenModes, label = { Text(label) })
                }
            }
            Text(
                when (mode) {
                    Mode.LOCK -> "A password protects your settings. Tap to manage."
                    Mode.STRICT -> "Changes are locked until Strict ends. Tap to manage."
                    else -> "Change anything freely. Tap to set up Lock or Strict."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickControls(vm: ProfileListViewModel) {
    val apps by vm.appBlocking.collectAsStateWithLifecycle()
    val sites by vm.siteBlocking.collectAsStateWithLifecycle()
    val reels by vm.reelsBlocking.collectAsStateWithLifecycle()
    val adult by vm.adultBlocking.collectAsStateWithLifecycle()

    Text("Quick controls", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleTile("Apps", apps, Modifier.weight(1f)) { vm.setQuick(ProfileListViewModel.Quick.APPS, it) }
        ToggleTile("Sites", sites, Modifier.weight(1f)) { vm.setQuick(ProfileListViewModel.Quick.SITES, it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleTile("Reels & Shorts", reels, Modifier.weight(1f)) { vm.setQuick(ProfileListViewModel.Quick.REELS, it) }
        ToggleTile("Adult sites", adult, Modifier.weight(1f)) { vm.setQuick(ProfileListViewModel.Quick.ADULT, it) }
    }
}

@Composable
private fun ToggleTile(label: String, checked: Boolean?, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val on = checked == true
    Card(
        modifier = modifier,
        colors = if (on) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = onChange)
            }
            Text(
                if (on) "On" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockerOffBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "Blocker setup isn't finished. Tap to fix.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPause: (Long) -> Unit,
    onResume: () -> Unit,
) {
    val paused = profile.pausedUntil?.isAfter(java.time.LocalDateTime.now()) == true
    var showPause by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${profile.summary()} · ${if (profile.wholePhone) "whole phone" else "${profile.packages.size} apps"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onToggle)
            }
            if (profile.enabled) {
                if (paused) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Paused until ${formatMinute(profile.pausedUntil!!.hour * 60 + profile.pausedUntil.minute)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onResume) { Text("Resume") }
                    }
                } else {
                    TextButton(onClick = { showPause = true }) { Text("Pause") }
                }
            }
        }
    }
    if (showPause) {
        PauseDialog(
            onDismiss = { showPause = false },
            onPick = {
                onPause(it)
                showPause = false
            },
        )
    }
}

@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val options = listOf(5L to "5 min", 15L to "15 min", 30L to "30 min", 60L to "1 hour", 480L to "8 hours")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause for") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    TextButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
