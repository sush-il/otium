package com.focusblock.ui.lock

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
import com.focusblock.FocusBlockApp
import com.focusblock.service.UnlockGrants
import com.focusblock.ui.MainActivity
import com.focusblock.ui.theme.FocusBlockTheme

/** Lock mode: asks you to type a number before Settings or uninstalling can be used. */
class PasswordGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = intent.getStringExtra(EXTRA_TARGET)
            ?.let { runCatching { UnlockGrants.Target.valueOf(it) }.getOrNull() }
            ?: UnlockGrants.Target.SETTINGS
        val grants = (application as FocusBlockApp).container.unlockGrants
        onBackPressedDispatcher.addCallback(this) { goHome() }

        val (title, message) = when (target) {
            UnlockGrants.Target.INSTALLER -> "Uninstalling is locked" to
                "Type the number. You'll then have 5 minutes to uninstall."
            else -> "Settings is locked" to "Type the number to open Settings for 5 minutes."
        }
        setContent {
            FocusBlockTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        PasswordForm(
                            message = message,
                            onUnlocked = { onUnlocked(target, grants) },
                            onForgot = {
                                startActivity(
                                    Intent(this@PasswordGateActivity, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        .putExtra(MainActivity.EXTRA_EMERGENCY, true),
                                )
                                finish()
                            },
                        )
                        TextButton(onClick = ::goHome) { Text("Cancel") }
                    }
                }
            }
        }
    }

    private fun onUnlocked(target: UnlockGrants.Target, grants: UnlockGrants) {
        grants.grant(target)
        when (target) {
            UnlockGrants.Target.SETTINGS -> startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> Toast.makeText(this, "Unlocked for 5 minutes. Try again now.", Toast.LENGTH_LONG).show()
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
