package dev.sushil.otium.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.sushil.otium.AppContainer
import dev.sushil.otium.OtiumApp

/** Access to the app-wide dependencies from a composable, for building ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as OtiumApp).container
