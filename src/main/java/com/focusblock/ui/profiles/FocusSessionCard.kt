package com.focusblock.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.ChallengeGate
import com.focusblock.util.formatCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val LENGTHS_MIN = listOf(15, 25, 45, 60, 90)

/**
 * Starts a focus session (everything blocked bar the essentials, for a chosen time) and shows
 * the countdown while one runs. Ending one early means typing the Lock-style number.
 */
@Composable
fun FocusSessionCard() {
    val modeStore = appContainer().modeStore
    val until by modeStore.state.map { it.sessionUntil }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var picked by remember { mutableStateOf(25) }
    var ending by remember { mutableStateOf(false) }

    // Re-read the clock every second while a session runs, so the card flips back when it ends
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (running) {
                Text("Focus session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatCountdown(remainingMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Everything is blocked except your phone, keyboard and excluded apps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { ending = true }, modifier = Modifier.fillMaxWidth()) { Text("End early") }
            } else {
                Text("Focus session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Block the whole phone for a set time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LENGTHS_MIN.forEach { minutes ->
                        FilterChip(
                            selected = picked == minutes,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                picked = minutes
                            },
                            label = { Text("$minutes") },
                        )
                    }
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            modeStore.update { it.copy(sessionUntil = System.currentTimeMillis() + picked * 60_000L) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start $picked min") }
            }
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
