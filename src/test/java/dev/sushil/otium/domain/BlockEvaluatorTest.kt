package dev.sushil.otium.domain

import dev.sushil.otium.domain.UsageSource
import dev.sushil.otium.domain.model.BlockDecision
import dev.sushil.otium.domain.model.Profile
import dev.sushil.otium.domain.model.RuleType
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.TimeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class BlockEvaluatorTest {

    private val app = "com.example.social"
    private val mon = 1 shl 0
    private val sun = 1 shl 6
    private val weekdays = 0b0011111

    // 2026-10-12 is a Monday
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private fun profile(type: RuleType, enabled: Boolean = true, limit: Int? = null, vararg schedules: Schedule) =
        Profile(id = 1, name = "Test", enabled = enabled, type = type, packages = setOf(app),
            dailyLimitMinutes = limit, schedules = schedules.toList())

    private fun usage(today: Long? = 0L, hour: Long? = 0L, launches: Int? = 0) = object : UsageSource {
        override fun usedTodayMs(pkg: String) = today
        override fun usedThisHourMs(pkg: String) = hour
        override fun launchesToday(pkg: String) = launches
    }

    private fun evaluate(now: LocalDateTime, vararg profiles: Profile, usedMs: Long? = 0) =
        BlockEvaluator.evaluate(app, now, profiles.toList(), usage(today = usedMs))

    @Test fun `always blocks`() {
        assertTrue(evaluate(at(12, 10), profile(RuleType.ALWAYS)) is BlockDecision.Blocked)
    }

    @Test fun `disabled profile does not block`() {
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 10), profile(RuleType.ALWAYS, enabled = false)))
    }

    @Test fun `app not in profile is allowed`() {
        val decision = BlockEvaluator.evaluate("other.app", at(12, 10), listOf(profile(RuleType.ALWAYS)), usage(today = 0))
        assertEquals(BlockDecision.Allowed, decision)
    }

    @Test fun `schedule blocks inside window and not outside`() {
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(weekdays, 9 * 60, 17 * 60)))
        assertTrue(evaluate(at(12, 9), p) is BlockDecision.Blocked)
        assertTrue(evaluate(at(12, 16, 59), p) is BlockDecision.Blocked)
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 17), p)) // end is exclusive
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 8, 59), p))
        assertEquals(BlockDecision.Allowed, evaluate(at(18, 10), p)) // Sunday
    }

    @Test fun `window crossing midnight belongs to the day it started`() {
        // Monday 22:00 -> Tuesday 06:00
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(mon, 22 * 60, 6 * 60)))
        assertTrue(evaluate(at(12, 23), p) is BlockDecision.Blocked)        // Mon night
        assertTrue(evaluate(at(13, 5, 59), p) is BlockDecision.Blocked)     // Tue early morning
        assertEquals(BlockDecision.Allowed, evaluate(at(13, 6), p))         // Tue 06:00
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 3), p))         // Mon early morning (Sun not set)
        assertEquals(BlockDecision.Allowed, evaluate(at(13, 23), p))        // Tue night
    }

    @Test fun `sunday night window spills into monday`() {
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(sun, 23 * 60, 2 * 60)))
        assertTrue(evaluate(at(12, 1), p) is BlockDecision.Blocked) // Monday 01:00
    }

    @Test fun `daily limit blocks when reached exactly`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 30)
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 10), p, usedMs = 30 * 60_000L - 1))
        assertTrue(evaluate(at(12, 10), p, usedMs = 30 * 60_000L) is BlockDecision.Blocked)
    }

    @Test fun `daily limit without usage access fails closed`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 1)
        assertTrue(evaluate(at(12, 10), p, usedMs = null) is BlockDecision.Blocked)
    }

    @Test fun `recheck at remaining limit time`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 30)
        val next = BlockEvaluator.nextRecheck(app, at(12, 10), listOf(p), usage(today = 20 * 60_000L))
        assertEquals(Duration.ofMinutes(10), next)
    }

    @Test fun `recheck at next schedule start`() {
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(weekdays, 9 * 60, 17 * 60)))
        assertEquals(Duration.ofMinutes(30), BlockEvaluator.nextRecheck(app, at(12, 8, 30), listOf(p), usage(today = 0)))
        // Friday 18:00 -> next start Monday 09:00
        assertEquals(Duration.ofHours(63), BlockEvaluator.nextRecheck(app, at(16, 18), listOf(p), usage(today = 0)))
    }

    @Test fun `no recheck for always or unrelated apps`() {
        assertNull(BlockEvaluator.nextRecheck(app, at(12, 10), listOf(profile(RuleType.ALWAYS)), usage(today = 0)))
        assertNull(BlockEvaluator.nextRecheck("other.app", at(12, 10), listOf(profile(RuleType.DAILY_LIMIT, limit = 5)), usage(today = 0)))
    }

    @Test fun `time left shows the daily limit remaining`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 30)
        val left = BlockEvaluator.timeLeft(app, at(12, 10), listOf(p), usage(today = 25 * 60_000L))
        assertEquals(TimeLeft(TimeLeft.Kind.LIMIT, Duration.ofMinutes(5)), left)
    }

    @Test fun `time left shows a schedule starting within the hour only`() {
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(weekdays, 9 * 60, 17 * 60)))
        assertEquals(
            TimeLeft(TimeLeft.Kind.SCHEDULE_START, Duration.ofMinutes(20)),
            BlockEvaluator.timeLeft(app, at(12, 8, 40), listOf(p), usage(today = 0)),
        )
        assertNull(BlockEvaluator.timeLeft(app, at(12, 7), listOf(p), usage(today = 0)))
    }

    @Test fun `time left picks the soonest of several profiles`() {
        val limit = profile(RuleType.DAILY_LIMIT, limit = 30)
        val schedule = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(weekdays, 9 * 60, 17 * 60)))
        val left = BlockEvaluator.timeLeft(app, at(12, 8, 50), listOf(limit, schedule), usage(today = 0))
        assertEquals(TimeLeft.Kind.SCHEDULE_START, left?.kind)
    }

    @Test fun `no time left for always-blocked or unlimited apps`() {
        assertNull(BlockEvaluator.timeLeft(app, at(12, 10), listOf(profile(RuleType.ALWAYS)), usage(today = 0)))
        assertNull(BlockEvaluator.timeLeft("other.app", at(12, 10), listOf(profile(RuleType.DAILY_LIMIT, limit = 5)), usage(today = 0)))
    }

    @Test fun `hourly limit blocks when the hour's minutes are used up`() {
        val p = Profile(id = 1, name = "H", type = RuleType.HOURLY_LIMIT, packages = setOf(app), hourlyLimitMinutes = 10)
        assertEquals(BlockDecision.Allowed, BlockEvaluator.evaluate(app, at(12, 10), listOf(p), usage(hour = 9 * 60_000L)))
        assertTrue(BlockEvaluator.evaluate(app, at(12, 10), listOf(p), usage(hour = 10 * 60_000L)) is BlockDecision.Blocked)
    }

    @Test fun `hourly limit rechecks at the hour boundary at the latest`() {
        val p = Profile(id = 1, name = "H", type = RuleType.HOURLY_LIMIT, packages = setOf(app), hourlyLimitMinutes = 60)
        // Limit can't be hit this hour (0 used, 60 min cap), so the recheck is the time to the next hour
        val next = BlockEvaluator.nextRecheck(app, at(12, 10, 20), listOf(p), usage(hour = 0))
        assertEquals(Duration.ofMinutes(40), next)
    }

    @Test fun `launch limit blocks after N opens`() {
        val p = Profile(id = 1, name = "L", type = RuleType.LAUNCH_LIMIT, packages = setOf(app), launchLimit = 5)
        assertEquals(BlockDecision.Allowed, BlockEvaluator.evaluate(app, at(12, 10), listOf(p), usage(launches = 4)))
        assertTrue(BlockEvaluator.evaluate(app, at(12, 10), listOf(p), usage(launches = 5)) is BlockDecision.Blocked)
    }

    @Test fun `block until blocks before the time and allows after`() {
        val until = at(13, 9)
        val p = Profile(id = 1, name = "U", type = RuleType.BLOCK_UNTIL, packages = setOf(app), blockUntil = until)
        assertTrue(BlockEvaluator.evaluate(app, at(12, 23), listOf(p), usage()) is BlockDecision.Blocked)
        assertEquals(BlockDecision.Allowed, BlockEvaluator.evaluate(app, at(13, 9), listOf(p), usage()))
    }

    @Test fun `limit rules fail closed without usage access`() {
        val hourly = Profile(id = 1, name = "H", type = RuleType.HOURLY_LIMIT, packages = setOf(app), hourlyLimitMinutes = 10)
        val launch = Profile(id = 2, name = "L", type = RuleType.LAUNCH_LIMIT, packages = setOf(app), launchLimit = 5)
        assertTrue(BlockEvaluator.evaluate(app, at(12, 10), listOf(hourly), usage(hour = null)) is BlockDecision.Blocked)
        assertTrue(BlockEvaluator.evaluate(app, at(12, 10), listOf(launch), usage(launches = null)) is BlockDecision.Blocked)
    }
}
