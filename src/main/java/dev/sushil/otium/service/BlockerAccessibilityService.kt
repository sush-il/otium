package dev.sushil.otium.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.sushil.otium.OtiumApp

/**
 * Reports window changes to [BlockEngine] the moment they happen, lends the engine its
 * "go home" action, and reads the browser address bar to block sites and keywords.
 *
 * This is the app's only persistent component: Android keeps an enabled accessibility
 * service alive (and restarts it after reboot), so the app needs no foreground service
 * and therefore shows no permanent notification.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val engine: BlockEngine
        get() = (application as OtiumApp).container.blockEngine

    // Avoid pressing Back many times for one Shorts/Reels screen
    private var lastBackAt = 0L

    // Accessibility events don't fire on screen-off, so we watch for it to reset the timer
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) engine.onScreenOff()
        }
    }

    override fun onServiceConnected() {
        engine.attachAccessibility(this)
        // Content events (for reading browser URLs) plus window changes. Throttled so
        // scrolling pages don't flood us. canRetrieveWindowContent is needed to read the URL bar.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 300
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                engine.onWindowEvent(pkg, event.className?.toString())
                // Judge "which app is in front" by the focused window, not by whichever window
                // fired the event: launchers and other apps draw overlays over the real app.
                val active = rootInActiveWindow?.packageName?.toString() ?: pkg
                engine.onForeground(active)
                checkContent(pkg)
            }
            // Only browsers/feeds do content work; everything else returns immediately (cheap)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkContent(pkg)
        }
    }

    private fun checkContent(pkg: String) {
        if (pkg !in engine.contentPackages) return
        if (pkg in engine.browserPackages) checkBrowser(pkg)
        checkFeeds(pkg)
    }

    /** Close a blocked in-app feed (YouTube Shorts, Instagram Reels…) by pressing Back. */
    private fun checkFeeds(pkg: String) {
        val viewIds = engine.blockedSectionViewIds(pkg)
        if (viewIds.isEmpty()) return
        val root = rootInActiveWindow ?: return
        val onFeed = viewIds.any { id ->
            try {
                root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }
        if (!onFeed) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 800) return
        lastBackAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** Read the address bar via its known view id and hand the text to the engine. */
    private fun checkBrowser(pkg: String) {
        val viewId = engine.urlBarViewId(pkg) ?: return
        val root = rootInActiveWindow ?: return
        val text = try {
            root.findAccessibilityNodeInfosByViewId(viewId)
                .firstNotNullOfOrNull { node -> node.text?.toString()?.takeIf { it.isNotBlank() } }
        } catch (_: Exception) {
            null
        }
        if (text != null) engine.onBrowserText(pkg, text)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        engine.detachAccessibility(this)
        super.onDestroy()
    }
}
