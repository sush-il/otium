package dev.sushil.otium.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.domain.EmergencyUnlock
import dev.sushil.otium.ui.common.BackButton
import dev.sushil.otium.ui.common.describeStrictEnd
import dev.sushil.otium.ui.common.StrictEndPicker
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.common.rememberStrictEndState
import dev.sushil.otium.ui.lock.PasswordDialog
import dev.sushil.otium.ui.modes.formatHours
import dev.sushil.otium.ui.profiles.summary
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdultSitesScreen(onBack: () -> Unit, onOpenEmergency: () -> Unit) {
    val container = appContainer()
    val vm: AdultSitesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AdultSitesViewModel(container.settingsStore, container.modeStore, container.editGuard) }
        },
    )
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val mode by vm.modeState.collectAsStateWithLifecycle()

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog(
            action = "make this change",
            onUnlocked = { vm.onPasswordAccepted() },
            onDismiss = vm::dismissPassword,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adult sites") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val s = mode ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Block adult sites in browsers", Modifier.weight(1f))
                Switch(checked = blocked == true, onCheckedChange = vm::setBlocked)
            }
            Text(
                "Uses a built-in starter list of adult sites and keywords.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Strict for adult sites", style = MaterialTheme.typography.titleMedium)

            val now = LocalDateTime.now()
            val nowMs = System.currentTimeMillis()
            if (s.isAdultStrictActive(now, nowMs)) {
                Text(
                    when {
                        s.adultStrictUntil != null && nowMs < s.adultStrictUntil ->
                            "On ${describeStrictEnd(s.adultStrictUntil)}. Adult sites can't be unblocked before then."
                        else -> "On while this schedule runs: ${s.adultStrictSchedule?.summary().orEmpty()}. Adult sites can't be unblocked then."
                    },
                )
                Text(
                    "Need out early? The emergency unlock takes ${formatHours(EmergencyUnlock.waitFor(s.emergency.history, nowMs))} " +
                        "of phone-on time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenEmergency) { Text("Open emergency unlock") }
            } else {
                AdultStrictSetup(onStart = vm::startStrict)
            }
        }
    }
}

@Composable
private fun AdultStrictSetup(onStart: (until: Long?, schedule: dev.sushil.otium.domain.model.Schedule?) -> Unit) {
    val end = rememberStrictEndState()
    var separate by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }

    Text(
        "By default adult sites follow the app-wide mode (Normal, Lock or Strict). Nothing else to set up.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = separate, onCheckedChange = { separate = it })
        Text("Use Strict for adult sites only, whatever the app mode is", Modifier.weight(1f))
    }
    if (!separate) return

    StrictEndPicker(end, scheduleHint = "Strict is active only while this schedule runs, every week.")
    Text(
        "Once started, adult sites can't be unblocked until it ends. The only early exit is the emergency unlock, " +
            "which takes at least ${formatHours(EmergencyUnlock.BASE_WAIT_MS)} of phone-on time. Type I understand to continue.",
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedTextField(
        value = confirmText, onValueChange = { confirmText = it },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("I understand") },
    )
    Button(
        onClick = {
            onStart(end.untilMs(), end.scheduleOrNull())
            confirmText = ""
        },
        enabled = end.valid && confirmText.trim().equals("I understand", ignoreCase = true),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Start Strict for adult sites") }
}
