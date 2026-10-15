package dev.sushil.otium.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.lock.ChallengeGate
import dev.sushil.otium.util.formatCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val LENGTHS_MIN = listOf(15, 25, 45, 60)

/**
 * A quick-start row for focus sessions: tap a length to start (everything blocked bar the
 * essentials). While one runs the row shows the countdown; ending early means typing the
 * Lock-style number.
 */
@Composable
fun FocusSessionRow() {
    val modeStore = appContainer().modeStore
    val until by modeStore.state.map { it.sessionUntil }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var ending by remember { mutableStateOf(false) }

    // Re-read the clock every second while a session runs, so the row flips back when it ends
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (until != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remainingMs = until?.let { it - now } ?: 0
    val running = remainingMs > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Badge("🎯")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running) {
                    Text(formatCountdown(remainingMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Focus session running", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Focus session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LENGTHS_MIN.forEach { minutes ->
                            AssistChip(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scope.launch { modeStore.update { it.copy(sessionUntil = System.currentTimeMillis() + minutes * 60_000L) } }
                                },
                                label = { Text("$minutes") },
                            )
                        }
                        Text("min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            if (running) TextButton(onClick = { ending = true }) { Text("End") }
        }
    }

    if (ending) {
        AlertDialog(
            onDismissRequest = { ending = false },
            title = { Text("End the focus session?") },
            text = {
                ChallengeGate(
                    prompt = "Type the number to end it now.",
                    buttonLabel = "End session",
                    onSolved = {
                        scope.launch { modeStore.update { it.copy(sessionUntil = null) } }
                        ending = false
                    },
                )
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { ending = false }) { Text("Keep going") } },
        )
    }
}
