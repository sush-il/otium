package com.focusblock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.data.SettingsStore
import com.focusblock.ui.common.BackButton
import com.focusblock.ui.common.EditCheck
import com.focusblock.ui.common.EditGuard
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KeywordsViewModel(private val settings: SettingsStore, private val guard: EditGuard) : ViewModel() {
    val keywords: StateFlow<Set<String>?> =
        settings.blockedKeywords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val enabled: StateFlow<Boolean?> =
        settings.keywordBlockingEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var error by mutableStateOf<String?>(null)
        private set
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun add(raw: String) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) return
        viewModelScope.launch { settings.setBlockedKeywords(settings.blockedKeywords.first() + keyword) }
    }

    // Removing a keyword or turning blocking off loosens, so they follow the mode rules
    fun remove(keyword: String) = guarded { settings.setBlockedKeywords(settings.blockedKeywords.first() - keyword) }

    fun setEnabled(on: Boolean) {
        if (on) viewModelScope.launch { settings.setKeywordBlockingEnabled(true) }
        else guarded { settings.setKeywordBlockingEnabled(false) }
    }

    private fun guarded(action: suspend () -> Unit) {
        viewModelScope.launch {
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> action()
                EditCheck.NeedsPassword -> passwordNeededFor = { guarded(action) }
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    fun onPasswordAccepted() {
        guard.grantProfiles()
        val retry = passwordNeededFor
        passwordNeededFor = null
        retry?.invoke()
    }

    fun dismissPassword() { passwordNeededFor = null }
    fun dismissError() { error = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: KeywordsViewModel = viewModel(
        factory = viewModelFactory { initializer { KeywordsViewModel(container.settingsStore, container.editGuard) } },
    )
    val keywords by vm.keywords.collectAsStateWithLifecycle()
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog("Lock mode: this change needs your password.", { vm.onPasswordAccepted() }, vm::dismissPassword)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Keywords") }, navigationIcon = { BackButton(onClick = onBack) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Block keywords", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Blocks pages and searches containing these words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = enabled == true, onCheckedChange = vm::setEnabled)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Add a keyword") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { vm.add(text); text = "" }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val list = keywords.orEmpty().sorted()
                if (list.isEmpty()) {
                    Text("No keywords yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column {
                            list.forEachIndexed { i, kw ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(kw, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                    TextButton(onClick = { vm.remove(kw) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
