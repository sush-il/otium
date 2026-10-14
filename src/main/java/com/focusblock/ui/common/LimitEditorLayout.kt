package com.focusblock.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusblock.domain.model.RuleType
import com.focusblock.ui.profiles.BlockUntilEditor
import com.focusblock.ui.profiles.DailyLimitEditor
import com.focusblock.ui.profiles.LaunchLimitEditor
import com.focusblock.ui.profiles.MinutesLimitEditor
import com.focusblock.ui.profiles.ScheduleEditor
import kotlinx.coroutines.launch

/** One existing limit as shown in the list. */
data class LimitRow(val id: Long, val type: RuleType, val summary: String)

private data class LimitKind(val type: RuleType, val glyph: String, val title: String, val hint: String)

private fun kinds(siteMode: Boolean) = listOf(
    LimitKind(RuleType.DAILY_LIMIT, "⏳", "Daily time", "Minutes per day"),
    LimitKind(RuleType.HOURLY_LIMIT, "⏱️", "Hourly time", "Minutes per hour"),
    LimitKind(RuleType.LAUNCH_LIMIT, "🔁", if (siteMode) "Visit limit" else "Open limit", "Times per day"),
    LimitKind(RuleType.SCHEDULE, "📅", "Schedule", "Blocked during set hours"),
    LimitKind(RuleType.BLOCK_UNTIL, "🗓️", "Block until", "Blocked until a date"),
    LimitKind(RuleType.ALWAYS, "⛔", "Block completely", "Never allow it"),
)

private fun kindOf(type: RuleType, siteMode: Boolean) = kinds(siteMode).first { it.type == type }

/**
 * Shared screen for an app's or a website's stacked limits: a header, a prominent
 * "Add a limit" button, the list of limits, and a two-step sheet (pick a kind → set it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitEditorLayout(
    title: String,
    header: @Composable () -> Unit,
    note: String?,
    rows: List<LimitRow>,
    form: LimitForm,
    siteMode: Boolean,
    onLoadForEdit: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // null = sheet closed; PICK = choose a kind; a RuleType = configure that kind
    var sheet by remember { mutableStateOf<SheetStep?>(null) }
    val existing = rows.map { it.type }.toSet()

    fun saved(message: String) {
        sheet = null
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun openKind(type: RuleType) {
        // Picking a kind that's already set edits it, with its current values
        rows.firstOrNull { it.type == type }?.let { onLoadForEdit(it.id) }
        form.type = type
        if (type == RuleType.ALWAYS) {
            form.addCurrent()
            saved("Blocked completely")
        } else {
            sheet = SheetStep.Configure(type, fromPicker = true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onClick = onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header()
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { sheet = SheetStep.Pick }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add a limit", modifier = Modifier.padding(start = 8.dp))
            }

            if (rows.isEmpty()) {
                Text(
                    "No limits yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    if (rows.size == 1) "1 limit" else "${rows.size} limits — blocked if any applies",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                rows.forEach { row ->
                    val kind = kindOf(row.type, siteMode)
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onLoadForEdit(row.id)
                            form.type = row.type
                            if (row.type != RuleType.ALWAYS) sheet = SheetStep.Configure(row.type, fromPicker = false)
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Glyph(kind.glyph)
                            Column(Modifier.weight(1f)) {
                                Text(kind.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(row.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onRemove(row.id) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    val step = sheet ?: return
    ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                SheetStep.Pick -> {
                    Text("What kind of limit?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    kinds(siteMode).forEach { kind ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { openKind(kind.type) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Glyph(kind.glyph)
                                Column(Modifier.weight(1f)) {
                                    Text(kind.title, style = MaterialTheme.typography.titleMedium)
                                    Text(kind.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (kind.type in existing) {
                                    Text("Set", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                is SheetStep.Configure -> {
                    val kind = kindOf(step.type, siteMode)
                    val editing = step.type in existing
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Glyph(kind.glyph)
                        Text(kind.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    when (step.type) {
                        RuleType.DAILY_LIMIT -> DailyLimitEditor(form.dailyLimitMinutes) { form.dailyLimitMinutes = it }
                        RuleType.HOURLY_LIMIT -> MinutesLimitEditor("Per clock hour", form.hourlyLimitMinutes, max = 55) { form.hourlyLimitMinutes = it }
                        RuleType.LAUNCH_LIMIT -> LaunchLimitEditor(form.launchLimit) { form.launchLimit = it }
                        RuleType.SCHEDULE -> ScheduleEditor(
                            form.schedule,
                            onToggleDay = { i -> form.schedule = form.schedule.copy(daysMask = form.schedule.daysMask xor (1 shl i)) },
                            onChange = { form.schedule = it },
                        )
                        RuleType.BLOCK_UNTIL -> BlockUntilEditor(form.blockUntil) { form.blockUntil = it }
                        RuleType.ALWAYS -> Unit
                    }
                    Button(
                        onClick = {
                            form.type = step.type
                            form.addCurrent()
                            saved(if (editing) "Limit updated" else "Limit added")
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(if (editing) "Save" else "Add limit") }
                    if (step.fromPicker) {
                        FilledTonalButton(onClick = { sheet = SheetStep.Pick }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose a different kind")
                        }
                    }
                }
            }
        }
    }
}

private sealed interface SheetStep {
    data object Pick : SheetStep
    data class Configure(val type: RuleType, val fromPicker: Boolean) : SheetStep
}

@Composable
private fun Glyph(glyph: String) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 18.sp) }
}
