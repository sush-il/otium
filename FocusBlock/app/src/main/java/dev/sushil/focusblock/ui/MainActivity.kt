package dev.sushil.focusblock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.sushil.focusblock.service.TrackingService
import dev.sushil.focusblock.ui.theme.FocusBlockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opening the app is always allowed to start the service, so (re)start it here too
        TrackingService.start(this)
        setContent {
            FocusBlockTheme {
                AppNavHost()
            }
        }
    }
}
