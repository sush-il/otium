package com.focusblock.ui.apps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.AppLimitRepository
import com.focusblock.domain.model.AppLimit
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule
import com.focusblock.ui.common.EditCheck
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Add/remove the stacked rules on one app. Adding tightens (free); removing loosens (guarded). */
class AppLimitEditorViewModel(
    private val pkg: String,
    private val repo: AppLimitRepository,
    private val guard: EditGuard,
) : ViewModel() {

    val rules: StateFlow<List<AppLimit>> =
        repo.limits.map { all -> all.filter { it.packageName == pkg } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Form for a new rule
    var type by mutableStateOf(RuleType.DAILY_LIMIT)
    var dailyLimitMinutes by mutableIntStateOf(30)
    var hourlyLimitMinutes by mutableIntStateOf(10)
    var launchLimit by mutableIntStateOf(10)
    var blockUntil by mutableStateOf(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
    var schedule by mutableStateOf(Schedule(daysMask = 0b0011111, startMinute = 9 * 60, endMinute = 17 * 60))

    var error by mutableStateOf<String?>(null)
        private set
    var passwordNeededFor by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun addCurrent() {
        viewModelScope.launch {
            repo.add(
                AppLimit(
                    packageName = pkg,
                    type = type,
                    dailyLimitMinutes = dailyLimitMinutes.takeIf { type == RuleType.DAILY_LIMIT },
                    hourlyLimitMinutes = hourlyLimitMinutes.takeIf { type == RuleType.HOURLY_LIMIT },
                    launchLimit = launchLimit.takeIf { type == RuleType.LAUNCH_LIMIT },
                    blockUntil = blockUntil.takeIf { type == RuleType.BLOCK_UNTIL },
                    schedule = schedule.takeIf { type == RuleType.SCHEDULE },
                ),
            )
        }
    }

    fun blockAlways() {
        viewModelScope.launch { repo.add(AppLimit(packageName = pkg, type = RuleType.ALWAYS)) }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            when (val check = guard.checkLoosen()) {
                EditCheck.Allowed -> repo.removeRule(id)
                EditCheck.NeedsPassword -> passwordNeededFor = { remove(id) }
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
