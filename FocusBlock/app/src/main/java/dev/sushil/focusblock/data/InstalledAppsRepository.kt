package dev.sushil.focusblock.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/** Apps the user can open from their home screen, i.e. the ones worth blocking. */
class InstalledAppsRepository(private val context: Context) {

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val excluded = setOf(context.packageName, launcherPackage(pm), settingsPackage(pm))
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName !in excluded }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun launcherPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    // Blocking Settings would lock the user out of turning the blocker off
    private fun settingsPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
}
