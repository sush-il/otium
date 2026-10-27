package dev.sushil.otium.ui.common

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
import dev.sushil.otium.data.SettingsStore
import dev.sushil.otium.domain.PauseWindow
import dev.sushil.otium.ui.lock.PasswordDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

fun appPauseKey(pkg: String) = "app:$pkg"
fun sitePauseKey(host: String) = "site:$host"

/**
 * Pause / resume all limits on one app or site. Limits stay saved; the engine just skips
 * paused ones. Pausing loosens blocking, so it follows Lock/Strict mode; resuming is free.
 * A pause ends by itself: after 30 minutes in Lock mode (the number is typed, Lock stays on),
 * or at the next day reset in Normal mode.
 */
class LimitPause(private val settings: SettingsStore, private val guard: EditGuard, private val scope: CoroutineScope) {
    val paused: StateFlow<Map<String, Long>> =
        settings.pausedLimits.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var passwordNeededFor by mutableStateOf<String?>(null)

    fun toggle(key: String) {
        scope.launch {
            val current = settings.pausedLimits.first()
            if (key in current) {
                settings.setPausedLimits(current - key)
                notice = "Blocking back on"
                return@launch
            }
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> pause(key)
                EditCheck.NeedsPassword -> passwordNeededFor = key
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    /** The number was typed for [passwordNeededFor]; pause it without asking the guard again. */
    fun onPasswordAccepted() {
        val key = passwordNeededFor ?: return
        passwordNeededFor = null
        scope.launch { pause(key) }
    }

    private suspend fun pause(key: String) {
        val now = LocalDateTime.now()
        val until = if (guard.isLockMode()) {
            now.plusMinutes(PauseWindow.LOCK_MODE_MINUTES)
        } else {
            PauseWindow.nextReset(now, settings.dayResetHour.first())
        }
        settings.setPausedLimits(settings.pausedLimits.first() + (key to until.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        notice = "Blocking paused until " + (if (until.toLocalDate() == now.toLocalDate()) "" else "tomorrow ") + "%02d:%02d".format(until.hour, until.minute)
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
        PasswordDialog(
            action = "pause blocking",
            onUnlocked = { pause.onPasswordAccepted() },
            onDismiss = { pause.passwordNeededFor = null },
            switchToNormal = false,
            note = "This only lasts ${PauseWindow.LOCK_MODE_MINUTES} minutes. To keep it off longer, switch to Normal mode.",
        )
    }
}

/** Shows the current state: coloured closed lock = limits enforced; greyed open lock = paused. */
@Composable
fun PauseButton(paused: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = if (paused) LockOpenFilled else Icons.Filled.Lock,
            contentDescription = if (paused) "Resume blocking" else "Pause blocking",
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
