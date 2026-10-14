package com.focusblock.ui.profiles

import com.focusblock.domain.model.Profile
import com.focusblock.domain.model.RuleType
import com.focusblock.domain.model.Schedule

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val EVERY_DAY = 0b1111111
private const val WEEKDAYS = 0b0011111
private const val WEEKENDS = 0b1100000

/** One-line description of a profile's rule, for list cards. */
fun Profile.summary(): String = when (type) {
    RuleType.ALWAYS -> "Always blocked"
    RuleType.SCHEDULE -> schedules.joinToString { it.summary() }.ifEmpty { "No schedule" }
    RuleType.DAILY_LIMIT -> "${dailyLimitMinutes ?: 0} min per day"
    RuleType.HOURLY_LIMIT -> "${hourlyLimitMinutes ?: 0} min per hour"
    RuleType.LAUNCH_LIMIT -> "${launchLimit ?: 0} opens per day"
    RuleType.BLOCK_UNTIL -> blockUntil?.let { "Blocked until ${it.toLocalDate()}" } ?: "Blocked until a date"
}

/** Short label for the rule-type picker. */
fun ruleTypeLabel(type: RuleType): String = when (type) {
    RuleType.ALWAYS -> "Always"
    RuleType.SCHEDULE -> "Schedule"
    RuleType.DAILY_LIMIT -> "Daily time limit"
    RuleType.HOURLY_LIMIT -> "Hourly time limit"
    RuleType.LAUNCH_LIMIT -> "Launch-count limit"
    RuleType.BLOCK_UNTIL -> "Block until a date"
}

fun Schedule.summary(): String = "${daysLabel(daysMask)} ${formatMinute(startMinute)}–${formatMinute(endMinute)}"

fun daysLabel(mask: Int): String = when (mask) {
    EVERY_DAY -> "Every day"
    WEEKDAYS -> "Weekdays"
    WEEKENDS -> "Weekends"
    else -> DAY_NAMES.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")
}

fun dayShortName(index: Int): String = DAY_NAMES[index]

fun formatMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
