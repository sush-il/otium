package dev.sushil.focusblock.domain.model

enum class RuleType { ALWAYS, SCHEDULE, DAILY_LIMIT }

/** A named set of apps plus one rule that decides when they are blocked. */
data class Profile(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val type: RuleType = RuleType.ALWAYS,
    val packages: Set<String> = emptySet(),
    val dailyLimitMinutes: Int? = null,
    val schedules: List<Schedule> = emptyList(),
)

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
