package dev.sushil.otium.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.sushil.otium.domain.model.STRICT_NO_END
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.ui.profiles.ScheduleEditor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class StrictEnd { PRESET, CUSTOM, SCHEDULE, NONE }

/** What the user picked for when a Strict window ends. Shared by the Strict mode and Adult sites screens. */
class StrictEndState {
    var kind by mutableStateOf(StrictEnd.CUSTOM) // invalid until a date is picked, so the user must choose
    var presetDays by mutableIntStateOf(1)
    var customEnd by mutableStateOf<LocalDateTime?>(null)
    var schedule by mutableStateOf(Schedule(daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60))

    val valid: Boolean
        get() = when (kind) {
            StrictEnd.PRESET -> true
            StrictEnd.CUSTOM -> customEnd?.isAfter(LocalDateTime.now()) == true
            StrictEnd.SCHEDULE -> schedule.daysMask != 0
            StrictEnd.NONE -> true
        }

    /** End time in epoch ms, [STRICT_NO_END] for no end, or null when following a schedule. */
    fun untilMs(): Long? = when (kind) {
        StrictEnd.PRESET -> System.currentTimeMillis() + presetDays * 24 * 3_600_000L
        StrictEnd.CUSTOM -> customEnd!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        StrictEnd.SCHEDULE -> null
        StrictEnd.NONE -> STRICT_NO_END
    }

    fun scheduleOrNull(): Schedule? = schedule.takeIf { kind == StrictEnd.SCHEDULE }
}

@Composable
fun rememberStrictEndState() = remember { StrictEndState() }

private val PRESETS = listOf(1 to "1 day", 7 to "7 days", 30 to "30 days")
val StrictEndFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

/** "until Tue 10 Nov, 09:00", or "until unlocked" for [STRICT_NO_END]. */
fun describeStrictEnd(until: Long): String =
    if (until == STRICT_NO_END) "until unlocked"
    else "until " + Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).format(StrictEndFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictEndPicker(state: StrictEndState, scheduleHint: String) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTimeFor by remember { mutableStateOf<LocalDate?>(null) }

    Text("Ends:", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESETS.forEach { (days, label) ->
            FilterChip(
                selected = state.kind == StrictEnd.PRESET && state.presetDays == days,
                onClick = { state.kind = StrictEnd.PRESET; state.presetDays = days },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = state.kind == StrictEnd.CUSTOM,
            onClick = { pickingDate = true },
            label = { Text(state.customEnd?.let { "Until " + it.format(StrictEndFormat) } ?: "Pick date & time…") },
        )
        FilterChip(
            selected = state.kind == StrictEnd.SCHEDULE,
            onClick = { state.kind = StrictEnd.SCHEDULE },
            label = { Text("Follow a schedule") },
        )
        FilterChip(
            selected = state.kind == StrictEnd.NONE,
            onClick = { state.kind = StrictEnd.NONE },
            label = { Text("Until unlocked") },
        )
    }
    if (state.kind == StrictEnd.NONE) {
        Text(
            "No end date. Stays on until you use the emergency unlock.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.kind == StrictEnd.SCHEDULE) {
        Text(scheduleHint, style = MaterialTheme.typography.bodySmall)
        ScheduleEditor(
            schedule = state.schedule,
            onToggleDay = { i -> state.schedule = state.schedule.copy(daysMask = state.schedule.daysMask xor (1 shl i)) },
            onChange = { state.schedule = it },
        )
    }

    if (pickingDate) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickingDate = false
                    // The picker returns UTC midnight of the chosen day
                    pickingTimeFor = dateState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    }
    pickingTimeFor?.let { date ->
        val timeState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTimeFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.customEnd = date.atTime(LocalTime.of(timeState.hour, timeState.minute))
                    state.kind = StrictEnd.CUSTOM
                    pickingTimeFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTimeFor = null }) { Text("Cancel") } },
            text = { TimePicker(timeState) },
        )
    }
}
