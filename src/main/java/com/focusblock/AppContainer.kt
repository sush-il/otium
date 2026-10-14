package com.focusblock

import android.content.Context
import com.focusblock.data.AppLimitRepository
import com.focusblock.data.SiteLimitRepository
import com.focusblock.data.SiteUsageTracker
import com.focusblock.data.BrowserRegistry
import com.focusblock.data.BackupManager
import com.focusblock.data.ContentRegistry
import com.focusblock.data.InstalledAppsRepository
import com.focusblock.data.ModeStore
import com.focusblock.data.ProfileRepository
import com.focusblock.data.SettingsStore
import com.focusblock.data.UsageRepository
import com.focusblock.data.db.AppDatabase
import com.focusblock.service.BlockEngine
import com.focusblock.service.BlockOverlayController
import com.focusblock.service.ModeKeeper
import com.focusblock.service.UnlockGrants
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manual dependency injection: one shared instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val appLimitRepository = AppLimitRepository(database.appLimitDao())
    val siteLimitRepository = SiteLimitRepository(database.siteLimitDao())
    val siteUsageTracker = SiteUsageTracker()
    val installedAppsRepository = InstalledAppsRepository(context)
    val usageRepository = UsageRepository(context)
    val settingsStore = SettingsStore(context)
    val modeStore = ModeStore(context)
    val unlockGrants = UnlockGrants()
    private val browserRegistry = BrowserRegistry(context)
    private val contentRegistry = ContentRegistry(context)
    val blockEngine = BlockEngine(context, profileRepository, appLimitRepository, siteLimitRepository, usageRepository, siteUsageTracker, modeStore, settingsStore, browserRegistry, contentRegistry, unlockGrants)
    val editGuard = EditGuard(modeStore, unlockGrants)
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
