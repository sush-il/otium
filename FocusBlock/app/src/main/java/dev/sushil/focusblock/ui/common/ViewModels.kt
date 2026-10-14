package dev.sushil.focusblock.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.sushil.focusblock.AppContainer
import dev.sushil.focusblock.FocusBlockApp

/** Access to the app-wide dependencies from a composable, for building ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as FocusBlockApp).container
