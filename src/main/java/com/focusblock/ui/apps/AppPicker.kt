package com.focusblock.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusblock.data.InstalledApp
import com.focusblock.ui.common.AppIcon

/** Searchable checklist of installed apps. [apps] null means still loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(
    apps: List<InstalledApp>?,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    title: String = "Choose apps",
) {
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title (${selected.size})") },
                actions = { TextButton(onClick = onDone) { Text("Done") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp),
            )
            if (apps == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIcon(app.packageName)
                        Text(app.label, modifier = Modifier.weight(1f))
                        Checkbox(checked = app.packageName in selected, onCheckedChange = { onToggle(app.packageName) })
                    }
                }
            }
        }
    }
}
