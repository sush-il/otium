package dev.sushil.focusblock.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.sushil.focusblock.util.Permissions

/** Step-by-step checklist for the permissions the blocker needs. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(Permissions.isAccessibilityEnabled(context)) }

    // The user grants permissions in system Settings, so re-check whenever we come back
    LifecycleResumeEffect(Unit) {
        accessibilityOn = Permissions.isAccessibilityEnabled(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Set up blocking", style = MaterialTheme.typography.headlineMedium)

        if (Permissions.needsRestrictedSettingsStep) {
            Step(
                title = "1. Allow restricted settings",
                body = "Android blocks accessibility for apps not installed from the Play Store. " +
                    "Open App info, tap the ⋮ menu (top right) and choose \"Allow restricted settings\". " +
                    "If you don't see the option, open step 2 once first, then come back.",
                done = accessibilityOn,
                action = "Open App info",
                intent = Permissions.appDetailsIntent(context),
            )
        }
        Step(
            title = if (Permissions.needsRestrictedSettingsStep) "2. Turn on the blocker" else "1. Turn on the blocker",
            body = "In Accessibility, open \"Focus Block app blocker\" and switch it on.",
            done = accessibilityOn,
            action = "Open Accessibility settings",
            intent = Permissions.accessibilitySettingsIntent(),
        )

        Button(onClick = onDone, enabled = accessibilityOn, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, action: String, intent: Intent) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (done) "$title ✓" else title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!done) OutlinedButton(onClick = { context.startActivity(intent) }) { Text(action) }
        }
    }
}
