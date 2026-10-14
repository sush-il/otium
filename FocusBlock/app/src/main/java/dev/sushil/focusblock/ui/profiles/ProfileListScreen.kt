package dev.sushil.focusblock.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.ui.common.appContainer
import dev.sushil.focusblock.util.Permissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    onOpenProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val container = appContainer()
    val vm: ProfileListViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileListViewModel(container.profileRepository) } },
    )
    val profiles by vm.profiles.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var blockerOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        blockerOn = Permissions.hasRequired(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Focus Block") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewProfile) { Text("New profile") }
        },
    ) { padding ->
        val list = profiles
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!blockerOn) item { BlockerOffBanner(onOpenSetup) }
                if (list.isEmpty()) item {
                    Text(
                        "No profiles yet. Tap \"New profile\" to choose apps to block.",
                        modifier = Modifier.padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                items(list, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onClick = { onOpenProfile(profile.id) },
                        onToggle = { vm.setEnabled(profile, it) },
                    )
                }
            }
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
private fun ProfileCard(profile: Profile, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${profile.summary()} · ${profile.packages.size} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = profile.enabled, onCheckedChange = onToggle)
        }
    }
}
