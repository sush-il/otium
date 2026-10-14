package com.focusblock.ui.settings

import com.focusblock.ui.common.BackButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun WebContentScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: WebContentViewModel = viewModel(
        factory = viewModelFactory { initializer { WebContentViewModel(container.settingsStore) } },
    )
    val sites by vm.sites.collectAsStateWithLifecycle()
    val keywords by vm.keywords.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Websites & keywords") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Blocked while any supported browser is open. Enter a site like instagram.com, or a keyword.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { AddRow(label = "Add a site (e.g. youtube.com)", onAdd = vm::addSite) }
            items(sites.orEmpty().toList(), key = { "s$it" }) { ValueRow(it) { vm.removeSite(it) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { AddRow(label = "Add a keyword", onAdd = vm::addKeyword) }
            items(keywords.orEmpty().toList(), key = { "k$it" }) { ValueRow(it) { vm.removeKeyword(it) } }
        }
    }
}

@Composable
private fun AddRow(label: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = {
            if (text.isNotBlank()) {
                onAdd(text)
                text = ""
            }
        }) { Text("Add") }
    }
}

@Composable
private fun ValueRow(value: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(value, Modifier.weight(1f))
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}
