package com.focusblock.ui.block

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.focusblock.ui.theme.FocusBlockTheme

/** Full-screen "this app is blocked" page. Started by the accessibility service. */
class BlockActivity : ComponentActivity() {

    private data class Blocked(val label: String, val icon: ImageBitmap?, val reason: String)

    private var blocked by mutableStateOf<Blocked?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        blocked = readIntent(intent)
        // Back must never return to the blocked app
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusBlockTheme {
                blocked?.let { BlockScreen(it, onGoHome = ::goHome) }
            }
        }
    }

    // singleInstance: a second block while this is open arrives here instead of onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blocked = readIntent(intent)
    }

    private fun readIntent(intent: Intent): Blocked {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            Blocked(
                label = packageManager.getApplicationLabel(info).toString(),
                icon = packageManager.getApplicationIcon(info).toBitmap(192, 192).asImageBitmap(),
                reason = reason,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Blocked(label = pkg, icon = null, reason = reason)
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    @Composable
    private fun BlockScreen(blocked: Blocked, onGoHome: () -> Unit) {
        // Auto-dismiss so it never feels stuck; the button is there if they want it sooner
        LaunchedEffect(blocked) {
            delay(4000)
            onGoHome()
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    blocked.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(56.dp)) }
                        ?: Text("🚫", style = MaterialTheme.typography.displaySmall)
                }
                Text("Blocked", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(
                    blocked.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(blocked.reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = onGoHome) { Text("Go home") }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_REASON = "reason"
    }
}
