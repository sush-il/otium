package dev.sushil.otium.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/** Every app worth blocking: all user-installed apps, plus system apps the user can open. */
class InstalledAppsRepository(private val context: Context, private val settings: SettingsStore) {

    suspend fun allApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val includeSystem = settings.showSystemApps.first()
        val pm = context.packageManager
        val excluded = setOf(context.packageName, launcherPackage(pm), settingsPackage(pm))
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName !in excluded }
            .filter { show(pm, it, includeSystem) }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * By default: user apps, plus updated pre-installed apps (YouTube, Chrome, Maps) that the
     * user actually uses. Pure system apps are hidden unless [includeSystem] is on.
     */
    private fun show(pm: PackageManager, app: ApplicationInfo, includeSystem: Boolean): Boolean {
        val launchable = pm.getLaunchIntentForPackage(app.packageName) != null
        val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return when {
            !isSystem -> true
            includeSystem -> launchable
            else -> isUpdatedSystem && launchable
        }
    }

    private fun launcherPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    // Settings is never blocked by profiles; Lock/Strict mode guard it separately (phase 8)
    private fun settingsPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
}
