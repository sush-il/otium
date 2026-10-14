package com.focusblock.ui.sites

import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.SiteLimit
import com.focusblock.ui.profiles.daysLabel
import com.focusblock.ui.profiles.formatMinute

fun SiteLimit.summary(): String = when (type) {
    RuleType.ALWAYS -> "Always blocked"
    RuleType.DAILY_LIMIT -> "${dailyLimitMinutes ?: 0} min / day"
    RuleType.HOURLY_LIMIT -> "${hourlyLimitMinutes ?: 0} min / hour"
    RuleType.LAUNCH_LIMIT -> "${launchLimit ?: 0} visits / day"
    RuleType.SCHEDULE -> schedule?.let { "${daysLabel(it.daysMask)} ${formatMinute(it.startMinute)}–${formatMinute(it.endMinute)}" } ?: "Schedule"
    RuleType.BLOCK_UNTIL -> blockUntil?.let { "Until ${it.toLocalDate()}" } ?: "Until a date"
}
