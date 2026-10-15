package dev.sushil.otium.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.ui.common.BackButton
import dev.sushil.otium.ui.common.PauseButton
import dev.sushil.otium.ui.common.PauseDialogs
import dev.sushil.otium.ui.common.sitePauseKey
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.sites.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebContentScreen(onBack: () -> Unit, onOpenSite: (String) -> Unit) {
    val container = appContainer()
    val vm: WebContentViewModel = viewModel(
        factory = viewModelFactory { initializer { WebContentViewModel(container.settingsStore, container.siteLimitRepository, container.editGuard) } },
    )
    val sites by vm.sites.collectAsStateWithLifecycle()
    val paused by vm.pause.paused.collectAsStateWithLifecycle()
    PauseDialogs(vm.pause)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Websites") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("Blocked sites") }
            item { AddField("Add a site, e.g. youtube.com") { onOpenSite(vm.normalizeHost(it)) } }
            item {
                if (sites.isEmpty()) {
                    EmptyHint("No sites yet. Add one above to set its limits.")
                } else {
                    GroupCard {
                        sites.toList().forEachIndexed { i, (host, rules) ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenSite(host) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(host, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        (if (sitePauseKey(host) in paused) "Paused · " else "") + rules.joinToString(" · ") { it.summary() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                PauseButton(sitePauseKey(host) in paused) { vm.pause.toggle(sitePauseKey(host)) }
                                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column { content() } }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/** Full-width field with an inline add action, so the list below stays the focus. */
@Composable
private fun AddField(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = ::submit) { Icon(Icons.Default.Add, contentDescription = "Add") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
