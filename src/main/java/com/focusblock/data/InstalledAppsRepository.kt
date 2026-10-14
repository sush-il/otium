package com.focusblock.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/** Every app worth blocking: all user-installed apps, plus system apps the user can open. */
class InstalledAppsRepository(private val context: Context) {

    suspend fun allApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val excluded = setOf(context.packageName, launcherPackage(pm), settingsPackage(pm))
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName !in excluded }
            // Skip pure system components (no UI to block), keep system apps like YouTube or Chrome
            .filter { !it.isSystem() || pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun ApplicationInfo.isSystem() = flags and ApplicationInfo.FLAG_SYSTEM != 0

    private fun launcherPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    // Settings is never blocked by profiles; Lock/Strict mode guard it separately (phase 8)
    private fun settingsPackage(pm: PackageManager): String? =
        pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
}
