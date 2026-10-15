package dev.sushil.otium.domain

import dev.sushil.otium.domain.model.BlockDecision
import dev.sushil.otium.domain.model.Profile
import dev.sushil.otium.domain.model.RuleType
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.TimeLeft
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Decides whether an app is blocked right now. Pure Kotlin (no Android types),
 * so it can be unit tested without a phone.
 *
 * [usage] reports how much each app has been used. Its methods return null when
 * usage access isn't granted; limit rules then block (fail closed), so revoking
 * usage access can't be used as a bypass.
 */
object BlockEvaluator {

    fun evaluate(
        pkg: String,
        now: LocalDateTime,
        profiles: List<Profile>,
        usage: UsageSource,
        excluded: Set<String> = emptySet(),
    ): BlockDecision {
        // Strictest rule first, so the reason shown is the most useful one
        val matching = profiles.filter { it.isEnforcing(now) && it.targets(pkg, excluded) }.sortedBy { it.type.ordinal }
        for (profile in matching) {
            val reason = profile.blockReason(pkg, now, usage) ?: continue
            return BlockDecision.Blocked(reason, profile.name)
        }
        return BlockDecision.Allowed
    }

    /** Why [profile] blocks [pkg] right now, or null if it doesn't. */
    private fun Profile.blockReason(pkg: String, now: LocalDateTime, usage: UsageSource): String? = when (type) {
        RuleType.ALWAYS -> "Blocked by \"$name\""

        RuleType.SCHEDULE -> schedules.firstOrNull { it.isActive(now) }?.let {
            "Blocked until ${LocalTime.of(it.endMinute / 60, it.endMinute % 60)} by \"$name\""
        }

        RuleType.DAILY_LIMIT -> {
            val limit = dailyLimitMinutes ?: return null
            when (val used = usage.usedTodayMs(pkg)) {
                null -> usageOffReason()
                else -> if (used >= limit * 60_000L) "Daily limit of $limit min reached (\"$name\")" else null
            }
        }

        RuleType.HOURLY_LIMIT -> {
            val limit = hourlyLimitMinutes ?: return null
            when (val used = usage.usedThisHourMs(pkg)) {
                null -> usageOffReason()
                else -> if (used >= limit * 60_000L) "Hourly limit of $limit min reached (\"$name\")" else null
            }
        }

        RuleType.LAUNCH_LIMIT -> {
            val limit = launchLimit ?: return null
            when (val count = usage.launchesToday(pkg)) {
                null -> usageOffReason()
                else -> if (count >= limit) "Opened $limit times today (\"$name\")" else null
            }
        }

        RuleType.BLOCK_UNTIL -> blockUntil?.takeIf { now.isBefore(it) }?.let {
            "Blocked until ${it.toLocalDate()} ${LocalTime.of(it.hour, it.minute)} (\"$name\")"
        }
    }

    private fun Profile.usageOffReason() = "Usage access is off, so the limit can't be checked (\"$name\")"

    /**
     * How long until [pkg] may become blocked while it stays open, or null if nothing
     * will change. Used to re-check at the right moment instead of polling.
     */
    fun nextRecheck(
        pkg: String,
        now: LocalDateTime,
        profiles: List<Profile>,
        usage: UsageSource,
        excluded: Set<String> = emptySet(),
    ): Duration? {
        val candidates = profiles.filter { it.isEnforcing(now) && it.targets(pkg, excluded) }.mapNotNull { profile ->
            when (profile.type) {
                RuleType.ALWAYS, RuleType.LAUNCH_LIMIT, RuleType.BLOCK_UNTIL -> null
                RuleType.SCHEDULE -> profile.schedules.mapNotNull { it.nextStart(now) }.minOrNull()
                    ?.let { Duration.between(now, it) }
                RuleType.DAILY_LIMIT -> remaining(profile.dailyLimitMinutes, usage.usedTodayMs(pkg))
                RuleType.HOURLY_LIMIT -> {
                    // Re-check when the limit runs out, or at the next hour when the counter resets
                    val toNextHour = Duration.between(now, now.plusHours(1).withMinute(0).withSecond(0).withNano(0))
                    listOfNotNull(remaining(profile.hourlyLimitMinutes, usage.usedThisHourMs(pkg)), toNextHour).minOrNull()
                }
            }
        }
        return candidates.minOrNull()
    }

    private fun remaining(limitMinutes: Int?, usedMs: Long?): Duration? {
        val limitMs = (limitMinutes ?: return null) * 60_000L
        val used = usedMs ?: return null
        return Duration.ofMillis(limitMs - used).takeIf { !it.isNegative }
    }

    /**
     * What to show on the timer while [pkg] is open: a limit's time left, or a schedule
     * starting within [scheduleHorizon]. Null means there's nothing to count down.
     */
    fun timeLeft(
        pkg: String,
        now: LocalDateTime,
        profiles: List<Profile>,
        usage: UsageSource,
        excluded: Set<String> = emptySet(),
        scheduleHorizon: Duration = Duration.ofHours(1),
    ): TimeLeft? {
        val candidates = profiles.filter { it.isEnforcing(now) && it.targets(pkg, excluded) }.mapNotNull { profile ->
            when (profile.type) {
                RuleType.ALWAYS, RuleType.LAUNCH_LIMIT, RuleType.BLOCK_UNTIL -> null
                RuleType.SCHEDULE -> profile.schedules.mapNotNull { it.nextStart(now) }.minOrNull()
                    ?.let { Duration.between(now, it) }
                    ?.takeIf { it <= scheduleHorizon }
                    ?.let { TimeLeft(TimeLeft.Kind.SCHEDULE_START, it) }
                RuleType.DAILY_LIMIT -> limitLeft(profile.dailyLimitMinutes, usage.usedTodayMs(pkg))
                RuleType.HOURLY_LIMIT -> limitLeft(profile.hourlyLimitMinutes, usage.usedThisHourMs(pkg))
            }
        }
        return candidates.minByOrNull { it.duration }
    }

    private fun limitLeft(limitMinutes: Int?, usedMs: Long?): TimeLeft? {
        val limitMs = (limitMinutes ?: return null) * 60_000L
        val used = usedMs ?: return null
        return TimeLeft(TimeLeft.Kind.LIMIT, Duration.ofMillis((limitMs - used).coerceAtLeast(0)))
    }
}

private fun Schedule.hasDay(dayIndex: Int) = daysMask and (1 shl dayIndex) != 0

/** Monday = 0 ... Sunday = 6, matching the bits of [Schedule.daysMask]. */
private fun LocalDateTime.dayIndex() = dayOfWeek.value - 1

fun Schedule.isActive(now: LocalDateTime): Boolean {
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
