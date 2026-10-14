package dev.sushil.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.sushil.focusblock.BuildConfig
import dev.sushil.focusblock.FocusBlockApp
import dev.sushil.focusblock.domain.BlockEvaluator
import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches which app comes to the front and sends the user away from blocked apps.
 *
 * Android keeps an enabled accessibility service alive and restarts it after reboot,
 * so no foreground service or boot receiver is needed.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // In-memory copy of the rules: events arrive on the main thread, so no DB reads per event
    @Volatile private var profiles: List<Profile> = emptyList()

    private var launcherPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

    override fun onServiceConnected() {
        launcherPackage = resolveLauncherPackage()
        log("connected, launcher=$launcherPackage")
        val repository = (application as FocusBlockApp).container.profileRepository
        scope.launch {
            repository.profiles.collect {
                profiles = it
                log("loaded ${it.size} profiles")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (isIgnored(pkg)) return

        val decision = BlockEvaluator.evaluate(pkg, profiles)
        if (decision is BlockDecision.Blocked) block(pkg, decision.reason)
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
