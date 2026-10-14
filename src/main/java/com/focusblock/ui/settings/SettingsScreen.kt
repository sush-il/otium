package com.focusblock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import java.time.LocalDateTime
import kotlinx.coroutines.launch
import com.focusblock.domain.model.Mode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.data.TimerDisplay
import com.focusblock.ui.common.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenExcludedApps: () -> Unit,
    onOpenWebContent: () -> Unit,
    onOpenFeeds: () -> Unit,
) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settingsStore) } },
    )
    val display by vm.timerDisplay.collectAsStateWithLifecycle()
    val alerts by vm.lowTimeAlerts.collectAsStateWithLifecycle()
    val resetHour by vm.dayResetHour.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Time-left display", style = MaterialTheme.typography.titleMedium)
            Text(
                "Shown while an app with a daily limit (or a schedule starting within the hour) is open.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                TimerDisplay.FLOATING to "Floating arrow on screen",
                TimerDisplay.HIDDEN to "Hidden",
            ).forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { vm.setTimerDisplay(value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = display == value, onClick = { vm.setTimerDisplay(value) })
                    Text(label)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Low-time alerts", style = MaterialTheme.typography.titleMedium)
                    Text("Notify at 10, 5 and 1 minute left.", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = alerts == true, onCheckedChange = vm::setLowTimeAlerts)
            }

            var pickingReset by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Day reset time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "When daily limits and launch counts reset. Default midnight.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = { pickingReset = true }) {
                    Text("%02d:00".format(resetHour ?: 0))
                }
            }
            if (pickingReset) {
                val state = rememberTimePickerState(initialHour = resetHour ?: 0, initialMinute = 0, is24Hour = true)
                AlertDialog(
                    onDismissRequest = { pickingReset = false },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.setDayResetHour(state.hour)
                            pickingReset = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { pickingReset = false }) { Text("Cancel") } },
                    text = { TimePicker(state) },
                )
            }

            Text("Whole-phone blocking", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Apps that a whole-phone profile should never block.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenExcludedApps) { Text("Excluded apps") }

            Text("Websites & keywords", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Block sites and search keywords in browsers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenWebContent) { Text("Websites & keywords") }

            Text("Feeds & content", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Block Shorts, Reels and adult sites.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenFeeds) { Text("Feeds & content") }

            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val mode by container.modeStore.state.collectAsStateWithLifecycle(initialValue = null)
            val backup = container.backupManager
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    runCatching { backup.exportTo(uri) }
                        .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
            val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    runCatching { backup.importFrom(uri) }
                        .onSuccess { Toast.makeText(context, "Restored", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
            val strictActive = mode?.isStrictActive(LocalDateTime.now(), System.currentTimeMillis()) == true

            Text("Backup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Save your profiles and settings to a file, or restore them. The password and mode are never included.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exporter.launch("focusblock-backup.json") }) { Text("Back up") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/json")) }, enabled = !strictActive) {
                    Text("Restore")
                }
            }
            if (strictActive) {
                Text(
                    "Restore is off while Strict mode is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 8.dp)) {
                Text("Permissions & setup")
            }
        }
    }
}
