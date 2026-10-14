package dev.sushil.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import dev.sushil.focusblock.BuildConfig
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.data.UsageRepository
import dev.sushil.focusblock.domain.BlockEvaluator
import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.ui.block.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * The one place that knows which app is in front and whether to block it.
 *
 * Two sources report the foreground app: accessibility events (instant) and the
 * [TrackingService] poller (every 300 ms, catches what events miss). Both call
 * [onForeground]; reports of the app we already know about are ignored.
 *
 * Main thread only.
 */
class BlockEngine(
    private val context: Context,
    profileRepository: ProfileRepository,
    private val usage: UsageRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // In-memory copy of the rules so a foreground change never waits on the database
    @Volatile private var profiles: List<Profile> = emptyList()

    private var current: String? = null
    private var checkJob: Job? = null
    private var accessibility: AccessibilityService? = null
    private var launcherPackage: String? = null

    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

    init {
        scope.launch {
            profileRepository.profiles.collect {
                profiles = it
                log("loaded ${it.size} profiles")
                // Rules changed: check the app in front again
                current?.let { pkg -> startWatching(pkg) }
            }
        }
    }

    fun attachAccessibility(service: AccessibilityService) {
        accessibility = service
    }

    fun detachAccessibility(service: AccessibilityService) {
        if (accessibility === service) accessibility = null
    }

    fun onForeground(pkg: String) {
        // Notification shade or keyboard on top: the app underneath is still in front
        if (pkg == SYSTEM_UI || pkg == currentKeyboardPackage()) return
        if (pkg == current) return
        current = pkg
        startWatching(pkg)
    }

    private fun startWatching(pkg: String) {
        checkJob?.cancel()
        checkJob = null
        if (pkg == context.packageName || pkg == launcherPackage()) return
        checkJob = scope.launch { watch(pkg) }
    }

    /** Runs while [pkg] stays in front; cancelled as soon as another app comes up. */
    private suspend fun watch(pkg: String) {
        while (true) {
            val usedToday = { p: String -> usage.usedTodayMs(p) }
            val rules = profiles
            // Reading usage stats is a slow system call, keep it off the main thread
            val (decision, recheckIn) = withContext(Dispatchers.Default) {
                val now = LocalDateTime.now()
                BlockEvaluator.evaluate(pkg, now, rules, usedToday) to
                    BlockEvaluator.nextRecheck(pkg, now, rules, usedToday)
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
        // Both sources can report the same launch; only react once
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockedAt < DEBOUNCE_MS) return
        lastBlockedPackage = pkg
        lastBlockedAt = now
        // Forget it, so opening the same app again is checked again
        current = null

        log("blocking $pkg: $reason (accessibility=${accessibility != null})")
        val service = accessibility
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            // Polling path: allowed from the background because we hold "Display over other apps"
            context.startActivity(homeIntent())
        }
        context.startActivity(
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason),
        )
    }

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    // Resolved lazily and cached: the default launcher rarely changes
    private fun launcherPackage(): String? =
        launcherPackage ?: context.packageManager
            .resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            .also { launcherPackage = it }

    private fun homeIntent() =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
