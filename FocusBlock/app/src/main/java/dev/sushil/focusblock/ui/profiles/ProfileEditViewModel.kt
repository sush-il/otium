package dev.sushil.focusblock.ui.profiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sushil.focusblock.data.InstalledApp
import dev.sushil.focusblock.data.InstalledAppsRepository
import dev.sushil.focusblock.data.ProfileRepository
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType
import dev.sushil.focusblock.domain.model.Schedule
import java.time.LocalDateTime
import dev.sushil.focusblock.ui.common.EditCheck
import dev.sushil.focusblock.ui.common.EditGuard
import kotlinx.coroutines.launch

/** Holds the form while creating ([profileId] = 0) or editing a profile. */
class ProfileEditViewModel(
    private val profileId: Long,
    private val profiles: ProfileRepository,
    private val installedApps: InstalledAppsRepository,
    private val guard: EditGuard,
) : ViewModel() {

    val isNew = profileId == 0L

    var name by mutableStateOf("")
    var type by mutableStateOf(RuleType.ALWAYS)
    var dailyLimitMinutes by mutableIntStateOf(30)
    var hourlyLimitMinutes by mutableIntStateOf(10)
    var launchLimit by mutableIntStateOf(10)
    var blockUntil by mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
    // The UI edits one time window; the data model allows several
    var schedule by mutableStateOf(Schedule(daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60))

    var selected by mutableStateOf(emptySet<String>())
        private set
    var apps by mutableStateOf<List<InstalledApp>?>(null) // null = loading
        private set

    private var enabled = true
    private var original: Profile? = null

    /** Shown under the Save button when the current mode refuses a change. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Non-null while the Lock-mode password dialog is open; runs after a correct password. */
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    init {
        viewModelScope.launch {
            if (!isNew) profiles.get(profileId)?.let {
                original = it
                name = it.name
                type = it.type
                selected = it.packages
                enabled = it.enabled
                it.dailyLimitMinutes?.let { limit -> dailyLimitMinutes = limit }
                it.hourlyLimitMinutes?.let { limit -> hourlyLimitMinutes = limit }
                it.launchLimit?.let { limit -> launchLimit = limit }
                it.blockUntil?.let { until -> blockUntil = until }
                it.schedules.firstOrNull()?.let { s -> schedule = s }
            }
            apps = installedApps.allApps()
        }
    }

    val canSave: Boolean
        get() = name.isNotBlank() && selected.isNotEmpty() &&
            (type != RuleType.SCHEDULE || schedule.daysMask != 0) &&
            (type != RuleType.BLOCK_UNTIL || blockUntil.isAfter(LocalDateTime.now()))

    fun toggleApp(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    fun toggleDay(index: Int) {
        schedule = schedule.copy(daysMask = schedule.daysMask xor (1 shl index))
    }

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        val updated = buildProfile()
        guarded(old = original, new = updated, retry = { save(onSaved) }) {
            profiles.save(updated)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        guarded(old = original, new = null, retry = { delete(onDeleted) }) {
            profiles.delete(profileId)
            onDeleted()
        }
    }

    fun onPasswordAccepted() {
        guard.grantProfiles()
        val retry = passwordNeededFor
        passwordNeededFor = null
        retry?.invoke()
    }

    fun onPasswordDismissed() {
        passwordNeededFor = null
    }

    /** Runs [action] only if the current mode allows changing [old] into [new]. */
    private fun guarded(old: Profile?, new: Profile?, retry: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            error = null
            when (val check = guard.check(old, new)) {
                EditCheck.Allowed -> action()
                EditCheck.NeedsPassword -> passwordNeededFor = retry
                is EditCheck.Forbidden -> error = check.reason
            }
        }
    }

    private fun buildProfile() = Profile(
        id = profileId,
        name = name.trim(),
        enabled = enabled,
        type = type,
        packages = selected,
        // Only keep the settings that belong to the chosen rule type
        dailyLimitMinutes = dailyLimitMinutes.takeIf { type == RuleType.DAILY_LIMIT },
        schedules = if (type == RuleType.SCHEDULE) listOf(schedule) else emptyList(),
        hourlyLimitMinutes = hourlyLimitMinutes.takeIf { type == RuleType.HOURLY_LIMIT },
        launchLimit = launchLimit.takeIf { type == RuleType.LAUNCH_LIMIT },
        blockUntil = blockUntil.takeIf { type == RuleType.BLOCK_UNTIL },
    )
}
