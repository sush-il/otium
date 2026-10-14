package com.focusblock.domain.model

import java.time.LocalDateTime

enum class RuleType {
    /** Blocked whenever the profile is on. */
    ALWAYS,
    /** Blocked during weekly time windows. */
    SCHEDULE,
    /** Blocked after N minutes of use today. */
    DAILY_LIMIT,
    /** Blocked after N minutes of use in the current clock hour. */
    HOURLY_LIMIT,
    /** Blocked after being opened N times today. */
    LAUNCH_LIMIT,
    /** Blocked until a chosen date/time ("keep away"). */
    BLOCK_UNTIL,
}

/** A named set of apps plus one rule that decides when they are blocked. */
data class Profile(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val type: RuleType = RuleType.ALWAYS,
    val packages: Set<String> = emptySet(),
    val dailyLimitMinutes: Int? = null,
    val schedules: List<Schedule> = emptyList(),
    val hourlyLimitMinutes: Int? = null,
    val launchLimit: Int? = null,
    val blockUntil: LocalDateTime? = null,
    /** When set and still in the future, the profile is temporarily off (Normal-mode pause). */
    val pausedUntil: LocalDateTime? = null,
    /** When true the profile targets every app except [excluded], not the [packages] list. */
    val wholePhone: Boolean = false,
) {
    /** Is this profile actually blocking right now? (On, and not paused.) */
    fun isEnforcing(now: LocalDateTime): Boolean =
        enabled && (pausedUntil == null || !now.isBefore(pausedUntil))

    /** Does this profile target [pkg]? [excluded] apps are never caught by a whole-phone profile. */
    fun targets(pkg: String, excluded: Set<String>): Boolean =
        if (wholePhone) pkg !in excluded else pkg in packages
}

/**
 * A weekly time window. [daysMask] bit 0 = Monday ... bit 6 = Sunday.
 * Minutes are counted from midnight (0..1439). If [endMinute] <= [startMinute]
 * the window crosses midnight into the next day.
 */
data class Schedule(
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
)
