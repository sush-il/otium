package dev.sushil.focusblock.ui.profiles

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import dev.sushil.focusblock.domain.model.RuleType
import dev.sushil.focusblock.domain.model.Schedule
import dev.sushil.focusblock.util.Permissions
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTypeSelector(type: RuleType, onChange: (RuleType) -> Unit) {
    val options = listOf(RuleType.ALWAYS to "Always", RuleType.SCHEDULE to "Schedule", RuleType.DAILY_LIMIT to "Daily limit")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = type == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
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
