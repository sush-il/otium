package com.focusblock

import android.content.Context
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
import com.focusblock.service.ModeKeeper
import com.focusblock.service.UnlockGrants
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency injection: one shared instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val installedAppsRepository = InstalledAppsRepository(context)
    val usageRepository = UsageRepository(context)
    val settingsStore = SettingsStore(context)
    val modeStore = ModeStore(context)
    val unlockGrants = UnlockGrants()
    private val browserRegistry = BrowserRegistry(context)
    private val contentRegistry = ContentRegistry(context)
    val blockEngine = BlockEngine(context, profileRepository, usageRepository, modeStore, settingsStore, browserRegistry, contentRegistry, unlockGrants)
    val editGuard = EditGuard(modeStore, unlockGrants)
    val backupManager = BackupManager(context, profileRepository, settingsStore)

    // Process-wide background work that isn't tied to a screen
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        ModeKeeper(context, modeStore, appScope).start()
        appScope.launch {
            settingsStore.dayResetHour.collect { usageRepository.dayResetHour = it }
        }
    }
}
