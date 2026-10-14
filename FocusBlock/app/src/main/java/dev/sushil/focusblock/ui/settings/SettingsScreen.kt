package dev.sushil.focusblock.ui.settings

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
import dev.sushil.focusblock.data.TimerDisplay
import dev.sushil.focusblock.ui.common.appContainer

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
                TimerDisplay.FLOATING to "Floating timer on screen",
                TimerDisplay.NOTIFICATION to "Countdown in the notification",
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

            OutlinedButton(onClick = onOpenSetup, modifier = Modifier.padding(top = 8.dp)) {
                Text("Permissions & setup")
            }
        }
    }
}
