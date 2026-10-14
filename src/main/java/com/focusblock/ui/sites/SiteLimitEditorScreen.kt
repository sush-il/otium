package com.focusblock.ui.sites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.domain.model.RuleType
import com.focusblock.ui.common.BackButton
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog
import com.focusblock.ui.profiles.BlockUntilEditor
import com.focusblock.ui.profiles.DailyLimitEditor
import com.focusblock.ui.profiles.LaunchLimitEditor
import com.focusblock.ui.profiles.MinutesLimitEditor
import com.focusblock.ui.profiles.RuleTypeSelector
import com.focusblock.ui.profiles.ScheduleEditor
import com.focusblock.ui.profiles.ruleTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteLimitEditorScreen(host: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm: SiteLimitEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { SiteLimitEditorViewModel(host, container.siteLimitRepository, container.editGuard) } },
    )
    val rules by vm.rules.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    vm.error?.let { message ->
        AlertDialog(
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
            TopAppBar(title = { Text(host) }, navigationIcon = { BackButton(onClick = onBack) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showSheet = true }, text = { Text("Add a limit") }, icon = { Text("＋") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    rules.isEmpty() -> "Not blocked"
                    rules.size == 1 -> "1 limit"
                    else -> "${rules.size} limits — blocked if any applies"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Time limits for sites are estimated from the browser, so they're approximate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (rules.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No limits yet", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = vm::blockAlways) { Text("Block completely") }
                    }
                }
            } else {
                Text("Active limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                rules.forEachIndexed { index, rule ->
                    Card(Modifier.fillMaxWidth().clickable { vm.loadForEdit(rule); showSheet = true }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
                            Column(Modifier.weight(1f)) {
                                Text(ruleTypeLabel(rule.type), style = MaterialTheme.typography.titleSmall)
                                Text(rule.summary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { vm.remove(rule.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val editing = rules.any { it.type == vm.type }
                Text(if (editing) "Edit limit" else "Add a limit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                RuleTypeSelector(vm.type) { vm.type = it }
                when (vm.type) {
                    RuleType.ALWAYS -> Text("Blocks this site whenever it's open.")
                    RuleType.DAILY_LIMIT -> DailyLimitEditor(vm.dailyLimitMinutes) { vm.dailyLimitMinutes = it }
                    RuleType.HOURLY_LIMIT -> MinutesLimitEditor("Per clock hour", vm.hourlyLimitMinutes, max = 55) { vm.hourlyLimitMinutes = it }
                    RuleType.LAUNCH_LIMIT -> LaunchLimitEditor(vm.launchLimit) { vm.launchLimit = it }
                    RuleType.SCHEDULE -> ScheduleEditor(
                        vm.schedule,
                        onToggleDay = { i -> vm.schedule = vm.schedule.copy(daysMask = vm.schedule.daysMask xor (1 shl i)) },
                        onChange = { vm.schedule = it },
                    )
                    RuleType.BLOCK_UNTIL -> BlockUntilEditor(vm.blockUntil) { vm.blockUntil = it }
                }
                Button(
                    onClick = {
                        vm.addCurrent()
                        showSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (editing) "Save" else "Add") }
            }
        }
    }
}
