package dev.sushil.otium.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device admin = Android's "uninstall protection": while active, the app can't be
 * uninstalled until it's deactivated in Settings (which Strict mode blocks).
 * We only declare the harmless force-lock policy and never use it.
 */
class FocusAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This removes Otium's uninstall protection."

    companion object {
        fun component(context: Context) = ComponentName(context, FocusAdminReceiver::class.java)

        fun isActive(context: Context): Boolean =
            context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

        /** System screen asking the user to turn protection on. */
        fun activateIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Stops Otium from being uninstalled while Strict mode is on.",
                )

        /** An app can always remove its own admin rights without asking. */
        fun deactivate(context: Context) {
            if (isActive(context)) {
                context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
            }
        }
    }
}
