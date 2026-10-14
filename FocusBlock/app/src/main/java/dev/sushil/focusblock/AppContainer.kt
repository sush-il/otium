package dev.sushil.focusblock

import android.content.Context
import dev.sushil.focusblock.data.BrowserRegistry
import dev.sushil.focusblock.data.BackupManager
import dev.sushil.focusblock.data.ContentRegistry
import dev.sushil.focusblock.data.InstalledAppsRepository
import dev.sushil.focusblock.data.ModeStore
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.data.SettingsStore
import dev.sushil.focusblock.data.UsageRepository
import dev.sushil.focusblock.data.db.AppDatabase
import dev.sushil.focusblock.service.BlockEngine
import dev.sushil.focusblock.service.ModeKeeper
import dev.sushil.focusblock.service.UnlockGrants
import dev.sushil.focusblock.ui.common.EditGuard
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
