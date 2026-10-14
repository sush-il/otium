package dev.sushil.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.sushil.focusblock.FocusBlockApp

/**
 * Reports window changes to [BlockEngine] the moment they happen, and lends the
 * engine its "go home" action. The rules themselves live in the engine.
 *
 * Android keeps an enabled accessibility service alive and restarts it after reboot.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val engine: BlockEngine
        get() = (application as FocusBlockApp).container.blockEngine

    override fun onServiceConnected() {
        engine.attachAccessibility(this)
        // We're running, so make sure the polling backup and notification are too
        TrackingService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        engine.onWindowEvent(pkg, event.className?.toString())
        engine.onForeground(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        engine.detachAccessibility(this)
        super.onDestroy()
    }
}
