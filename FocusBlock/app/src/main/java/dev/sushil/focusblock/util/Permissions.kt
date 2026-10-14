package dev.sushil.focusblock.util

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import dev.sushil.focusblock.service.BlockerAccessibilityService

object Permissions {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = ComponentName(context, BlockerAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    /** Android 13+ greys out the accessibility toggle for sideloaded apps until this is allowed. */
    val needsRestrictedSettingsStep: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Usage access lets us read how long each app was used today (for daily limits). */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    fun accessibilitySettingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
