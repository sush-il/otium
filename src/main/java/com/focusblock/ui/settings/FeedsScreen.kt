package com.focusblock.ui.settings

import com.focusblock.ui.common.BackButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.ui.common.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: FeedsViewModel = viewModel(
        factory = viewModelFactory { initializer { FeedsViewModel(container.settingsStore) } },
    )
    val shorts by vm.youtubeShorts.collectAsStateWithLifecycle()
    val reels by vm.instagramReels.collectAsStateWithLifecycle()
    val fbReels by vm.facebookReels.collectAsStateWithLifecycle()
    val adult by vm.adult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feeds & content") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "When on, Otium closes these feeds with Back as soon as they open. The rest of the app stays usable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow("Block YouTube Shorts", shorts, vm::setYoutubeShorts)
            ToggleRow("Block Instagram Reels", reels, vm::setInstagramReels)
            ToggleRow("Block Facebook Reels", fbReels, vm::setFacebookReels)
            HorizontalDivider()
            ToggleRow("Block adult sites in browsers", adult, vm::setAdult)
            Text(
                "Uses a built-in starter list of adult sites and keywords. These detections rely on the app's current " +
                    "screen layout and may stop working when YouTube, Instagram or Facebook update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean?, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked == true, onCheckedChange = onChange)
    }
}
