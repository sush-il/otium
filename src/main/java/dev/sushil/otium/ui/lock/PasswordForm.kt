package dev.sushil.otium.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sushil.otium.domain.NumberChallenge
import dev.sushil.otium.domain.NumberChallenges
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.ui.common.appContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Shows a challenge: what the number is (title + short description side by side) and all its digits in groups of five. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeView(challenge: NumberChallenge) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(challenge.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            challenge.digits.chunked(5).forEach { group ->
                Text(group, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Number field that only takes digits. */
@Composable
fun DigitsField(value: String, onChange: (String) -> Unit, label: String, maxLength: Int, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Type a fresh number shown on screen. A wrong answer gives a new number, so it can't be
 * retried from memory. [extra] sits between the field and the button; [onSolved] runs on success.
 */
@Composable
fun ChallengeGate(
    prompt: String,
    buttonLabel: String,
    onSolved: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val modeStore = appContainer().modeStore
    val length by modeStore.state.map { it.lockLength ?: NumberChallenges.MIN_LENGTH }.collectAsState(initial = NumberChallenges.MIN_LENGTH)
    var attempt by remember { mutableIntStateOf(0) }
    val challenge = remember(length, attempt) { NumberChallenges.generate(length) }
    var input by remember(attempt) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(prompt, style = MaterialTheme.typography.bodyLarge)
        ChallengeView(challenge)
        DigitsField(
            input,
            {
                // A soft tick per digit typed (not on delete)
                if (it.length > input.length) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                input = it
                error = null
            },
            "Type the number", challenge.digits.length, error,
        )
        extra()
        Button(
            onClick = {
                if (input == challenge.digits) {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSolved()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    error = "Not quite. Here's a new number."
                    attempt++
                }
            },
            enabled = input.length == challenge.digits.length,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(buttonLabel) }
    }
}

/**
 * Lock mode's "password": a correct number switches to Normal mode and then runs
 * [onUnlocked], which does the [action]. Lock stays off until the user turns it on again.
 * With [switchToNormal] off, Lock stays on and the number only allows this one [action];
 * [note] is shown above the button.
 */
@Composable
fun PasswordForm(
    action: String,
    onUnlocked: () -> Unit,
    switchToNormal: Boolean = true,
    note: String? = null,
) {
    val modeStore = appContainer().modeStore
    val scope = rememberCoroutineScope()

    ChallengeGate(
        prompt = if (switchToNormal) "Type the number to switch to Normal mode and $action." else "Type the number to $action.",
        buttonLabel = "Unlock",
        onSolved = {
            scope.launch {
                modeStore.update {
                    if (switchToNormal && it.mode == Mode.LOCK) it.copy(mode = Mode.NORMAL) else it
                }
                onUnlocked()
            }
        },
        extra = {
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
fun PasswordDialog(
    action: String,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    switchToNormal: Boolean = true,
    note: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock mode is on") },
        text = { PasswordForm(action, onUnlocked, switchToNormal, note) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
