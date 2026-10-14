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
        factory = viewModelFactory { initializer { ProfileListViewModel(container.profileRepository, container.editGuard) } },
    )
    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog(
            message = "Lock mode: turning a profile off needs your password.",
            onUnlocked = { vm.onPasswordAccepted() },
            onDismiss = vm::dismissPassword,
        )
    }
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var blockerOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        blockerOn = Permissions.hasRequired(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Block") },
                actions = {
                    TextButton(onClick = onOpenModes) { Text("Modes") }
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewProfile) { Text("New profile") }
        },
    ) { padding ->
        val list = profiles
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!blockerOn) item { BlockerOffBanner(onOpenSetup) }
                if (list.isEmpty()) item {
                    Text(
                        "No profiles yet. Tap \"New profile\" to choose apps to block.",
                        modifier = Modifier.padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                items(list, key = { it.id }) { profile ->
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
                        "${profile.summary()} · ${profile.packages.size} apps",
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
