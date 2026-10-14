package com.focusblock.service

import android.content.Context
import android.content.pm.PackageManager
import com.focusblock.data.SettingsStore
import com.focusblock.data.TimerDisplay
import com.focusblock.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the floating time-left arrow and the low-time alerts from the engine's countdown.
 * Lives in the app process (kept alive by the accessibility service), so no foreground
 * service — and therefore no permanent notification — is needed.
 */
class BlockOverlayController(
    private val context: Context,
    private val engine: BlockEngine,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val overlay = TimerOverlay(context)
    private val alertOverlay = AlertOverlay(context)
    private val alerts = LowTimeAlerts(context)

    fun start() {
        scope.launch {
            combine(engine.countdown, settings.timerDisplay, settings.lowTimeAlerts, ::Triple)
                .collectLatest { (countdown, display, alertsOn) -> show(countdown, display, alertsOn) }
        }
    }

    private suspend fun show(countdown: Countdown?, display: TimerDisplay, alertsOn: Boolean) {
        if (countdown == null) {
            // Grace period so a re-check or sub-window change doesn't make the arrow flicker
            delay(GRACE_HIDE_MS)
            overlay.hide()
            return
        }
        val label = appLabel(countdown.pkg)
        val wantOverlay = display != TimerDisplay.HIDDEN && Permissions.canDrawOverlays(context)
        if (!wantOverlay) overlay.hide()

        coroutineScope {
            if (alertsOn) launch {
                for (minutes in alerts.pendingThresholds(countdown.pkg, countdown.remainingMs())) {
                    delay(countdown.remainingMs() - minutes * 60_000L)
                    if (!alerts.markIfNew(countdown.pkg, minutes)) continue
                    // Prefer a big on-screen flash; fall back to a notification without overlay access
                    if (Permissions.canDrawOverlays(context)) {
                        alertOverlay.flash("$minutes min left")
                    } else {
                        alerts.send(countdown.pkg, label, minutes)
                    }
                }
            }
            if (wantOverlay) launch {
                while (true) {
                    overlay.update(label, countdown.kind, countdown.remainingMs())
                    delay(1_000)
                }
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val GRACE_HIDE_MS = 2500L
    }
}
