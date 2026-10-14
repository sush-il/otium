package com.focusblock.ui.block

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
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
        // A line chosen once per block; a gentle nudge, nothing preachy
        val line = remember(blocked) { LINES.random() }
        // Fallback auto-dismiss so it never gets stuck if they walk away
        LaunchedEffect(blocked) {
            delay(10_000)
            onGoHome()
        }
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.background, scheme.background)),
            ),
        ) {
            // X to dismiss, top-right
            IconButton(
                onClick = onGoHome,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp),
            ) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }

            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(84.dp).background(scheme.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    blocked.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                        ?: Text("🚫", style = MaterialTheme.typography.displaySmall)
                }
                Text(line, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "${blocked.label} · ${blocked.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_REASON = "reason"

        // Short original nudges shown on the block screen
        private val LINES = listOf(
            "Your time, back in your hands.",
            "The scroll can wait.",
            "Nothing important is on that feed.",
            "Come back to what matters.",
            "Small nos build a big yes.",
            "You set this. Good call.",
            "Focus is a gift you give yourself.",
            "Take the moment back.",
        )
    }
}
