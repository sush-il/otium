package dev.sushil.otium.ui.modes

import dev.sushil.otium.ui.common.BackButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.sushil.otium.admin.FocusAdminReceiver
import dev.sushil.otium.domain.EmergencyUnlock
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.LockOptions
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.StrictOptions
import dev.sushil.otium.ui.common.appContainer
import dev.sushil.otium.domain.NumberChallenges
import dev.sushil.otium.ui.lock.ChallengeView
import dev.sushil.otium.ui.lock.DigitsField
import dev.sushil.otium.ui.lock.PasswordDialog
import dev.sushil.otium.ui.profiles.ScheduleEditor
import dev.sushil.otium.ui.profiles.summary
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModesScreen(onBack: () -> Unit, onOpenEmergency: () -> Unit) {
    val container = appContainer()
    val vm: ModesViewModel = viewModel(factory = viewModelFactory { initializer { ModesViewModel(container.modeStore) } })
    val state by vm.state.collectAsStateWithLifecycle()

    var showLockSheet by remember { mutableStateOf(false) }
    var showStrictSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modes") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeCard("Normal", "Change anything freely.", s.mode == Mode.NORMAL)
            ModeCard("Lock", "Type a long number to switch to Normal mode and change things.", s.mode == Mode.LOCK)
            ModeCard("Strict", "Locked until it ends. No undo.", s.mode == Mode.STRICT)

            when (s.mode) {
                Mode.NORMAL -> {
                    // A pending automatic re-lock offers one tap to lock now; otherwise Lock is set up (again) from scratch
                    val relockAt = s.relockAt.takeIf { s.hasLock }
                    if (relockAt != null) {
                        Button(onClick = vm::relock, modifier = Modifier.fillMaxWidth()) { Text("Turn Lock mode back on") }
                        Text(
                            "Lock turns back on by itself at " +
                                java.time.Instant.ofEpochMilli(relockAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                                    .let { "%02d:%02d".format(it.hour, it.minute) } + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FilledTonalButton(onClick = { showLockSheet = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (s.hasLock) "Set up Lock mode again" else "Set up Lock mode")
                        }
                    }
                    FilledTonalButton(onClick = { showStrictSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Set up Strict mode")
                    }
                }
                Mode.LOCK -> {
                    LockActive(vm, s)
                    FilledTonalButton(onClick = { showStrictSheet = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Switch to Strict mode")
                    }
                }
                Mode.STRICT -> StrictActive(vm, s)
            }
            if (s.mode == Mode.STRICT) {
                HorizontalDivider()
                EmergencySummary(s, onOpenEmergency)
            }
        }
    }

    if (showLockSheet) {
        ModeSheet(onDismiss = { showLockSheet = false }) { LockSetup(vm, onDone = { showLockSheet = false }) }
    }
    if (showStrictSheet) {
        ModeSheet(onDismiss = { showStrictSheet = false }) { StrictSetup(vm, onDone = { showStrictSheet = false }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun ModeCard(title: String, body: String, current: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (current) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current) Text("● Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- Lock ----------

private const val SAMPLE_DIGITS = 4

@Composable
private fun LockSetup(vm: ModesViewModel, onDone: () -> Unit = {}) {
    var length by remember { mutableIntStateOf(15) }
    var options by remember { mutableStateOf(LockOptions()) }
    // A short sample to confirm; the real numbers use the length chosen below
    val sample = remember { NumberChallenges.generate(SAMPLE_DIGITS) }
    var typed by remember { mutableStateOf("") }

    Text("Turn on Lock mode", style = MaterialTheme.typography.titleMedium)
    Text("To get out of Lock mode you'll type a long number. Try a short one:", style = MaterialTheme.typography.bodyMedium)
    ChallengeView(sample)
    DigitsField(typed, { typed = it }, "Type the number", SAMPLE_DIGITS)
    Text("Digits to unlock later", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 20, 30, 40).forEach { n ->
            FilterChip(selected = length == n, onClick = { length = n }, label = { Text("$n") })
        }
    }
    LockOptionSwitches(options) { options = it }
    Button(
        onClick = {
            vm.confirmLock(length, options)
            onDone()
        },
        enabled = typed == sample.digits,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Turn on Lock mode") }
}

@Composable
private fun LockOptionSwitches(options: LockOptions, onChange: (LockOptions) -> Unit) {
    OptionSwitch("Number to open Otium", options.lockApp) { onChange(options.copy(lockApp = it)) }
    OptionSwitch("Number to open phone Settings", options.lockSettings) { onChange(options.copy(lockSettings = it)) }
    OptionSwitch("Number to loosen or delete profiles", options.lockProfiles) { onChange(options.copy(lockProfiles = it)) }
    Text(
        "Uninstalling always needs the number in Lock mode.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LockActive(vm: ModesViewModel, s: ModeState) {
    // Every change here loosens Lock mode, so each one asks for the number
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    Text("Lock mode is on", style = MaterialTheme.typography.titleMedium)
    LockOptionSwitches(s.lock) { new -> pendingAction = { vm.setLockOptions(new) } }
    OutlinedButton(onClick = { pendingAction = {} }, modifier = Modifier.fillMaxWidth()) {
        Text("Switch to Normal mode")
    }
    pendingAction?.let { action ->
        PasswordDialog(
            action = "make this change",
            onUnlocked = {
                action()
                pendingAction = null
            },
            onDismiss = { pendingAction = null },
        )
    }
}

// ---------- Strict ----------

private enum class StrictEnd { PRESET, CUSTOM, SCHEDULE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrictSetup(vm: ModesViewModel, onDone: () -> Unit = {}) {
    val context = LocalContext.current
    var endKind by remember { mutableStateOf(StrictEnd.PRESET) }
    var presetHours by remember { mutableIntStateOf(24) }
    var customEnd by remember { mutableStateOf<LocalDateTime?>(null) }
    var schedule by remember { mutableStateOf(Schedule(daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60)) }
    var options by remember { mutableStateOf(StrictOptions()) }
    var confirmText by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTimeFor by remember { mutableStateOf<java.time.LocalDate?>(null) }

    Text("Start Strict mode", style = MaterialTheme.typography.titleMedium)
    Text("Ends:", style = MaterialTheme.typography.bodyLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESETS.forEach { (hours, label) ->
            FilterChip(
                selected = endKind == StrictEnd.PRESET && presetHours == hours,
                onClick = { endKind = StrictEnd.PRESET; presetHours = hours },
                label = { Text(label) },
            )
        }
        FilterChip(
            selected = endKind == StrictEnd.CUSTOM,
            onClick = { pickingDate = true },
            label = { Text(customEnd?.let { "Until " + it.format(END_FORMAT) } ?: "Pick date…") },
        )
        FilterChip(
            selected = endKind == StrictEnd.SCHEDULE,
            onClick = { endKind = StrictEnd.SCHEDULE },
            label = { Text("Follow a schedule") },
        )
    }
    if (endKind == StrictEnd.SCHEDULE) {
        Text(
            "Strict is active only while this schedule runs, every week, until you switch it off outside the schedule.",
            style = MaterialTheme.typography.bodySmall,
        )
        ScheduleEditor(
            schedule = schedule,
            onToggleDay = { i -> schedule = schedule.copy(daysMask = schedule.daysMask xor (1 shl i)) },
            onChange = { schedule = it },
        )
    }

    Text("Also block:", style = MaterialTheme.typography.bodyLarge)
    OptionSwitch("Phone Settings", options.blockSettings) { options = options.copy(blockSettings = it) }
    OptionSwitch("Recent apps screen", options.blockRecents) { options = options.copy(blockRecents = it) }
    OptionSwitch("Power-off menu (may not work on every phone)", options.blockPowerMenu) { options = options.copy(blockPowerMenu = it) }
    OptionSwitch("Play Store (its Uninstall button skips our protection)", options.blockPlayStore) {
        options = options.copy(blockPlayStore = it)
    }

    val adminActive = FocusAdminReceiver.isActive(context)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (adminActive) "Uninstall protection is on ✓" else "Recommended: turn on uninstall protection",
                style = MaterialTheme.typography.titleSmall,
            )
            if (!adminActive) {
                Text("Android's device-admin setting stops the app being uninstalled until it's switched off in Settings.")
                OutlinedButton(onClick = { context.startActivity(FocusAdminReceiver.activateIntent(context)) }) {
                    Text("Turn on")
                }
            }
        }
    }

    Text(
        "While Strict is on you can't switch it off, loosen or delete profiles, or uninstall Otium. " +
            "The only early exit is the emergency unlock, which takes at least 3 days. Type I understand to continue.",
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedTextField(
        value = confirmText, onValueChange = { confirmText = it },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("I understand") },
    )
    val endValid = when (endKind) {
        StrictEnd.PRESET -> true
        StrictEnd.CUSTOM -> customEnd?.isAfter(LocalDateTime.now()) == true
        StrictEnd.SCHEDULE -> schedule.daysMask != 0
    }
    Button(
        onClick = {
            val until = when (endKind) {
                StrictEnd.PRESET -> System.currentTimeMillis() + presetHours * 3_600_000L
                StrictEnd.CUSTOM -> customEnd!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                StrictEnd.SCHEDULE -> null
            }
            vm.startStrict(until, schedule.takeIf { endKind == StrictEnd.SCHEDULE }, options)
            confirmText = ""
            onDone()
        },
        enabled = endValid && confirmText.trim().equals("I understand", ignoreCase = true),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Start Strict mode") }

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
                    customEnd = date.atTime(LocalTime.of(timeState.hour, timeState.minute))
                    endKind = StrictEnd.CUSTOM
                    pickingTimeFor = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTimeFor = null }) { Text("Cancel") } },
            text = { TimePicker(timeState) },
        )
    }
}

@Composable
private fun StrictActive(vm: ModesViewModel, s: ModeState) {
    val context = LocalContext.current
    val active = s.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())
    Text("Strict mode is on", style = MaterialTheme.typography.titleMedium)
    Text(
        when {
            s.strictUntil != null -> "Ends " + Instant.ofEpochMilli(s.strictUntil).atZone(ZoneId.systemDefault()).format(END_FORMAT) +
                ". It switches back to Normal by itself."
            s.strictSchedule != null -> "Active during: ${s.strictSchedule.summary()}" +
                if (active) " (running now)" else " (not running now)"
            else -> ""
        },
    )
    Text(
        "Blocking: uninstalling, loosening profiles" +
            (if (s.strict.blockSettings) ", Settings" else "") +
            (if (s.strict.blockRecents) ", Recent apps" else "") +
            (if (s.strict.blockPowerMenu) ", power menu" else "") +
            (if (s.strict.blockPlayStore) ", Play Store" else ""),
        style = MaterialTheme.typography.bodyMedium,
    )
    // A schedule-based Strict mode can be left while its schedule isn't running
    if (!active) {
        OutlinedButton(
            onClick = {
                vm.switchToNormal()
                FocusAdminReceiver.deactivate(context)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Switch to Normal mode") }
    }
}

// ---------- Emergency ----------

@Composable
private fun EmergencySummary(s: ModeState, onOpen: () -> Unit) {
    val now = System.currentTimeMillis()
    val recent = s.emergency.history.count { now - it < EmergencyUnlock.HISTORY_MS }
    Text("Emergency unlock", style = MaterialTheme.typography.titleMedium)
    Text(
        when (s.emergency.phase) {
            EmergencyState.Phase.IDLE ->
                "Need out early? Takes ${formatHours(EmergencyUnlock.waitFor(s.emergency.history, now))} " +
                    "of phone-on time. Used $recent time(s) in the last 30 days."
            EmergencyState.Phase.WAITING -> "Waiting: ${formatHours(s.emergency.remainingMs)} left."
            EmergencyState.Phase.WINDOW -> "Ready now, for a few minutes."
        },
    )
    OutlinedButton(onClick = onOpen) { Text("Open emergency unlock") }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun formatHours(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
}

private val PRESETS = listOf(1 to "1 hour", 6 to "6 hours", 24 to "1 day", 72 to "3 days", 168 to "1 week", 720 to "30 days")
private val END_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
