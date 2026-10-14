package dev.sushil.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.sushil.focusblock.FocusBlockApp

/**
 * Reports window changes to [BlockEngine] the moment they happen, lends the engine its
 * "go home" action, and reads the browser address bar to block sites and keywords.
 *
 * Android keeps an enabled accessibility service alive and restarts it after reboot.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val engine: BlockEngine
        get() = (application as FocusBlockApp).container.blockEngine

    // Avoid pressing Back many times for one Shorts/Reels screen
    private var lastBackAt = 0L

    override fun onServiceConnected() {
        engine.attachAccessibility(this)
        // Content events (for reading browser URLs) plus window changes. Throttled so
        // scrolling pages don't flood us. canRetrieveWindowContent is needed to read the URL bar.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 300
        }
        TrackingService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                engine.onWindowEvent(pkg, event.className?.toString())
                engine.onForeground(pkg)
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
        engine.detachAccessibility(this)
        super.onDestroy()
    }
}
