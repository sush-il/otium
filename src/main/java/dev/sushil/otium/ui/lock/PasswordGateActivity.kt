package dev.sushil.otium.ui.lock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sushil.otium.OtiumApp
import dev.sushil.otium.service.LockTarget
import dev.sushil.otium.ui.MainActivity
import dev.sushil.otium.ui.theme.OtiumTheme

/** Lock mode: asks you to type a number before Settings or uninstalling can be used. */
class PasswordGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = intent.getStringExtra(EXTRA_TARGET)
            ?.let { runCatching { LockTarget.valueOf(it) }.getOrNull() }
            ?: LockTarget.SETTINGS
        onBackPressedDispatcher.addCallback(this) { goHome() }

        val (title, action) = when (target) {
            LockTarget.INSTALLER -> "Uninstalling is locked" to "allow uninstalling"
            else -> "Settings is locked" to "open Settings"
        }
        setContent {
            OtiumTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        PasswordForm(
                            action = action,
                            onUnlocked = { onUnlocked(target) },
                        )
                        TextButton(onClick = ::goHome) { Text("Cancel") }
                    }
                }
            }
        }
    }

    private fun onUnlocked(target: LockTarget) {
        when (target) {
            LockTarget.SETTINGS -> startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> Toast.makeText(this, "Switched to Normal mode. Try again now.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_TARGET = "target"
    }
}
