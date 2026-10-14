package com.focusblock.ui.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.focusblock.ui.common.LimitEditorLayout
import com.focusblock.ui.common.LimitRow
import com.focusblock.ui.common.appContainer
import com.focusblock.ui.lock.PasswordDialog

@Composable
fun SiteLimitEditorScreen(host: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm: SiteLimitEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { SiteLimitEditorViewModel(host, container.siteLimitRepository, container.editGuard) } },
    )
    val rules by vm.rules.collectAsStateWithLifecycle()

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog("Lock mode: removing a limit needs your password.", { vm.onPasswordAccepted() }, vm::dismissPassword)
    }

    LimitEditorLayout(
        title = host,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("🌐", style = MaterialTheme.typography.headlineMedium)
                Text(host, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        note = "Website time is estimated from the browser, so time limits are approximate.",
        rows = rules.map { LimitRow(it.id, it.type, it.summary()) },
        form = vm,
        siteMode = true,
        onLoadForEdit = { id -> rules.firstOrNull { it.id == id }?.let(vm::loadForEdit) },
        onRemove = vm::remove,
        onBack = onBack,
    )
}
