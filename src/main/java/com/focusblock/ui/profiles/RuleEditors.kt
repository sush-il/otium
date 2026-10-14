package com.focusblock.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule
import com.focusblock.util.Permissions
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTypeSelector(type: RuleType, onChange: (RuleType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = ruleTypeLabel(type),
            onValueChange = {},
            readOnly = true,
            label = { Text("Rule type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RuleType.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(ruleTypeLabel(value)) },
                    onClick = {
                        onChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Minutes slider shared by the time-limit rules. */
@Composable
fun MinutesLimitEditor(label: String, minutes: Int, max: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $minutes min", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange((it / 5).roundToInt() * 5) },
            valueRange = 5f..max.toFloat(),
            steps = (max - 5) / 5 - 1,
        )
        UsageAccessWarning()
    }
}

@Composable
fun LaunchLimitEditor(count: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$count opens per day, for each app", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = count.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 1f..50f,
            steps = 48,
        )
        UsageAccessWarning()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockUntilEditor(until: java.time.LocalDateTime, onChange: (java.time.LocalDateTime) -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("These apps stay blocked until:", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { pickingDate = true }) { Text(until.toLocalDate().toString()) }
            OutlinedButton(onClick = { pickingTime = true }) { Text(formatMinute(until.hour * 60 + until.minute)) }
        }
        if (until.isBefore(java.time.LocalDateTime.now())) {
            Text("That time is in the past.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = until.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        onChange(until.withYear(d.year).withDayOfYear(d.dayOfYear))
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = until.hour, initialMinute = until.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(until.withHour(state.hour).withMinute(state.minute))
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun ScheduleEditor(schedule: Schedule, onToggleDay: (Int) -> Unit, onChange: (Schedule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { i ->
                FilterChip(
                    selected = schedule.daysMask and (1 shl i) != 0,
                    onClick = { onToggleDay(i) },
                    label = { Text(dayShortName(i)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeButton("From", schedule.startMinute) { onChange(schedule.copy(startMinute = it)) }
            TimeButton("To", schedule.endMinute) { onChange(schedule.copy(endMinute = it)) }
        }
        if (schedule.endMinute <= schedule.startMinute) {
            Text(
                "Ends the next day (crosses midnight).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(label: String, minute: Int, onPicked: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label ${formatMinute(minute)}") }
    if (!open) return

    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                onPicked(state.hour * 60 + state.minute)
                open = false
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        text = { TimePicker(state) },
    )
}

@Composable
fun DailyLimitEditor(minutes: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$minutes min per day, for each app", style = MaterialTheme.typography.bodyLarge)
        // 5 min to 4 h in 5-minute steps
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange((it / 5).roundToInt() * 5) },
            valueRange = 5f..240f,
            steps = (240 - 5) / 5 - 1,
        )
        UsageAccessWarning()
    }
}

/** Daily limits can't work without usage access, so point the user to it. */
@Composable
private fun UsageAccessWarning() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        granted = Permissions.hasUsageAccess(context)
        onPauseOrDispose { }
    }
    if (granted) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Daily limits need Usage access to know how long apps were used. Until it's on, these apps stay blocked.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = { context.startActivity(Permissions.usageAccessIntent()) }) {
                Text("Open Usage access")
            }
        }
    }
}
