package dev.sushil.focusblock.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sushil.focusblock.service.TrackingService
import dev.sushil.focusblock.ui.theme.FocusBlockTheme

class MainActivity : ComponentActivity() {

    // Set when the password gate's "Forgot password" sends the user here
    private var openEmergency by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opening the app is always allowed to start the service, so (re)start it here too
        TrackingService.start(this)
        openEmergency = intent.getBooleanExtra(EXTRA_EMERGENCY, false)
        setContent {
            FocusBlockTheme {
                AppRoot(openEmergency = openEmergency, onEmergencyClosed = { openEmergency = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EMERGENCY, false)) openEmergency = true
    }

    companion object {
        const val EXTRA_EMERGENCY = "emergency"
    }
}
