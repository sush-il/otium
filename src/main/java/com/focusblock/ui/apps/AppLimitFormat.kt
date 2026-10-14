package com.focusblock.ui.apps

import com.focusblock.ui.profiles.formatDuration
import com.focusblock.domain.model.AppLimit
import com.focusblock.domain.model.RuleType
import com.focusblock.ui.profiles.daysLabel
import com.focusblock.ui.profiles.formatMinute

/** Short one-line summary of a single stacked rule. */
fun AppLimit.summary(): String = when (type) {
    RuleType.ALWAYS -> "Always blocked"
    RuleType.DAILY_LIMIT -> "${formatDuration(dailyLimitMinutes ?: 0)} / day"
    RuleType.HOURLY_LIMIT -> "${formatDuration(hourlyLimitMinutes ?: 0)} / hour"
    RuleType.LAUNCH_LIMIT -> "${launchLimit ?: 0} opens / day"
    RuleType.SCHEDULE -> schedule?.let { "${daysLabel(it.daysMask)} ${formatMinute(it.startMinute)}–${formatMinute(it.endMinute)}" } ?: "Schedule"
    RuleType.BLOCK_UNTIL -> blockUntil?.let { "Until ${it.toLocalDate()}" } ?: "Until a date"
}
