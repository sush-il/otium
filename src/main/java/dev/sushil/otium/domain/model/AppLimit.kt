package dev.sushil.otium.domain.model

import java.time.LocalDateTime

/**
 * One stackable rule on a directly-blocked app (no profile needed). An app can have
 * several of these at once (e.g. 60 min/day AND 15 min/hour); it's blocked if any triggers.
 */
data class AppLimit(
    val id: Long = 0,
    val packageName: String,
    val type: RuleType,
    val dailyLimitMinutes: Int? = null,
    val hourlyLimitMinutes: Int? = null,
    val launchLimit: Int? = null,
    val blockUntil: LocalDateTime? = null,
    val schedule: Schedule? = null,
) {
    /** As a one-app [Profile], so the existing evaluator can judge it (ids are negative to avoid clashes). */
    fun asProfile() = Profile(
        id = -(1000L + id),
        name = "Blocked app",
        enabled = true,
        type = type,
        packages = setOf(packageName),
        dailyLimitMinutes = dailyLimitMinutes,
        hourlyLimitMinutes = hourlyLimitMinutes,
        launchLimit = launchLimit,
        blockUntil = blockUntil,
        schedules = schedule?.let { listOf(it) } ?: emptyList(),
    )
}
