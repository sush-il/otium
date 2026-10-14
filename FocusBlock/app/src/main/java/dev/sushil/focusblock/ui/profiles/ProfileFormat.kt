package dev.sushil.focusblock.ui.profiles

import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType

/** One-line description of a profile's rule, for list cards. */
fun Profile.summary(): String = when (type) {
    RuleType.ALWAYS -> "Always blocked"
    RuleType.SCHEDULE -> "On a schedule"
    RuleType.DAILY_LIMIT -> "${dailyLimitMinutes ?: 0} min per day"
}
