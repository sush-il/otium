package com.focusblock.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.focusblock.ui.common.appContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Password field + Unlock button. Accepts the password or the recovery code. */
@Composable
fun PasswordForm(
    message: String,
    onUnlocked: (PasswordResult) -> Unit,
    onForgot: (() -> Unit)?,
) {
    val modeStore = appContainer().modeStore
    val scope = rememberCoroutineScope()
    val pinMode by modeStore.state.map { it.pinMode }.collectAsState(initial = false)
    var useCode by remember { mutableStateOf(false) }
    val numeric = pinMode && !useCode
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (checking || input.isEmpty()) return
        checking = true
        scope.launch {
            val result = checkPassword(modeStore, input)
            checking = false
            if (result == PasswordResult.WRONG) {
                error = if (numeric) "Wrong PIN" else "Wrong password"
                input = ""
            } else {
                onUnlocked(result)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = input,
            onValueChange = { input = if (numeric) it.filter(Char::isDigit) else it; error = null },
            label = { Text(if (numeric) "PIN" else if (pinMode) "Recovery code" else "Password or recovery code") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::submit, enabled = !checking && input.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (checking) "Checking…" else "Unlock")
        }
        if (pinMode) {
            TextButton(onClick = { useCode = !useCode; input = ""; error = null }) {
                Text(if (useCode) "Use PIN" else "Use recovery code")
            }
        }
        if (onForgot != null) {
            TextButton(onClick = onForgot) { Text("Forgot password? Emergency unlock") }
        }
    }
}

@Composable
fun PasswordDialog(message: String, onUnlocked: (PasswordResult) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password needed") },
        text = { PasswordForm(message, onUnlocked, onForgot = null) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
