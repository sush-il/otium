package com.focusblock.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.focusblock.AppContainer
import com.focusblock.FocusBlockApp

/** Access to the app-wide dependencies from a composable, for building ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as FocusBlockApp).container
