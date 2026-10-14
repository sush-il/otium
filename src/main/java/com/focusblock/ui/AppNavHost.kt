package com.focusblock.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.focusblock.ui.modes.EmergencyScreen
import com.focusblock.ui.modes.ModesScreen
import com.focusblock.ui.onboarding.PermissionsScreen
import com.focusblock.ui.profiles.ProfileEditScreen
import com.focusblock.ui.apps.AppLimitEditorScreen
import com.focusblock.ui.apps.ManageBlockedAppsScreen
import com.focusblock.ui.profiles.ProfileListScreen
import com.focusblock.ui.settings.ExcludedAppsScreen
import com.focusblock.ui.settings.FeedsScreen
import com.focusblock.ui.settings.SettingsScreen
import com.focusblock.ui.settings.WebContentScreen
import com.focusblock.util.Permissions

object Routes {
    const val PROFILES = "profiles"
    const val PERMISSIONS = "permissions"
    const val PROFILE_EDIT = "profile/{id}"
    const val SETTINGS = "settings"
    const val MODES = "modes"
    const val EMERGENCY = "emergency"
    const val EXCLUDED_APPS = "excluded_apps"
    const val WEB_CONTENT = "web_content"
    const val FEEDS = "feeds"
    const val MANAGE_APPS = "manage_apps"
    const val APP_LIMIT = "app_limit/{pkg}"

    fun appLimit(pkg: String) = "app_limit/$pkg"

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
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // The bottom bar only shows on the two top-level tabs
    val showBottomBar = route == Routes.PROFILES || route == Routes.SETTINGS

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.PROFILES,
                        onClick = { nav.switchTab(Routes.PROFILES) },
                        icon = { Text("🏠", fontSize = 18.sp) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.SETTINGS,
                        onClick = { nav.switchTab(Routes.SETTINGS) },
                        icon = { Text("⚙️", fontSize = 18.sp) },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { bottomPadding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(bottomPadding),
            // Quick, fluid slide + fade; avoids the default crossfade flash between screens
            enterTransition = {
                slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(180))
            },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = {
                slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(140))
            },
        ) {
        composable(Routes.PROFILES) {
            ProfileListScreen(
                onOpenProfile = { nav.navigate(Routes.profileEdit(it)) },
                onNewProfile = { nav.navigate(Routes.profileEdit(0)) },
                onOpenSetup = { nav.navigate(Routes.PERMISSIONS) },
                onOpenModes = { nav.navigate(Routes.MODES) },
                onOpenApps = { nav.navigate(Routes.MANAGE_APPS) },
                onOpenSites = { nav.navigate(Routes.WEB_CONTENT) },
                onOpenFeeds = { nav.navigate(Routes.FEEDS) },
            )
        }
        composable(Routes.MANAGE_APPS) {
            ManageBlockedAppsScreen(
                onBack = { nav.popBackStack() },
                onOpenApp = { pkg -> nav.navigate(Routes.appLimit(pkg)) },
            )
        }
        composable(
            Routes.APP_LIMIT,
            arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
        ) { entry ->
            AppLimitEditorScreen(
                packageName = entry.arguments?.getString("pkg").orEmpty(),
                onBack = { nav.popBackStack() },
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
                onOpenSetup = { nav.navigate(Routes.PERMISSIONS) },
                onOpenExcludedApps = { nav.navigate(Routes.EXCLUDED_APPS) },
                onOpenWebContent = { nav.navigate(Routes.WEB_CONTENT) },
                onOpenFeeds = { nav.navigate(Routes.FEEDS) },
            )
        }
        composable(Routes.EXCLUDED_APPS) {
            ExcludedAppsScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.WEB_CONTENT) {
            WebContentScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.FEEDS) {
            FeedsScreen(onBack = { nav.popBackStack() })
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
}

/** Switch to a top-level tab without piling up back-stack entries. */
private fun androidx.navigation.NavController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
