package dev.sushil.otium.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.lock.ChallengeGate
import dev.sushil.otium.util.formatCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val LENGTHS_MIN = listOf(15, 30, 45, 60)

/**
 * A quick-start row for focus sessions (everything blocked bar the essentials): pick a length,
 * then tap Start. While one runs the row shows the countdown; ending early means typing the
 * Lock-style number.
 */
@Composable
fun FocusSessionRow() {
    val modeStore = appContainer().modeStore
    val until by modeStore.state.map { it.sessionUntil }.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var picked by remember { mutableIntStateOf(30) }
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Badge("🎯")
            if (running) {
                Column(Modifier.weight(1f)) {
                    Text(formatCountdown(remainingMs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Focus session running", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { ending = true }) { Text("End") }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column {
                        Text("Focus session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Blocks everything but the essentials", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Four equal chips fill the column; Start spans the same width underneath
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LENGTHS_MIN.forEach { minutes ->
                            TimeChip("${minutes}m", selected = picked == minutes, modifier = Modifier.weight(1f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                picked = minutes
                            }
                        }
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            scope.launch { modeStore.update { it.copy(sessionUntil = System.currentTimeMillis() + picked * 60_000L) } }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) { Text("Start") }
                }
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

/** A small selectable pill; the caller gives it a weight so a row of them fills the width evenly. */
@Composable
private fun TimeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 5.dp))
    }
}
