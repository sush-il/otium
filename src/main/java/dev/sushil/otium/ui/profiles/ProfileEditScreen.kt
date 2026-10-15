package dev.sushil.otium.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.domain.model.RuleType
import dev.sushil.otium.ui.apps.AppPicker
import dev.sushil.otium.ui.common.AppIcon
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.lock.PasswordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(profileId: Long, onClose: () -> Unit) {
    val container = appContainer()
    val vm: ProfileEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileEditViewModel(profileId, container.profileRepository, container.installedAppsRepository, container.editGuard)
            }
        },
    )
    var pickingApps by rememberSaveable { mutableStateOf(false) }

    if (vm.passwordNeededFor != null) {
        PasswordDialog(
            action = "loosen this profile",
            onUnlocked = { vm.onPasswordAccepted() },
            onDismiss = vm::onPasswordDismissed,
        )
    }

    // The app picker is part of this screen so the selection lives in one ViewModel
    if (pickingApps) {
        BackHandler { pickingApps = false }
        AppPicker(
            apps = vm.apps,
            selected = vm.selected,
            onToggle = vm::toggleApp,
            onDone = { pickingApps = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New profile" else "Edit profile") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Social media") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Rule", style = MaterialTheme.typography.titleMedium)
            RuleTypeSelector(vm.type) { vm.type = it }
            when (vm.type) {
                RuleType.ALWAYS -> Text("These apps are blocked whenever the profile is on.")
                RuleType.SCHEDULE -> ScheduleEditor(vm.schedule, onToggleDay = vm::toggleDay, onChange = { vm.schedule = it })
                RuleType.DAILY_LIMIT -> DailyLimitEditor(vm.dailyLimitMinutes) { vm.dailyLimitMinutes = it }
                RuleType.HOURLY_LIMIT -> MinutesLimitEditor("Each app, per clock hour", vm.hourlyLimitMinutes, max = 55) { vm.hourlyLimitMinutes = it }
                RuleType.LAUNCH_LIMIT -> LaunchLimitEditor(vm.launchLimit) { vm.launchLimit = it }
                RuleType.BLOCK_UNTIL -> BlockUntilEditor(vm.blockUntil) { vm.blockUntil = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Block the whole phone", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Every app except your excluded list (Phone, Settings and this app are always safe).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.wholePhone, onCheckedChange = { vm.wholePhone = it })
            }

            if (!vm.wholePhone) {
                Text("Apps (${vm.selected.size})", style = MaterialTheme.typography.titleMedium)
                val labels = vm.apps?.associate { it.packageName to it.label }.orEmpty()
                vm.selected.sortedBy { labels[it] ?: it }.forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(pkg, size = 32.dp)
                        Text(labels[pkg] ?: pkg)
                    }
                }
                OutlinedButton(onClick = { pickingApps = true }) { Text("Choose apps") }
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.save(onClose) },
                enabled = vm.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }

            if (!vm.isNew) {
                TextButton(onClick = { vm.delete(onClose) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete profile", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
