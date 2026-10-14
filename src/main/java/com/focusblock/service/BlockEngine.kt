package com.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import com.focusblock.BuildConfig
import com.focusblock.data.ModeStore
import com.focusblock.data.SettingsStore
import com.focusblock.data.ProfileRepository
import com.focusblock.data.UsageRepository
import com.focusblock.data.BrowserRegistry
import com.focusblock.data.ContentRegistry
import com.focusblock.domain.BlockEvaluator
import com.focusblock.domain.SiteMatcher
import com.focusblock.domain.UsageSource
import com.focusblock.domain.model.BlockDecision
import com.focusblock.domain.model.ModeState
import com.focusblock.domain.model.Profile
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.TimeLeft
import com.focusblock.ui.block.BlockActivity
import com.focusblock.ui.lock.PasswordGateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The one place that knows which app is in front and whether to block it.
 *
 * The accessibility service reports the foreground app via [onForeground]; reports of
 * the app we already know about are ignored.
 *
 * Main thread only.
 */
class BlockEngine(
    private val context: Context,
    profileRepository: ProfileRepository,
    private val usage: UsageRepository,
    modeStore: ModeStore,
    settingsStore: SettingsStore,
    private val browsers: BrowserRegistry,
    private val content: ContentRegistry,
    private val grants: UnlockGrants,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // In-memory copy of the rules so a foreground change never waits on the database
    @Volatile private var profiles: List<Profile> = emptyList()
    @Volatile private var modeState = ModeState()
    @Volatile private var excludedApps: Set<String> = emptySet()
    @Volatile private var blockedSites: Set<String> = emptySet()
    @Volatile private var blockedKeywords: Set<String> = emptySet()
    @Volatile private var enabledSectionKeys: Set<String> = emptySet()
    @Volatile private var blockAdult: Boolean = false
    @Volatile private var appBlockingEnabled: Boolean = true
    @Volatile private var keywordBlockingEnabled: Boolean = true
    @Volatile private var quickBlockedApps: Set<String> = emptySet()

    private var current: String? = null
    private var checkJob: Job? = null
    private var accessibility: AccessibilityService? = null
    private var launcherPackage: String? = null
    private var settingsPackage: String? = null
    private var installerPackages: Set<String>? = null
    private var dialerPackage: String? = null

    private val _countdown = MutableStateFlow<Countdown?>(null)

    /** The open app's time left, or null when nothing is counting down. Drives the timer and alerts. */
    val countdown: StateFlow<Countdown?> = _countdown.asStateFlow()

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
        scope.launch {
            modeStore.state.collect {
                modeState = it
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch {
            settingsStore.excludedApps.collect {
                excludedApps = it
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch { settingsStore.blockedSites.collect { blockedSites = it } }
        scope.launch { settingsStore.blockedKeywords.collect { blockedKeywords = it } }
        scope.launch { settingsStore.blockAdult.collect { blockAdult = it } }
        scope.launch {
            settingsStore.appBlockingEnabled.collect {
                appBlockingEnabled = it
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch { settingsStore.keywordBlockingEnabled.collect { keywordBlockingEnabled = it } }
        scope.launch {
            settingsStore.quickBlockedApps.collect {
                quickBlockedApps = it
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch {
            combine(
                settingsStore.blockYoutubeShorts,
                settingsStore.blockInstagramReels,
                settingsStore.blockFacebookReels,
            ) { yt, ig, fb ->
                buildSet {
                    if (yt) add("youtube_shorts")
                    if (ig) add("instagram_reels")
                    if (fb) add("facebook_reels")
                }
            }.collect { enabledSectionKeys = it }
        }
    }

    /** Packages where we also read content: browsers (URLs) and apps with a feed to close. */
    val contentPackages: Set<String> get() = browsers.browserPackages + content.sectionPackages

    /** View ids of feeds that are enabled for [pkg] right now, for the service to look for. */
    fun blockedSectionViewIds(pkg: String): List<String> =
        content.sections.filter { it.packageName == pkg && it.settingKey in enabledSectionKeys }.map { it.viewId }

    val browserPackages: Set<String> get() = browsers.browserPackages

    fun urlBarViewId(pkg: String): String? = browsers.urlBarViewId(pkg)

    /** Browser address-bar text changed: send the user home if it's a blocked site/search. */
    fun onBrowserText(pkg: String, text: String) {
        val sites = buildSet {
            addAll(blockedSites)
            if (blockAdult) addAll(content.adultDomains)
        }
        val keywords = buildSet {
            if (keywordBlockingEnabled) addAll(blockedKeywords)
            if (blockAdult) addAll(content.adultKeywords)
        }
        if (sites.isEmpty() && keywords.isEmpty()) return
        val reason = SiteMatcher.blockedReason(text, sites, keywords) ?: return
        log("blocked browsing in $pkg: $reason")
        block(pkg, reason)
    }

    fun attachAccessibility(service: AccessibilityService) {
        accessibility = service
    }

    fun detachAccessibility(service: AccessibilityService) {
        if (accessibility === service) accessibility = null
    }

    /**
     * Window details only accessibility events have (the class name). Used by Strict mode
     * to close the Recents screen and the power-off menu.
     */
    fun onWindowEvent(pkg: String, className: String?) {
        val cls = className ?: return
        val state = modeState
        if (!state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) return
        val service = accessibility ?: return
        if (state.strict.blockRecents && cls.contains("Recents", ignoreCase = true)) {
            log("closing recents ($pkg/$cls)")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        if (state.strict.blockPowerMenu && cls.contains("GlobalActions", ignoreCase = true)) {
            log("closing power menu ($pkg/$cls)")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    fun onForeground(pkg: String) {
        // Notification shade or keyboard on top: the app underneath is still in front
        if (pkg == SYSTEM_UI || pkg == currentKeyboardPackage()) return
        if (pkg == current) return
        current = pkg
        startWatching(pkg)
    }

    /** Screen went off: nobody is using the app any more. It's reported again on unlock. */
    fun onScreenOff() {
        grants.clear()
        current = null
        checkJob?.cancel()
        checkJob = null
        _countdown.value = null
    }

    private fun startWatching(pkg: String) {
        checkJob?.cancel()
        checkJob = null
        _countdown.value = null
        if (pkg == context.packageName || pkg == launcherPackage()) return
        checkJob = scope.launch { watch(pkg) }
    }

    /** Runs while [pkg] stays in front; cancelled as soon as another app comes up. */
    private suspend fun watch(pkg: String) {
        when (val guard = modeGuard(pkg)) {
            is Guard.Block -> {
                block(pkg, guard.reason)
                return
            }
            is Guard.Password -> {
                showPasswordGate(pkg, guard.target)
                return
            }
            null -> Unit
        }
        while (true) {
            // Master app-blocking switch: when off, nothing app-based blocks.
            // Directly-blocked apps act like a built-in "always" profile.
            val rules = if (!appBlockingEnabled) {
                emptyList()
            } else if (quickBlockedApps.isEmpty()) {
                profiles
            } else {
                profiles + Profile(id = -1L, name = "Blocked apps", type = RuleType.ALWAYS, packages = quickBlockedApps)
            }
            // Reading usage stats is a slow system call, keep it off the main thread
            val result = withContext(Dispatchers.Default) {
                // One snapshot per round, so each usage value is read at most once
                val cached = CachingUsageSource(usage)
                val now = LocalDateTime.now()
                val excluded = wholePhoneExcluded()
                Triple(
                    BlockEvaluator.evaluate(pkg, now, rules, cached, excluded),
                    BlockEvaluator.nextRecheck(pkg, now, rules, cached, excluded),
                    BlockEvaluator.timeLeft(pkg, now, rules, cached, excluded),
                )
            }
            val (decision, recheckIn, timeLeft) = result
            if (decision is BlockDecision.Blocked) {
                _countdown.value = null
                block(pkg, decision.reason)
                return
            }
            _countdown.value = timeLeft?.let {
                Countdown(pkg, it.kind, SystemClock.elapsedRealtime() + it.duration.toMillis())
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
        // Without accessibility (polling path), starting activities from the background is
        // allowed because we hold "Display over other apps"
        goHome()
        context.startActivity(
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockActivity.EXTRA_REASON, reason),
        )
    }

    private sealed interface Guard {
        data class Block(val reason: String) : Guard
        data class Password(val target: UnlockGrants.Target) : Guard
    }

    /** Lock/Strict mode protection of Settings, uninstalling and the Play Store. */
    private fun modeGuard(pkg: String): Guard? {
        val state = modeState
        val isSettings = pkg == settingsPackage()
        val isInstaller = pkg in installerPackages()
        if (state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) {
            val until = state.strictUntil?.let { "until " + formatEndTime(it) } ?: "while its schedule runs"
            if (isInstaller) return Guard.Block("Uninstalling is blocked by Strict mode $until")
            if (isSettings && state.strict.blockSettings) return Guard.Block("Settings is blocked by Strict mode $until")
            if (pkg == PLAY_STORE && state.strict.blockPlayStore) return Guard.Block("Play Store is blocked by Strict mode $until")
        }
        if (state.isLock && state.passwordHash != null) {
            if (isSettings && state.lock.lockSettings && !grants.isGranted(UnlockGrants.Target.SETTINGS)) {
                return Guard.Password(UnlockGrants.Target.SETTINGS)
            }
            if (isInstaller && !grants.isGranted(UnlockGrants.Target.INSTALLER)) {
                return Guard.Password(UnlockGrants.Target.INSTALLER)
            }
        }
        return null
    }

    private fun showPasswordGate(pkg: String, target: UnlockGrants.Target) {
        current = null
        log("password needed for $pkg ($target)")
        goHome()
        context.startActivity(
            Intent(context, PasswordGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(PasswordGateActivity.EXTRA_TARGET, target.name),
        )
    }

    private fun goHome() {
        val service = accessibility
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            context.startActivity(homeIntent())
        }
    }

    private fun formatEndTime(epochMs: Long): String =
        DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    private fun settingsPackage(): String? =
        settingsPackage ?: context.packageManager
            .resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            .also { settingsPackage = it }

    /** Whatever handles uninstall/install on this phone, plus the usual names. */
    private fun installerPackages(): Set<String> = installerPackages ?: buildSet {
        addAll(KNOWN_INSTALLERS)
        val pm = context.packageManager
        listOf(
            @Suppress("DEPRECATION") Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:x".toUri()),
            Intent(Intent.ACTION_DELETE, "package:x".toUri()),
        ).forEach { intent ->
            pm.queryIntentActivities(intent, 0).forEach { add(it.activityInfo.packageName) }
        }
    }.also { installerPackages = it }

    /** Apps a whole-phone profile must never catch: the user's list plus essentials. */
    private fun wholePhoneExcluded(): Set<String> = buildSet {
        addAll(excludedApps)
        add(context.packageName)
        launcherPackage()?.let { add(it) }
        settingsPackage()?.let { add(it) }
        dialerPackage()?.let { add(it) }
        currentKeyboardPackage()?.let { add(it) }
        add(SYSTEM_UI)
    }

    private fun dialerPackage(): String? =
        dialerPackage ?: context.packageManager
            .resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            .also { dialerPackage = it }

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
        const val PLAY_STORE = "com.android.vending"
        val KNOWN_INSTALLERS = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )
        const val DEBOUNCE_MS = 1_000L
        const val MIN_RECHECK_MS = 1_000L
    }
}

/**
 * [endsAt] is on the [SystemClock.elapsedRealtime] clock, so changing the phone's
 * date/time doesn't move it.
 */
data class Countdown(val pkg: String, val kind: TimeLeft.Kind, val endsAt: Long) {
    fun remainingMs(): Long = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
}

/** Reads each usage value at most once, so one evaluation round makes a stable decision. */
private class CachingUsageSource(private val delegate: UsageSource) : UsageSource {
    private val today = HashMap<String, Long?>()
    private val hour = HashMap<String, Long?>()
    private val launches = HashMap<String, Int?>()
    override fun usedTodayMs(pkg: String) = today.getOrPut(pkg) { delegate.usedTodayMs(pkg) }
    override fun usedThisHourMs(pkg: String) = hour.getOrPut(pkg) { delegate.usedThisHourMs(pkg) }
    override fun launchesToday(pkg: String) = launches.getOrPut(pkg) { delegate.launchesToday(pkg) }
}
