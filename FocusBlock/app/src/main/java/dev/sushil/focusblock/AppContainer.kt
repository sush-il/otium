package dev.sushil.focusblock

import android.content.Context
import dev.sushil.focusblock.data.InstalledAppsRepository
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.data.db.AppDatabase

/** Manual dependency injection: one shared instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val profileRepository = ProfileRepository(database.profileDao())
    val installedAppsRepository = InstalledAppsRepository(context)
}
