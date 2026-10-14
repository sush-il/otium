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
import dev.sushil.focusblock.data.UsageRepository
import dev.sushil.focusblock.domain.BlockEvaluator
import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Watches which app comes to the front and sends the user away from blocked apps.
 *
 * Android keeps an enabled accessibility service alive and restarts it after reboot,
 * so no foreground service or boot receiver is needed.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var usage: UsageRepository

    // In-memory copy of the rules: events arrive on the main thread, so no DB reads per event
    @Volatile private var profiles: List<Profile> = emptyList()

    private var launcherPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

    // Checks the app in front now, then again whenever its rules say it may become blocked
    private var checkJob: Job? = null

    override fun onServiceConnected() {
        launcherPackage = resolveLauncherPackage()
        val container = (application as FocusBlockApp).container
        usage = container.usageRepository
        log("connected, launcher=$launcherPackage")
        scope.launch {
            container.profileRepository.profiles.collect {
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
        // Notification shade or keyboard on top: the app underneath is still in front
        if (pkg == SYSTEM_UI || pkg == currentKeyboardPackage()) return

        checkJob?.cancel()
        if (pkg == packageName || pkg == launcherPackage) return
        checkJob = scope.launch { watch(pkg) }
    }

    override fun onInterrupt() = Unit

    /** Runs while [pkg] stays in front; cancelled as soon as another app comes up. */
    private suspend fun watch(pkg: String) {
        while (true) {
            val usedToday = { p: String -> usage.usedTodayMs(p) }
            // Reading usage stats is a slow system call, keep it off the main thread
            val (decision, recheckIn) = withContext(Dispatchers.Default) {
                val now = LocalDateTime.now()
                BlockEvaluator.evaluate(pkg, now, profiles, usedToday) to
                    BlockEvaluator.nextRecheck(pkg, now, profiles, usedToday)
            }
            if (decision is BlockDecision.Blocked) {
                block(pkg, decision.reason)
                return
            }
            if (recheckIn == null) return
            log("recheck $pkg in ${recheckIn.seconds}s")
            // Usage stats can lag a little, so never spin faster than once a second
            delay(recheckIn.toMillis().coerceAtLeast(MIN_RECHECK_MS))
        }
    }

    private fun block(pkg: String, reason: String) {
        // One app can fire several window events while opening; only react once
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockedAt < DEBOUNCE_MS) return
        lastBlockedPackage = pkg
        lastBlockedAt = now

        log("blocking $pkg: $reason")
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason),
        )
    }

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
        const val SYSTEM_UI = "com.android.systemui"
        const val DEBOUNCE_MS = 1_000L
        const val MIN_RECHECK_MS = 1_000L
    }
}
