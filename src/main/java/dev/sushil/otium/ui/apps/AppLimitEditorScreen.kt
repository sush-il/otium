package dev.sushil.otium.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.ui.common.AppIcon
import dev.sushil.otium.ui.common.LimitEditorLayout
import dev.sushil.otium.ui.common.LimitRow
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.ui.lock.PasswordDialog

@Composable
fun AppLimitEditorScreen(packageName: String, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val vm: AppLimitEditorViewModel = viewModel(
        factory = viewModelFactory { initializer { AppLimitEditorViewModel(packageName, container.appLimitRepository, container.editGuard) } },
    )
    val rules by vm.rules.collectAsStateWithLifecycle()
    val label = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
        )
    }
    if (vm.passwordNeededFor != null) {
        PasswordDialog("remove this limit", { vm.onPasswordAccepted() }, vm::dismissPassword)
    }

    LimitEditorLayout(
        title = label,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppIcon(packageName, size = 52.dp)
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        note = null,
        rows = rules.map { LimitRow(it.id, it.type, it.summary()) },
        form = vm,
        siteMode = false,
        onLoadForEdit = { id -> rules.firstOrNull { it.id == id }?.let(vm::loadForEdit) },
        onRemove = vm::remove,
        onBack = onBack,
    )
}
