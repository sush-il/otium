package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.domain.model.RuleType
import dev.sushil.focusblock.domain.model.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictPolicyTest {
    private val base = Profile(id = 1, name = "Social", type = RuleType.DAILY_LIMIT, packages = setOf("a", "b"), dailyLimitMinutes = 30)

    @Test fun `new profiles are allowed`() = assertTrue(StrictPolicy.isAllowed(null, base))
    @Test fun `deleting is not`() = assertFalse(StrictPolicy.isAllowed(base, null))
    @Test fun `disabling is not`() = assertFalse(StrictPolicy.isAllowed(base, base.copy(enabled = false)))
    @Test fun `enabling is`() = assertTrue(StrictPolicy.isAllowed(base.copy(enabled = false), base))
    @Test fun `anything goes on a disabled profile`() = assertTrue(StrictPolicy.isAllowed(base.copy(enabled = false), null))
    @Test fun `adding apps is allowed`() = assertTrue(StrictPolicy.isAllowed(base, base.copy(packages = setOf("a", "b", "c"))))
    @Test fun `removing apps is not`() = assertFalse(StrictPolicy.isAllowed(base, base.copy(packages = setOf("a"))))
    @Test fun `lowering the limit is allowed`() = assertTrue(StrictPolicy.isAllowed(base, base.copy(dailyLimitMinutes = 10)))
    @Test fun `raising the limit is not`() = assertFalse(StrictPolicy.isAllowed(base, base.copy(dailyLimitMinutes = 60)))
    @Test fun `changing type is not`() = assertFalse(StrictPolicy.isAllowed(base, base.copy(type = RuleType.ALWAYS)))
    @Test fun `renaming is allowed`() = assertTrue(StrictPolicy.isAllowed(base, base.copy(name = "Feeds")))

    @Test fun `schedules may only gain windows`() {
        val w1 = Schedule(0b11111, 540, 1020)
        val w2 = Schedule(0b1100000, 600, 720)
        val s = base.copy(type = RuleType.SCHEDULE, dailyLimitMinutes = null, schedules = listOf(w1))
        assertTrue(StrictPolicy.isAllowed(s, s.copy(schedules = listOf(w1, w2))))
        assertFalse(StrictPolicy.isAllowed(s, s.copy(schedules = listOf(w1.copy(endMinute = 900)))))
    }
}
