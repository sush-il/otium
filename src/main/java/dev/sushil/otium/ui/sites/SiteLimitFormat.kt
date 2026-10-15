package dev.sushil.otium.ui.sites

import dev.sushil.otium.ui.profiles.formatDuration
import dev.sushil.otium.domain.model.RuleType
import dev.sushil.otium.domain.model.SiteLimit
import dev.sushil.otium.ui.profiles.daysLabel
import dev.sushil.otium.ui.profiles.formatMinute

fun SiteLimit.summary(): String = when (type) {
    RuleType.ALWAYS -> "Always blocked"
    RuleType.DAILY_LIMIT -> "${formatDuration(dailyLimitMinutes ?: 0)} / day"
    RuleType.HOURLY_LIMIT -> "${formatDuration(hourlyLimitMinutes ?: 0)} / hour"
    RuleType.LAUNCH_LIMIT -> "${launchLimit ?: 0} visits / day"
    RuleType.SCHEDULE -> schedule?.let { "${daysLabel(it.daysMask)} ${formatMinute(it.startMinute)}–${formatMinute(it.endMinute)}" } ?: "Schedule"
    RuleType.BLOCK_UNTIL -> blockUntil?.let { "Until ${it.toLocalDate()}" } ?: "Until a date"
}
