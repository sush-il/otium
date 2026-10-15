package dev.sushil.otium

import android.content.Context
import dev.sushil.otium.data.AppLimitRepository
import dev.sushil.otium.data.SiteLimitRepository
import dev.sushil.otium.data.SiteUsageTracker
import dev.sushil.otium.data.BrowserRegistry
import dev.sushil.otium.data.BackupManager
import dev.sushil.otium.data.ContentRegistry
import dev.sushil.otium.data.InstalledAppsRepository
import dev.sushil.otium.data.ModeStore
import dev.sushil.otium.data.ProfileRepository
import dev.sushil.otium.data.SettingsStore
import dev.sushil.otium.data.UsageRepository
import dev.sushil.otium.data.db.AppDatabase
import dev.sushil.otium.service.BlockEngine
import dev.sushil.otium.service.BlockOverlayController
import dev.sushil.otium.service.ModeKeeper
import dev.sushil.otium.ui.common.EditGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency injection: one shared instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val appLimitRepository = AppLimitRepository(database.appLimitDao())
    val siteLimitRepository = SiteLimitRepository(database.siteLimitDao())
    val siteUsageTracker = SiteUsageTracker()
    val usageRepository = UsageRepository(context)
    val settingsStore = SettingsStore(context)
    val installedAppsRepository = InstalledAppsRepository(context, settingsStore)
    val modeStore = ModeStore(context)
    private val browserRegistry = BrowserRegistry(context)
    private val contentRegistry = ContentRegistry(context)
    val blockEngine = BlockEngine(context, profileRepository, appLimitRepository, siteLimitRepository, usageRepository, siteUsageTracker, modeStore, settingsStore, browserRegistry, contentRegistry)
    val editGuard = EditGuard(modeStore)
    val backupManager = BackupManager(context, profileRepository, settingsStore)

    // Process-wide background work that isn't tied to a screen
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        ModeKeeper(context, modeStore, appScope).start()
        BlockOverlayController(context, blockEngine, settingsStore, appScope).start()
        appScope.launch {
            settingsStore.dayResetHour.collect {
                usageRepository.dayResetHour = it
                siteUsageTracker.dayResetHour = it
            }
        }
        // Forget pauses for apps/sites that no longer have any limit, so a re-added limit isn't silently paused
        appScope.launch {
            combine(appLimitRepository.limits, siteLimitRepository.limits, settingsStore.pausedLimits) { apps, sites, paused ->
                val live = apps.map { "app:${it.packageName}" }.toSet() + sites.map { "site:${it.host}" }.toSet()
                paused.filter { it in live }.toSet().takeIf { it != paused }
            }.collect { pruned -> if (pruned != null) settingsStore.setPausedLimits(pruned) }
        }
        // One-time: move old directly-blocked apps into the stackable app-limits table
        appScope.launch {
            val legacy = settingsStore.quickBlockedApps.first()
            if (legacy.isNotEmpty()) {
                legacy.forEach { appLimitRepository.addAlwaysIfAbsent(it) }
                settingsStore.setQuickBlockedApps(emptySet())
            }
        }
        // One-time: move old simple blocked-sites into stackable site-limits as ALWAYS rules
        appScope.launch {
            val legacy = settingsStore.blockedSites.first()
            if (legacy.isNotEmpty()) {
                legacy.forEach { siteLimitRepository.addAlwaysIfAbsent(it) }
                settingsStore.setBlockedSites(emptySet())
            }
        }
    }
}
