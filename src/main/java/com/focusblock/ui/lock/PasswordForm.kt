package com.focusblock.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusblock.domain.NumberChallenge
import com.focusblock.domain.NumberChallenges
import com.focusblock.domain.model.Mode
import com.focusblock.service.ModeKeeper
import com.focusblock.ui.common.appContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Shows a challenge: what the number is, all its digits in groups of five, and where it starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeView(challenge: NumberChallenge) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(challenge.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            challenge.digits.chunked(5).forEach { group ->
                Text(group, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(challenge.where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
 * Lock mode's "password": type a fresh number shown on screen. A wrong answer gives a new
 * number, so it can't be retried from memory. A correct one switches to Normal mode (optionally
 * switching Lock back on after an hour) and then runs [onUnlocked], which does the [action].
 */
@Composable
fun PasswordForm(
    action: String,
    onUnlocked: () -> Unit,
    onForgot: (() -> Unit)?,
) {
    val modeStore = appContainer().modeStore
    val length by modeStore.state.map { it.lockLength ?: NumberChallenges.MIN_LENGTH }.collectAsState(initial = NumberChallenges.MIN_LENGTH)
    var attempt by remember { mutableIntStateOf(0) }
    val challenge = remember(length, attempt) { NumberChallenges.generate(length) }
    var input by remember(attempt) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var relock by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Type the number to switch to Normal mode and $action.", style = MaterialTheme.typography.bodyLarge)
        ChallengeView(challenge)
        DigitsField(input, { input = it; error = null }, "Type the number", challenge.digits.length, error)
        Row(Modifier.fillMaxWidth().clickable { relock = !relock }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = relock, onCheckedChange = { relock = it })
            Text("Lock again in 1 hour", style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = {
                if (input == challenge.digits) {
                    scope.launch {
                        modeStore.update {
                            if (it.mode == Mode.LOCK) {
                                it.copy(mode = Mode.NORMAL, relockAt = if (relock) System.currentTimeMillis() + ModeKeeper.RELOCK_MS else null)
                            } else {
                                it
                            }
                        }
                        onUnlocked()
                    }
                } else {
                    error = "Not quite. Here's a new number."
                    attempt++
                }
            },
            enabled = input.length == challenge.digits.length,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock") }
        if (onForgot != null) {
            TextButton(onClick = onForgot) { Text("Need out early? Emergency unlock") }
        }
    }
}

@Composable
fun PasswordDialog(action: String, onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock mode is on") },
        text = { PasswordForm(action, onUnlocked, onForgot = null) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
