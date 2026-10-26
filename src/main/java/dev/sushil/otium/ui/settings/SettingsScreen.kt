package dev.sushil.otium.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import dev.sushil.otium.data.TimerDisplay
import dev.sushil.otium.ui.common.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenSetup: () -> Unit,
    onOpenExcludedApps: () -> Unit,
    onOpenWebContent: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenFeeds: () -> Unit,
) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.settingsStore) } },
    )
    val display by vm.timerDisplay.collectAsStateWithLifecycle()
    val alerts by vm.lowTimeAlerts.collectAsStateWithLifecycle()
    val resetHour by vm.dayResetHour.collectAsStateWithLifecycle()
    val showSystem by vm.showSystemApps.collectAsStateWithLifecycle()
    val mode by container.modeStore.state.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = container.backupManager
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { backup.exportTo(uri) }
                .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Backup failed", Toast.LENGTH_LONG).show() }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { backup.importFrom(uri) }
                .onSuccess {
                    val skipped = if (it.appsSkipped > 0) ", ${it.appsSkipped} not installed (skipped)" else ""
                    Toast.makeText(context, "Restored ${it.appsRestored} apps, ${it.sitesRestored} sites$skipped", Toast.LENGTH_LONG).show()
                }
                .onFailure { Toast.makeText(context, "Restore failed", Toast.LENGTH_LONG).show() }
        }
    }
    val strictActive = mode?.isStrictActive(LocalDateTime.now(), System.currentTimeMillis()) == true
    var pickingReset by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("Blocking") }
            item {
                SettingsGroup {
                    NavRow("🚫", "Excluded apps", "Never blocked by whole-phone", onOpenExcludedApps)
                    Divider()
                    NavRow("🌐", "Websites", "Per-site limits", onOpenWebContent)
                    Divider()
                    NavRow("🔤", "Keywords", "Block words in pages & searches", onOpenKeywords)
                    Divider()
                    NavRow("🎬", "Feeds & content", "Shorts, Reels, adult", onOpenFeeds)
                    Divider()
                    SwitchRow("Show system apps", "In the app picker", showSystem == true, vm::setShowSystemApps)
                }
            }

            item { SectionLabel("Timer & alerts") }
            item {
                SettingsGroup {
                    SwitchRow("On-screen timer", "Floating time-left arrow", display == TimerDisplay.FLOATING) {
                        vm.setTimerDisplay(if (it) TimerDisplay.FLOATING else TimerDisplay.HIDDEN)
                    }
                    Divider()
                    SwitchRow("Low-time alerts", "At 10, 5 and 1 min left", alerts == true, vm::setLowTimeAlerts)
                    Divider()
                    NavRow("🕛", "Day reset time", "%02d:00".format(resetHour ?: 0), { pickingReset = true })
                }
            }

            item { SectionLabel("Data") }
            item {
                SettingsGroup {
                    NavRow("💾", "Back up", "Save profiles, app & site limits and settings to a file") { exporter.launch("otium-backup.json") }
                    Divider()
                    NavRow(
                        "↩️",
                        "Restore",
                        if (strictActive) "Off while Strict mode is on" else "Load from a backup file",
                        { if (!strictActive) confirmRestore = true },
                    )
                }
            }

            item { SectionLabel("Setup") }
            item {
                SettingsGroup {
                    NavRow("🔐", "Permissions & setup", null, onOpenSetup)
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("Apps and sites in the backup will have their current limits overwritten. Profiles are replaced. Other apps are left as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importer.launch(arrayOf("application/json"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
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
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun NavRow(glyph: String, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
