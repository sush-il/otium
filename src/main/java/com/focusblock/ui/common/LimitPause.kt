package com.focusblock.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    var notice by mutableStateOf<String?>(null)
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)

    fun toggle(key: String) {
        scope.launch {
            val current = settings.pausedLimits.first()
            if (key in current) {
                settings.setPausedLimits(current - key)
                notice = "Limits resumed"
                return@launch
            }
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> {
                    settings.setPausedLimits(current + key)
                    notice = "Limits paused. Tap again to resume"
                }
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
    val context = LocalContext.current
    pause.notice?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            pause.notice = null
        }
    }
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

/** Shows the current state: coloured closed lock = limits enforced; greyed open lock = paused. */
@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = if (paused) LockOpenFilled else Icons.Filled.Lock,
            contentDescription = if (paused) "Resume limits" else "Pause limits",
            tint = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

// Material "lock open" (filled); not in the core icon set, so drawn here.
private val LockOpenFilled: ImageVector = materialIcon(name = "Filled.LockOpen") {
    materialPath {
        moveTo(12f, 17f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
        reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
        reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
        close()
        moveToRelative(6f, -9f)
        horizontalLineToRelative(-1f)
        verticalLineTo(6f)
        curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
        reflectiveCurveTo(7f, 3.24f, 7f, 6f)
        horizontalLineToRelative(1.9f)
        curveToRelative(0f, -1.71f, 1.39f, -3.1f, 3.1f, -3.1f)
        curveToRelative(1.71f, 0f, 3.1f, 1.39f, 3.1f, 3.1f)
        verticalLineToRelative(2f)
        horizontalLineTo(6f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(10f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(12f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(10f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
    }
}
