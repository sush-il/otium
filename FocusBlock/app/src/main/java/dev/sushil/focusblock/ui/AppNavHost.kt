package dev.sushil.focusblock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sushil.focusblock.ui.modes.EmergencyScreen
import dev.sushil.focusblock.ui.modes.ModesScreen
import dev.sushil.focusblock.ui.onboarding.PermissionsScreen
import dev.sushil.focusblock.ui.profiles.ProfileEditScreen
import dev.sushil.focusblock.ui.profiles.ProfileListScreen
import dev.sushil.focusblock.ui.settings.ExcludedAppsScreen
import dev.sushil.focusblock.ui.settings.SettingsScreen
import dev.sushil.focusblock.util.Permissions

object Routes {
    const val PROFILES = "profiles"
    const val PERMISSIONS = "permissions"
    const val PROFILE_EDIT = "profile/{id}"
    const val SETTINGS = "settings"
    const val MODES = "modes"
    const val EMERGENCY = "emergency"
    const val EXCLUDED_APPS = "excluded_apps"

    fun profileEdit(id: Long) = "profile/$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    // First launch (or blocker switched off): go through setup first
    val start = remember {
        if (Permissions.hasRequired(context)) Routes.PROFILES else Routes.PERMISSIONS
    }
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.PROFILES) {
            ProfileListScreen(
                onOpenProfile = { nav.navigate(Routes.profileEdit(it)) },
                onNewProfile = { nav.navigate(Routes.profileEdit(0)) },
                onOpenSetup = { nav.navigate(Routes.PERMISSIONS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenModes = { nav.navigate(Routes.MODES) },
            )
        }
        composable(Routes.MODES) {
            ModesScreen(onBack = { nav.popBackStack() }, onOpenEmergency = { nav.navigate(Routes.EMERGENCY) })
        }
        composable(Routes.EMERGENCY) {
            EmergencyScreen(onClose = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenSetup = { nav.navigate(Routes.PERMISSIONS) },
                onOpenExcludedApps = { nav.navigate(Routes.EXCLUDED_APPS) },
            )
        }
        composable(Routes.EXCLUDED_APPS) {
            ExcludedAppsScreen(onDone = { nav.popBackStack() })
        }
        composable(
            Routes.PROFILE_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            ProfileEditScreen(
                profileId = entry.arguments?.getLong("id") ?: 0L,
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(onDone = {
                // Coming from the list: just go back. First launch: replace setup with the list.
                if (!nav.popBackStack(Routes.PROFILES, inclusive = false)) {
                    nav.navigate(Routes.PROFILES) { popUpTo(Routes.PERMISSIONS) { inclusive = true } }
                }
            })
        }
    }
}
