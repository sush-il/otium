package dev.sushil.focusblock

import android.content.Context
import dev.sushil.focusblock.data.InstalledAppsRepository
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.data.UsageRepository
import dev.sushil.focusblock.data.db.AppDatabase
import dev.sushil.focusblock.service.BlockEngine

/** Manual dependency injection: one shared instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val installedAppsRepository = InstalledAppsRepository(context)
    val usageRepository = UsageRepository(context)
    val blockEngine = BlockEngine(context, profileRepository, usageRepository)
}
