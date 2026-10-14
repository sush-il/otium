package dev.sushil.focusblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.sushil.focusblock.ui.onboarding.PermissionsScreen
import dev.sushil.focusblock.util.Permissions

object Routes {
    const val PROFILES = "profiles"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    // First launch (or blocker switched off): go through setup first
    val start = remember {
        if (Permissions.isAccessibilityEnabled(context)) Routes.PROFILES else Routes.PERMISSIONS
    }
    Scaffold { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.PROFILES) {
                Placeholder("Profiles") {
                    Text("Phase 2: Chrome is blocked (hard-coded)")
                    Button(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Permissions") }
                    Button(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Settings") }
                }
            }
            composable(Routes.PERMISSIONS) {
                PermissionsScreen(onDone = {
                    nav.navigate(Routes.PROFILES) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                })
            }
            composable(Routes.SETTINGS) { Placeholder("Settings") }
        }
    }
}

// Temporary screen body until each real screen is built
@Composable
private fun Placeholder(title: String, actions: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        actions()
    }
}
