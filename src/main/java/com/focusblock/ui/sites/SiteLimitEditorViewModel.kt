package com.focusblock.ui.sites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusblock.data.SiteLimitRepository
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule
import com.focusblock.domain.model.SiteLimit
import com.focusblock.ui.common.EditCheck
import com.focusblock.ui.common.EditGuard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class SiteLimitEditorViewModel(
    private val host: String,
    private val repo: SiteLimitRepository,
    private val guard: EditGuard,
) : ViewModel() {

    val rules: StateFlow<List<SiteLimit>> =
        repo.limits.map { all -> all.filter { it.host == host } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                SiteLimit(
                    host = host,
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

    /** Pre-fill the form from an existing rule so it can be edited (adding replaces same type). */
    fun loadForEdit(rule: SiteLimit) {
        type = rule.type
        rule.dailyLimitMinutes?.let { dailyLimitMinutes = it }
        rule.hourlyLimitMinutes?.let { hourlyLimitMinutes = it }
        rule.launchLimit?.let { launchLimit = it }
        rule.blockUntil?.let { blockUntil = it }
        rule.schedule?.let { schedule = it }
    }

    fun blockAlways() {
        viewModelScope.launch { repo.add(SiteLimit(host = host, type = RuleType.ALWAYS)) }
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
