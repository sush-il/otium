package com.focusblock.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusblock.util.Permissions

/** Snapshot of every permission, re-read each time the user comes back from Settings. */
private data class PermissionState(
    val accessibility: Boolean,
    val usageAccess: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val battery: Boolean,
) {
    val required get() = accessibility && usageAccess

    companion object {
        fun read(context: Context) = PermissionState(
            accessibility = Permissions.isAccessibilityEnabled(context),
            usageAccess = Permissions.hasUsageAccess(context),
            notifications = Permissions.hasNotificationPermission(context),
            overlay = Permissions.canDrawOverlays(context),
            battery = Permissions.isIgnoringBatteryOptimizations(context),
        )
    }
}

/** Step-by-step checklist for the permissions the blocker needs. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionState.read(context)) }

    // The user grants permissions in system Settings, so re-check whenever we come back
    LifecycleResumeEffect(Unit) {
        state = PermissionState.read(context)
        onPauseOrDispose { }
    }

    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = PermissionState.read(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Set up blocking", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Label("Required")
        if (Permissions.needsRestrictedSettingsStep) {
            Step(
                glyph = "🔓",
                title = "Allow restricted settings",
                body = "App info → ⋮ menu → \"Allow restricted settings\".",
                done = state.accessibility,
                action = "Open App info",
                onAction = { context.startActivity(Permissions.appDetailsIntent(context)) },
            )
        }
        Step(
            glyph = "🛡️",
            title = "Turn on the blocker",
            body = "Accessibility → Focus Block → On. Reacts instantly when an app opens.",
            done = state.accessibility,
            action = "Open Accessibility",
            onAction = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
        )
        Step(
            glyph = "⏱️",
            title = "Usage access",
            body = "Needed to count app time for limits.",
            done = state.usageAccess,
            action = "Open Usage access",
            onAction = { context.startActivity(Permissions.usageAccessIntent()) },
        )

        Label("Recommended")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Step(
                glyph = "🔔",
                title = "Alerts",
                body = "Warnings at 10, 5 and 1 minute left.",
                done = state.notifications,
                action = "Allow alerts",
                onAction = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        Step(
            glyph = "🪟",
            title = "Display over other apps",
            body = "Shows the floating time-left arrow.",
            done = state.overlay,
            action = "Open setting",
            onAction = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        )
        Step(
            glyph = "🔋",
            title = "Don't optimise battery",
            body = "Stops some phones killing the app in the background.",
            done = state.battery,
            action = "Open battery settings",
            onAction = { context.startActivity(Permissions.batterySettingsIntent()) },
        )

        Button(
            onClick = onDone,
            enabled = state.required,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(if (state.required) "Continue" else "Finish the required steps") }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Step(glyph: String, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(glyph, fontSize = 20.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!done) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(action) }
                } else {
                    Text("Done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}
