package com.focusblock.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusblock.data.SettingsStore
import com.focusblock.ui.lock.PasswordDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun appPauseKey(pkg: String) = "app:$pkg"
fun sitePauseKey(host: String) = "site:$host"

/**
 * Pause / resume all limits on one app or site. Limits stay saved; the engine just skips
 * paused ones. Pausing loosens blocking, so it follows Lock/Strict mode; resuming is free.
 */
class LimitPause(private val settings: SettingsStore, private val guard: EditGuard, private val scope: CoroutineScope) {
    val paused: StateFlow<Set<String>> =
        settings.pausedLimits.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    var error by mutableStateOf<String?>(null)
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)

    fun toggle(key: String) {
        scope.launch {
            val current = settings.pausedLimits.first()
            if (key in current) {
                settings.setPausedLimits(current - key)
                return@launch
            }
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> settings.setPausedLimits(current + key)
                EditCheck.NeedsPassword -> passwordNeededFor = { toggle(key) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    fun onPasswordAccepted() {
        guard.grantProfiles()
        val retry = passwordNeededFor
        passwordNeededFor = null
        retry?.invoke()
    }
}

@Composable
fun PauseDialogs(pause: LimitPause) {
    pause.error?.let { message ->
        AlertDialog(
            onDismissRequest = { pause.error = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { pause.error = null }) { Text("OK") } },
        )
    }
    if (pause.passwordNeededFor != null) {
        PasswordDialog("Lock mode: pausing a limit needs your password.", { pause.onPasswordAccepted() }, { pause.passwordNeededFor = null })
    }
}

/** 🔒 = limits active, 🔓 = paused. */
@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Text(if (paused) "🔓" else "🔒", fontSize = 20.sp)
    }
}
