package com.focusblock.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.domain.model.RuleType
import com.focusblock.ui.common.AppIcon
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog
import com.focusblock.ui.profiles.BlockUntilEditor
import com.focusblock.ui.profiles.DailyLimitEditor
import com.focusblock.ui.profiles.LaunchLimitEditor
import com.focusblock.ui.profiles.MinutesLimitEditor
import com.focusblock.ui.profiles.RuleTypeSelector
import com.focusblock.ui.profiles.ScheduleEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitEditorScreen(packageName: String, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val vm: AppLimitEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { AppLimitEditorViewModel(packageName, container.appLimitRepository, container.editGuard) } },
    )
    val rules by vm.rules.collectAsStateWithLifecycle()
    val label = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    vm.error?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog("Lock mode: removing a limit needs your password.", { vm.onPasswordAccepted() }, vm::dismissPassword)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(packageName, size = 44.dp)
                Text(
                    if (rules.isEmpty()) "Not blocked yet" else "${rules.size} limit${if (rules.size == 1) "" else "s"} — blocked if any applies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            rules.forEach { rule ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(rule.summary(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { vm.remove(rule.id) }) { Text("Remove") }
                    }
                }
            }

            OutlinedButton(onClick = vm::blockAlways, modifier = Modifier.fillMaxWidth()) { Text("Block completely (always)") }

            Text("Add a limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            RuleTypeSelector(vm.type) { vm.type = it }
            when (vm.type) {
                RuleType.ALWAYS -> Text("Blocks this app whenever it's open.")
                RuleType.DAILY_LIMIT -> DailyLimitEditor(vm.dailyLimitMinutes) { vm.dailyLimitMinutes = it }
                RuleType.HOURLY_LIMIT -> MinutesLimitEditor("Per clock hour", vm.hourlyLimitMinutes, max = 55) { vm.hourlyLimitMinutes = it }
                RuleType.LAUNCH_LIMIT -> LaunchLimitEditor(vm.launchLimit) { vm.launchLimit = it }
                RuleType.SCHEDULE -> ScheduleEditor(vm.schedule, onToggleDay = { i -> vm.schedule = vm.schedule.copy(daysMask = vm.schedule.daysMask xor (1 shl i)) }, onChange = { vm.schedule = it })
                RuleType.BLOCK_UNTIL -> BlockUntilEditor(vm.blockUntil) { vm.blockUntil = it }
            }
            FilledTonalButton(onClick = vm::addCurrent, modifier = Modifier.fillMaxWidth()) { Text("Add limit") }
        }
    }
}
