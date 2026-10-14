package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType

/**
 * Decides whether an app is blocked right now. Pure Kotlin (no Android types),
 * so it can be unit tested without a phone.
 */
object BlockEvaluator {

    fun evaluate(pkg: String, profiles: List<Profile>): BlockDecision {
        val matching = profiles.filter { it.enabled && pkg in it.packages }
        for (profile in matching) {
            when (profile.type) {
                RuleType.ALWAYS -> return BlockDecision.Blocked("Blocked by \"${profile.name}\"", profile.name)
                // Phase 4
                RuleType.SCHEDULE, RuleType.DAILY_LIMIT -> Unit
            }
        }
        return BlockDecision.Allowed
    }
}
