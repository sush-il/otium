package com.focusblock.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.focusblock.data.SettingsStore
import com.focusblock.data.TimerDisplay
import com.focusblock.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives the floating time-left arrow and the low-time alerts from the engine's countdown.
 * Lives in the app process (kept alive by the accessibility service), so no foreground
 * service — and therefore no permanent notification — is needed.
 *
 * A single steady render loop reads the latest countdown each tick, rather than restarting
 * on every flow emission, so the arrow doesn't flicker when the engine re-checks.
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

    @Volatile private var latest: Countdown? = null
    @Volatile private var display = TimerDisplay.FLOATING
    @Volatile private var alertsOn = true

    fun start() {
        scope.launch { engine.countdown.collect { latest = it } }
        scope.launch { settings.timerDisplay.collect { display = it } }
        scope.launch { settings.lowTimeAlerts.collect { alertsOn = it } }
        scope.launch { renderLoop() }
    }

    private suspend fun renderLoop() {
        var lastShownAt = 0L
        while (true) {
            val c = latest
            val canOverlay = display == TimerDisplay.FLOATING && Permissions.canDrawOverlays(context)
            when {
                c != null && canOverlay -> {
                    overlay.update(appLabel(c.pkg), c.kind, c.remainingMs())
                    lastShownAt = SystemClock.elapsedRealtime()
                    if (alertsOn) fireAlerts(c)
                }
                c != null -> { // overlay off or no permission: notification alerts only
                    overlay.hide()
                    if (alertsOn) fireAlerts(c)
                }
                else -> { // nothing to show; hide once the brief grace has passed
                    if (overlay.isShowing && SystemClock.elapsedRealtime() - lastShownAt > GRACE_HIDE_MS) overlay.hide()
                }
            }
            delay(TICK_MS)
        }
    }

    private fun fireAlerts(c: Countdown) {
        val remaining = c.remainingMs()
        // Mark every threshold already crossed, flash only the most urgent new one
        val newlyReached = THRESHOLDS_MIN.filter { remaining <= it * 60_000L && alerts.markIfNew(c.pkg, it) }
        if (newlyReached.isEmpty()) return
        val minutes = newlyReached.min()
        if (Permissions.canDrawOverlays(context)) {
            alertOverlay.flash("$minutes min left")
        } else {
            alerts.send(c.pkg, appLabel(c.pkg), minutes)
        }
    }

    private fun appLabel(pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val GRACE_HIDE_MS = 700L
        const val TICK_MS = 500L
        val THRESHOLDS_MIN = listOf(10, 5, 1)
    }
}
