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
        // Dropping whole-phone coverage, or removing apps, is loosening
        if (old.wholePhone && !new.wholePhone) return false
        if (!new.wholePhone && !new.packages.containsAll(old.packages)) return false
        return when (new.type) {
            RuleType.ALWAYS -> true
            // Only allow adding windows; every existing one must still be there unchanged
            RuleType.SCHEDULE -> new.schedules.containsAll(old.schedules)
            RuleType.DAILY_LIMIT -> lower(new.dailyLimitMinutes, old.dailyLimitMinutes)
            RuleType.HOURLY_LIMIT -> lower(new.hourlyLimitMinutes, old.hourlyLimitMinutes)
            RuleType.LAUNCH_LIMIT -> lower(new.launchLimit, old.launchLimit)
            // Keeping the block at least as long is tightening
            RuleType.BLOCK_UNTIL -> {
                val o = old.blockUntil ?: return true
                new.blockUntil?.let { !it.isBefore(o) } ?: false
            }
        }
    }

    // A smaller (or equal) limit is stricter. Missing = unlimited = loosest.
    private fun lower(new: Int?, old: Int?) = (new ?: Int.MAX_VALUE) <= (old ?: Int.MAX_VALUE)
}
