package dev.sushil.focusblock.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import dev.sushil.focusblock.service.TrackingService
import dev.sushil.focusblock.util.Permissions

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

    // Notifications are the one runtime permission: a system dialog, not a Settings page
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        state = PermissionState.read(context)
        // The service was started without its notification showing; start again to show it
        TrackingService.start(context)
    }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Set up blocking", style = MaterialTheme.typography.headlineMedium)

        Text("Required", style = MaterialTheme.typography.titleMedium)
        if (Permissions.needsRestrictedSettingsStep) {
            Step(
                title = "Allow restricted settings",
                body = "Android blocks accessibility for apps not installed from the Play Store. " +
                    "Open App info, tap the ⋮ menu (top right) and choose \"Allow restricted settings\". " +
                    "If you don't see the option, try the next step once first, then come back.",
                done = state.accessibility,
                action = "Open App info",
                onAction = { context.startActivity(Permissions.appDetailsIntent(context)) },
            )
        }
        Step(
            title = "Turn on the blocker",
            body = "In Accessibility, open \"Focus Block app blocker\" and switch it on. This reacts instantly when an app opens.",
            done = state.accessibility,
            action = "Open Accessibility settings",
            onAction = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
        )
        Step(
            title = "Usage access",
            body = "Lets Focus Block check which app is open several times a second, and count time for daily limits. " +
                "Without it, apps with a daily limit stay blocked.",
            done = state.usageAccess,
            action = "Open Usage access",
            onAction = { context.startActivity(Permissions.usageAccessIntent()) },
        )

        Text("Recommended", style = MaterialTheme.typography.titleMedium)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Step(
                title = "Notifications",
                body = "Shows the permanent \"Focus Block is active\" notification so you can see it's running.",
                done = state.notifications,
                action = "Allow notifications",
                onAction = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        Step(
            title = "Display over other apps",
            body = "Lets the block screen appear even if Android pauses the accessibility service, and shows the on-screen timer.",
            done = state.overlay,
            action = "Open setting",
            onAction = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
        )
        Step(
            title = "Battery: don't optimise",
            body = "Some phones (Samsung, Xiaomi, Oppo…) stop background apps to save battery. " +
                "Find Focus Block in the list and choose \"Don't optimise\" / \"Unrestricted\".",
            done = state.battery,
            action = "Open battery settings",
            onAction = { context.startActivity(Permissions.batterySettingsIntent()) },
        )

        Button(onClick = onDone, enabled = state.required, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (done) "$title ✓" else title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (!done) OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}
