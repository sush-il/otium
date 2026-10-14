package com.focusblock.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.focusblock.BuildConfig
import com.focusblock.data.AppLimitRepository
import com.focusblock.data.SiteLimitRepository
import com.focusblock.data.SiteUsageTracker
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
import com.focusblock.domain.model.SiteLimit
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
    appLimitRepository: AppLimitRepository,
    siteLimitRepository: SiteLimitRepository,
    private val usage: UsageRepository,
    private val siteUsage: SiteUsageTracker,
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
    @Volatile private var appLimitProfiles: List<Profile> = emptyList()
    @Volatile private var siteLimits: List<SiteLimit> = emptyList()
    @Volatile private var pausedLimits: Set<String> = emptySet()
    @Volatile private var siteLimitHosts: Set<String> = emptySet()

    // Which website host the current browser is showing, if it has site rules
    @Volatile private var currentSiteHost: String? = null
    private var currentSitePkg: String? = null

    private var current: String? = null
    private var pendingPkg: String? = null
    private var switchJob: Job? = null
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
    private var lastRedirectPkg: String? = null
    private var lastRedirectAt = 0L
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
            settingsStore.pausedLimits.collect {
                pausedLimits = it
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch {
            appLimitRepository.limits.collect { limits ->
                appLimitProfiles = limits.map { it.asProfile() }
                current?.let { pkg -> startWatching(pkg) }
            }
        }
        scope.launch {
            siteLimitRepository.limits.collect { limits ->
                siteLimits = limits
                siteLimitHosts = limits.mapTo(mutableSetOf()) { it.host }
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

    /**
     * Browser address-bar text changed. Two paths:
     *  - adult sites and keywords are blocked immediately (no time involved);
     *  - a host with user site-limits becomes the "current site" so the watch loop can
     *    apply its stacked rules (schedule / time limits / etc.) and track its time.
     */
    fun onBrowserText(pkg: String, text: String) {
        val host = SiteMatcher.extractHost(text)?.removePrefix("www.")

        // Immediate blocks: adult domains + keywords
        val immediateSites = if (blockAdult) content.adultDomains else emptySet()
        val immediateKeywords = buildSet {
            if (keywordBlockingEnabled) addAll(blockedKeywords)
            if (blockAdult) addAll(content.adultKeywords)
        }
        if (immediateSites.isNotEmpty() || immediateKeywords.isNotEmpty()) {
            SiteMatcher.blockedReason(text, immediateSites, immediateKeywords)?.let {
                setCurrentSite(pkg, null)
                redirectBrowserAway(pkg, it)
                return
            }
        }

        // User site-limits: find the rule host this page belongs to (incl. subdomains)
        val ruleHost = host?.let { h -> siteLimitHosts.firstOrNull { h == it || h.endsWith(".$it") } }
        setCurrentSite(pkg, ruleHost)
    }

    private fun setCurrentSite(pkg: String, ruleHost: String?) {
        if (ruleHost == currentSiteHost && pkg == currentSitePkg) return
        siteUsage.onHost(ruleHost)
        currentSiteHost = ruleHost
        currentSitePkg = pkg
        startWatching(pkg)
    }

    private fun clearCurrentSite() {
        if (currentSiteHost == null && currentSitePkg == null) return
        siteUsage.pause()
        currentSiteHost = null
        currentSitePkg = null
    }

    /**
     * Unlike a blocked app (which gets sent home), a blocked site must NOT close the browser:
     * the browser would just reopen on the same page and loop. Instead we navigate it to a
     * blank page, like the reference app opening a new tab. The URL bar then no longer matches,
     * so it won't re-trigger.
     */
    private fun redirectBrowserAway(pkg: String, reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastRedirectPkg && now - lastRedirectAt < REDIRECT_DEBOUNCE_MS) return
        lastRedirectPkg = pkg
        lastRedirectAt = now
        log("blocked browsing in $pkg: $reason — redirecting")
        val redirected = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "about:blank".toUri())
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        // If the browser won't take the intent, fall back to pressing Back off the page
        if (!redirected) accessibility?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
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
        // Notification shade or keyboard on top: the app underneath is still in front.
        // Google Play services pops brief windows (common in YouTube/ReVanced); ignore them.
        if (pkg == SYSTEM_UI || pkg == currentKeyboardPackage() || pkg.endsWith(".gms")) return
        if (pkg == current) {
            // Already the current app: cancel any pending switch away from it
            pendingPkg = null
            switchJob?.cancel()
            switchJob = null
            return
        }
        if (pkg == pendingPkg) return
        // Debounce the switch: a chatty app (e.g. YouTube) flips through transient windows,
        // so only commit once a new app has been in front continuously for a moment. This
        // keeps the timer steady while the app is genuinely open.
        pendingPkg = pkg
        switchJob?.cancel()
        switchJob = scope.launch {
            delay(SWITCH_DEBOUNCE_MS)
            pendingPkg = null
            log("foreground -> $pkg")
            if (pkg != currentSitePkg) clearCurrentSite()
            current = pkg
            startWatching(pkg)
        }
    }

    /** Screen went off: nobody is using the app any more. It's reported again on unlock. */
    fun onScreenOff() {
        grants.clear()
        clearCurrentSite()
        current = null
        pendingPkg = null
        switchJob?.cancel()
        switchJob = null
        checkJob?.cancel()
        checkJob = null
        _countdown.value = null
    }

    private fun startWatching(pkg: String) {
        checkJob?.cancel()
        checkJob = null
        if (pkg == context.packageName || pkg == launcherPackage()) {
            // No app to judge here, so there's nothing to count down
            _countdown.value = null
            return
        }
        // Don't null the countdown here: watch() sets it on its first pass, so re-watching
        // the same limited app (e.g. after a settings change) won't blink the timer off.
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
            // A browser showing a limited website is judged on the SITE's rules and time;
            // everything else is judged on the app's rules.
            val site = if (pkg in browserPackages) currentSiteHost else null
            val result = withContext(Dispatchers.Default) {
                val now = LocalDateTime.now()
                if (site != null) {
                    val rules = siteLimits.filter { it.host == site && "site:$site" !in pausedLimits }.map { it.asProfile() }
                    Triple(
                        BlockEvaluator.evaluate(site, now, rules, siteUsage),
                        BlockEvaluator.nextRecheck(site, now, rules, siteUsage),
                        BlockEvaluator.timeLeft(site, now, rules, siteUsage),
                    )
                } else {
                    // Master app-blocking switch off → nothing app-based blocks
                    val rules = if (!appBlockingEnabled) emptyList() else profiles + appLimitProfiles.filter { p -> p.packages.none { "app:$it" in pausedLimits } }
                    val cached = CachingUsageSource(usage) // read each usage value at most once
                    val excluded = wholePhoneExcluded()
                    Triple(
                        BlockEvaluator.evaluate(pkg, now, rules, cached, excluded),
                        BlockEvaluator.nextRecheck(pkg, now, rules, cached, excluded),
                        BlockEvaluator.timeLeft(pkg, now, rules, cached, excluded),
                    )
                }
            }
            val (decision, recheckIn, timeLeft) = result
            if (decision is BlockDecision.Blocked) {
                _countdown.value = null
                if (site != null) redirectBrowserAway(pkg, decision.reason) else block(pkg, decision.reason)
                return
            }
            _countdown.value = timeLeft?.let {
                Countdown(site ?: pkg, it.kind, SystemClock.elapsedRealtime() + it.duration.toMillis())
            }
            if (recheckIn == null) return
            log("recheck ${site ?: pkg} in ${recheckIn.seconds}s")
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
        // Show the block screen over the app. No "go home" first: that action lands a moment
        // later and covers the block screen. The block screen goes home when dismissed.
        val shown = runCatching {
            context.startActivity(
                Intent(context, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                    .putExtra(BlockActivity.EXTRA_REASON, reason),
            )
        }.isSuccess
        if (!shown) goHome()
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
        const val SWITCH_DEBOUNCE_MS = 450L
        const val REDIRECT_DEBOUNCE_MS = 2_500L
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
