package com.focusblock.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.Profile
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog
import com.focusblock.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenModes: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSites: () -> Unit,
    onOpenFeeds: () -> Unit,
    onOpenKeywords: () -> Unit,
) {
    val container = appContainer()
    val vm: ProfileListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileListViewModel(container.profileRepository, container.settingsStore, container.appLimitRepository, container.siteLimitRepository, container.modeStore, container.editGuard)
            }
        },
    )
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog(
            message = "Lock mode: this change needs you to type a number.",
            onUnlocked = { vm.onPasswordAccepted() },
            onDismiss = vm::dismissPassword,
        )
    }

    val context = LocalContext.current
    var blockerReady by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        blockerReady = Permissions.hasRequired(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Focus Block",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        val list = profiles
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!blockerReady) item { BlockerOffBanner(onOpenSetup) }

            item { ModeCarousel(mode, onOpenModes) }

            item { SectionHeader("Quick Actions") }
            item { QuickActions(vm, onOpenApps, onOpenSites, onOpenFeeds, onOpenKeywords) }

            item { SectionHeader("Profiles") }
            item { AddProfileButton(onNewProfile) }
            when {
                list == null -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                }
                else -> items(list, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onOpenProfile(profile.id) },
                        onToggle = { vm.setEnabled(profile, it) },
                        onPause = { minutes -> vm.pause(profile, minutes) },
                        onResume = { vm.resume(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private data class ModeInfo(val mode: Mode, val title: String, val body: String)

private val MODE_INFO = listOf(
    ModeInfo(Mode.NORMAL, "Normal Mode", "Change anything and uninstall freely."),
    ModeInfo(Mode.LOCK, "Lock Mode", "Typing a long number protects your settings and changes."),
    ModeInfo(Mode.STRICT, "Strict Mode", "Locked until it ends. No undo."),
)

/** Swipeable cards for all three modes (browsing only — doesn't change the mode). */
@Composable
private fun ModeCarousel(mode: Mode?, onOpenModes: () -> Unit) {
    val current = mode ?: Mode.NORMAL
    val currentIndex = MODE_INFO.indexOfFirst { it.mode == current }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentIndex) { MODE_INFO.size }

    // Follow the actual mode when it changes elsewhere (not when the user just swipes)
    LaunchedEffect(currentIndex) { pager.animateScrollToPage(currentIndex) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(state = pager, pageSpacing = 12.dp) { page ->
            val info = MODE_INFO[page]
            val isCurrent = info.mode == current
            Card(
                modifier = Modifier.fillMaxWidth().height(128.dp).clickable(onClick = onOpenModes),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isCurrent) Text("● Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(info.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap to manage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(MODE_INFO.size) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 8.dp else 6.dp).background(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    vm: ProfileListViewModel,
    onOpenApps: () -> Unit,
    onOpenSites: () -> Unit,
    onOpenFeeds: () -> Unit,
    onOpenKeywords: () -> Unit,
) {
    val apps by vm.appsBlockedCount.collectAsStateWithLifecycle()
    val sites by vm.sitesBlockedCount.collectAsStateWithLifecycle()
    val keywords by vm.keywordsOn.collectAsStateWithLifecycle()
    val adult by vm.adultOn.collectAsStateWithLifecycle()
    val reels by vm.reelsOn.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CountRow("📱", "${apps ?: 0}", "Apps blocked", onOpenApps)
        CountRow("🌐", "${sites ?: 0}", "Sites blocked", onOpenSites)
        ToggleRow("🎬", "Reels & Shorts", reels, onConfigure = onOpenFeeds) { vm.setQuick(ProfileListViewModel.Quick.REELS, it) }
        ToggleRow("🔞", "Adult sites", adult, onConfigure = null) { vm.setQuick(ProfileListViewModel.Quick.ADULT, it) }
        ToggleRow("🔤", "Keywords", keywords, onConfigure = onOpenKeywords) { vm.setQuick(ProfileListViewModel.Quick.KEYWORDS, it) }
    }
}

@Composable
private fun Badge(glyph: String) {
    Box(
        Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 20.sp) }
}

/** A row card that shows a count and navigates on tap. */
@Composable
private fun CountRow(glyph: String, count: String, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Badge(glyph)
            Column(Modifier.weight(1f)) {
                Text(count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A row card that toggles on tap; tints up when on. */
@Composable
private fun ToggleRow(glyph: String, label: String, on: Boolean?, onConfigure: (() -> Unit)?, onChange: (Boolean) -> Unit) {
    val checked = on == true
    Card(
        modifier = Modifier.fillMaxWidth().let { m -> if (onConfigure != null) m.clickable(onClick = onConfigure) else m },
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Badge(glyph)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (checked) "On" else "Off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun AddProfileButton(onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("＋", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Add a new profile",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text("→", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BlockerOffBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            "Blocker setup isn't finished. Tap to fix.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPause: (Long) -> Unit,
    onResume: () -> Unit,
) {
    val paused = profile.pausedUntil?.isAfter(java.time.LocalDateTime.now()) == true
    var showPause by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${profile.summary()} · ${if (profile.wholePhone) "whole phone" else "${profile.packages.size} apps"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onToggle)
            }
            if (profile.enabled) {
                if (paused) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Paused until ${formatMinute(profile.pausedUntil!!.hour * 60 + profile.pausedUntil.minute)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onResume) { Text("Resume") }
                    }
                } else {
                    TextButton(onClick = { showPause = true }) { Text("Pause") }
                }
            }
        }
    }
    if (showPause) {
        PauseDialog(
            onDismiss = { showPause = false },
            onPick = {
                onPause(it)
                showPause = false
            },
        )
    }
}

@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val options = listOf(5L to "5 min", 15L to "15 min", 30L to "30 min", 60L to "1 hour", 480L to "8 hours")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pause for") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    TextButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
