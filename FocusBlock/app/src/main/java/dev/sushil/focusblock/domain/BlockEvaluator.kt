package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType
import dev.sushil.focusblock.domain.model.Schedule
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Decides whether an app is blocked right now. Pure Kotlin (no Android types),
 * so it can be unit tested without a phone.
 *
 * [usedTodayMs] returns how long the app was used today, or null when usage
 * access isn't granted. It is only called for daily-limit profiles.
 */
object BlockEvaluator {

    fun evaluate(
        pkg: String,
        now: LocalDateTime,
        profiles: List<Profile>,
        usedTodayMs: (String) -> Long?,
    ): BlockDecision {
        // Strictest rule first, so the reason shown is the most useful one
        val matching = profiles.filter { it.enabled && pkg in it.packages }.sortedBy { it.type.ordinal }
        for (profile in matching) {
            when (profile.type) {
                RuleType.ALWAYS -> return BlockDecision.Blocked("Blocked by \"${profile.name}\"", profile.name)

                RuleType.SCHEDULE -> {
                    val window = profile.schedules.firstOrNull { it.isActive(now) } ?: continue
                    val until = LocalTime.of(window.endMinute / 60, window.endMinute % 60)
                    return BlockDecision.Blocked("Blocked until $until by \"${profile.name}\"", profile.name)
                }

                RuleType.DAILY_LIMIT -> {
                    val limit = profile.dailyLimitMinutes ?: continue
                    // No usage access: fail closed, otherwise revoking it would be an easy bypass
                    val used = usedTodayMs(pkg)
                        ?: return BlockDecision.Blocked("Usage access is off, so the daily limit can't be checked (\"${profile.name}\")", profile.name)
                    if (used >= limit * 60_000L) {
                        return BlockDecision.Blocked("Daily limit of $limit min reached (\"${profile.name}\")", profile.name)
                    }
                }
            }
        }
        return BlockDecision.Allowed
    }

    /**
     * How long until [pkg] may become blocked while it stays open, or null if nothing
     * will change. Used to re-check at the right moment instead of polling.
     */
    fun nextRecheck(
        pkg: String,
        now: LocalDateTime,
        profiles: List<Profile>,
        usedTodayMs: (String) -> Long?,
    ): Duration? {
        val candidates = profiles.filter { it.enabled && pkg in it.packages }.mapNotNull { profile ->
            when (profile.type) {
                RuleType.ALWAYS -> null
                RuleType.SCHEDULE -> profile.schedules.mapNotNull { it.nextStart(now) }.minOrNull()
                    ?.let { Duration.between(now, it) }
                RuleType.DAILY_LIMIT -> {
                    val limitMs = (profile.dailyLimitMinutes ?: return@mapNotNull null) * 60_000L
                    val used = usedTodayMs(pkg) ?: return@mapNotNull null
                    Duration.ofMillis(limitMs - used).takeIf { !it.isNegative }
                }
            }
        }
        return candidates.minOrNull()
    }
}

private fun Schedule.hasDay(dayIndex: Int) = daysMask and (1 shl dayIndex) != 0

/** Monday = 0 ... Sunday = 6, matching the bits of [Schedule.daysMask]. */
private fun LocalDateTime.dayIndex() = dayOfWeek.value - 1

internal fun Schedule.isActive(now: LocalDateTime): Boolean {
    val minute = now.hour * 60 + now.minute
    val today = now.dayIndex()
    val yesterday = (today + 6) % 7
    return if (endMinute > startMinute) {
        hasDay(today) && minute in startMinute until endMinute
    } else {
        // Crosses midnight: the part after midnight belongs to the day the window started
        (hasDay(today) && minute >= startMinute) || (hasDay(yesterday) && minute < endMinute)
    }
}

/** Next moment (strictly after [now]) this window starts, within the coming week. */
internal fun Schedule.nextStart(now: LocalDateTime): LocalDateTime? =
    (0..7).asSequence()
        .map { now.toLocalDate().plusDays(it.toLong()) }
        .filter { hasDay(it.dayOfWeek.value - 1) }
        .map { it.atTime(startMinute / 60, startMinute % 60) }
        .firstOrNull { it.isAfter(now) }
