package com.focusblock.ui.apps

import com.focusblock.ui.common.BackButton
import androidx.compose.foundation.background
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.focusblock.ui.profiles.ruleTypeLabel
import kotlinx.coroutines.launch

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

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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
            TopAppBar(
                title = { Text(label) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showSheet = true }, text = { Text("Add a limit") }, icon = { Text("＋") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(packageName, size = 48.dp)
                Column {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            rules.isEmpty() -> "Not blocked"
                            rules.size == 1 -> "1 limit"
                            else -> "${rules.size} limits — blocked if any applies"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (rules.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No limits yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap \"Add a limit\" to limit this app, or block it completely.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = vm::blockAlways) { Text("Block completely") }
                    }
                }
            } else {
                Text("Active limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                rules.forEachIndexed { index, rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
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
                Text("Add a limit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                RuleTypeSelector(vm.type) { vm.type = it }
                when (vm.type) {
                    RuleType.ALWAYS -> Text("Blocks this app whenever it's open.")
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
                        scope.launch { snackbar.showSnackbar("Limit added") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add") }
            }
        }
    }
}
