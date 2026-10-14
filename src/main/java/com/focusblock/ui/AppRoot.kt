package com.focusblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusblock.service.UnlockGrants
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordForm
import com.focusblock.ui.modes.EmergencyScreen

/** Decides what the app shows first: the emergency unlock, the Lock-mode password, or the app. */
@Composable
fun AppRoot(openEmergency: Boolean, onEmergencyClosed: () -> Unit) {
    val container = appContainer()
    val mode by container.modeStore.state.collectAsStateWithLifecycle(initialValue = null)
    var granted by remember { mutableStateOf(container.unlockGrants.isGranted(UnlockGrants.Target.APP)) }
    var showEmergency by remember(openEmergency) { mutableStateOf(openEmergency) }

    // Coming back after the 5-minute grant ran out locks the app again
    LifecycleResumeEffect(Unit) {
        granted = container.unlockGrants.isGranted(UnlockGrants.Target.APP)
        onPauseOrDispose { }
    }

    val state = mode ?: return // first DataStore read, a few ms
    val locked = state.isLock && state.lock.lockApp && state.passwordHash != null && !granted

    when {
        showEmergency -> EmergencyScreen(onClose = {
            showEmergency = false
            onEmergencyClosed()
        })
        locked -> Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text("Focus Block is locked", style = MaterialTheme.typography.headlineMedium)
                PasswordForm(
                    message = "Enter your Lock mode password.",
                    onUnlocked = {
                        container.unlockGrants.grant(UnlockGrants.Target.APP)
                        granted = true
                    },
                    onForgot = { showEmergency = true },
                )
            }
        }
        else -> AppNavHost()
    }
}
