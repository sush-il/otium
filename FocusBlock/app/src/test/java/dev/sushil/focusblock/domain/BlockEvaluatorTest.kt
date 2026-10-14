package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.model.BlockDecision
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType
import dev.sushil.focusblock.domain.model.Schedule
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

    private fun evaluate(now: LocalDateTime, vararg profiles: Profile, usedMs: Long? = 0) =
        BlockEvaluator.evaluate(app, now, profiles.toList()) { usedMs }

    @Test fun `always blocks`() {
        assertTrue(evaluate(at(12, 10), profile(RuleType.ALWAYS)) is BlockDecision.Blocked)
    }

    @Test fun `disabled profile does not block`() {
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 10), profile(RuleType.ALWAYS, enabled = false)))
    }

    @Test fun `app not in profile is allowed`() {
        val decision = BlockEvaluator.evaluate("other.app", at(12, 10), listOf(profile(RuleType.ALWAYS))) { 0 }
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

    @Test fun `daily limit without usage access fails open`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 1)
        assertEquals(BlockDecision.Allowed, evaluate(at(12, 10), p, usedMs = null))
    }

    @Test fun `recheck at remaining limit time`() {
        val p = profile(RuleType.DAILY_LIMIT, limit = 30)
        val next = BlockEvaluator.nextRecheck(app, at(12, 10), listOf(p)) { 20 * 60_000L }
        assertEquals(Duration.ofMinutes(10), next)
    }

    @Test fun `recheck at next schedule start`() {
        val p = profile(RuleType.SCHEDULE, schedules = arrayOf(Schedule(weekdays, 9 * 60, 17 * 60)))
        assertEquals(Duration.ofMinutes(30), BlockEvaluator.nextRecheck(app, at(12, 8, 30), listOf(p)) { 0 })
        // Friday 18:00 -> next start Monday 09:00
        assertEquals(Duration.ofHours(63), BlockEvaluator.nextRecheck(app, at(16, 18), listOf(p)) { 0 })
    }

    @Test fun `no recheck for always or unrelated apps`() {
        assertNull(BlockEvaluator.nextRecheck(app, at(12, 10), listOf(profile(RuleType.ALWAYS))) { 0 })
        assertNull(BlockEvaluator.nextRecheck("other.app", at(12, 10), listOf(profile(RuleType.DAILY_LIMIT, limit = 5))) { 0 })
    }
}
