package dev.sushil.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.sushil.focusblock.BuildConfig
import dev.sushil.focusblock.ui.block.BlockActivity

/**
 * Watches which app comes to the front and sends the user away from blocked apps.
 *
 * Android keeps an enabled accessibility service alive and restarts it after reboot,
 * so no foreground service or boot receiver is needed.
 */
class BlockerAccessibilityService : AccessibilityService() {

    // Phase 2: hard-coded. Replaced by profiles from the database in phase 3.
    private val blockedPackages = setOf("com.android.chrome")

    private var launcherPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

    override fun onServiceConnected() {
        launcherPackage = resolveLauncherPackage()
        log("connected, launcher=$launcherPackage")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isIgnored(pkg)) return

        if (pkg in blockedPackages) block(pkg, reason = "This app is blocked")
    }

    override fun onInterrupt() = Unit

    private fun block(pkg: String, reason: String) {
        // One app can fire several window events while opening; only react once
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockedAt < DEBOUNCE_MS) return
        lastBlockedPackage = pkg
        lastBlockedAt = now

        log("blocking $pkg")
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason),
        )
    }

    // Never block ourselves, the system UI, the home screen or the keyboard
    private fun isIgnored(pkg: String): Boolean =
        pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg == launcherPackage ||
            pkg == currentKeyboardPackage()

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    private fun resolveLauncherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private companion object {
        const val TAG = "FocusBlock"
        const val DEBOUNCE_MS = 1_000L
    }
}
