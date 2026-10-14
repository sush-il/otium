package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType

/**
 * Is a profile change "tightening" (always allowed) or "loosening" (blocked in Strict,
 * needs the password in Lock)? [old] null = new profile; [new] null = deleting.
 */
object StrictPolicy {

    fun isAllowed(old: Profile?, new: Profile?): Boolean {
        if (old == null) return true // adding rules is always fine
        if (!old.enabled) return true // a switched-off profile isn't enforcing anything yet
        if (new == null) return false // deleting an active profile
        if (!new.enabled) return false
        if (new.type != old.type) return false
        if (!new.packages.containsAll(old.packages)) return false
        return when (new.type) {
            RuleType.ALWAYS -> true
            // Only allow adding windows; every existing one must still be there unchanged
            RuleType.SCHEDULE -> new.schedules.containsAll(old.schedules)
            RuleType.DAILY_LIMIT -> (new.dailyLimitMinutes ?: Int.MAX_VALUE) <= (old.dailyLimitMinutes ?: Int.MAX_VALUE)
        }
    }
}
