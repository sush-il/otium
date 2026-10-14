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
import kotlinx.coroutines.launch

/** Holds the form while creating ([profileId] = 0) or editing a profile. */
class ProfileEditViewModel(
    private val profileId: Long,
    private val profiles: ProfileRepository,
    private val installedApps: InstalledAppsRepository,
) : ViewModel() {

    val isNew = profileId == 0L

    var name by mutableStateOf("")
    var type by mutableStateOf(RuleType.ALWAYS)
    var dailyLimitMinutes by mutableIntStateOf(30)
    // The UI edits one time window; the data model allows several
    var schedule by mutableStateOf(Schedule(daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60))

    var selected by mutableStateOf(emptySet<String>())
        private set
    var apps by mutableStateOf<List<InstalledApp>?>(null) // null = loading
        private set

    private var enabled = true

    init {
        viewModelScope.launch {
            if (!isNew) profiles.get(profileId)?.let {
                name = it.name
                type = it.type
                selected = it.packages
                enabled = it.enabled
                it.dailyLimitMinutes?.let { limit -> dailyLimitMinutes = limit }
                it.schedules.firstOrNull()?.let { s -> schedule = s }
            }
            apps = installedApps.allApps()
        }
    }

    val canSave: Boolean
        get() = name.isNotBlank() && selected.isNotEmpty() &&
            (type != RuleType.SCHEDULE || schedule.daysMask != 0)

    fun toggleApp(pkg: String) {
        selected = if (pkg in selected) selected - pkg else selected + pkg
    }

    fun toggleDay(index: Int) {
        schedule = schedule.copy(daysMask = schedule.daysMask xor (1 shl index))
    }

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            profiles.save(
                Profile(
                    id = profileId,
                    name = name.trim(),
                    enabled = enabled,
                    type = type,
                    packages = selected,
                    // Only keep the settings that belong to the chosen rule type
                    dailyLimitMinutes = dailyLimitMinutes.takeIf { type == RuleType.DAILY_LIMIT },
                    schedules = if (type == RuleType.SCHEDULE) listOf(schedule) else emptyList(),
                ),
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            profiles.delete(profileId)
            onDeleted()
        }
    }
}
