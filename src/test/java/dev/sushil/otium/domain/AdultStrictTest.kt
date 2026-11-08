package dev.sushil.otium.domain

import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.STRICT_NO_END
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.service.ModeKeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AdultStrictTest {
    private val now = LocalDateTime.of(2026, 11, 9, 12, 0) // a Monday
    private val nowMs = 1_800_000_000_000L

    @Test fun `off by default`() {
        assertFalse(ModeState().isAdultStrictActive(now, nowMs))
    }

    @Test fun `active until its end time, in any app mode`() {
        val s = ModeState(mode = Mode.NORMAL, adultStrictUntil = nowMs + 1000)
        assertTrue(s.isAdultStrictActive(now, nowMs))
        assertFalse(s.isAdultStrictActive(now, nowMs + 1000))
    }

    @Test fun `active only while its schedule runs`() {
        val s = ModeState(adultStrictSchedule = Schedule(daysMask = 0b0000001, startMinute = 9 * 60, endMinute = 17 * 60))
        assertTrue(s.isAdultStrictActive(now, nowMs))
        assertFalse(s.isAdultStrictActive(now.plusDays(1), nowMs))
    }

    @Test fun `survives leaving strict mode`() {
        val s = ModeState(mode = Mode.STRICT, adultStrictUntil = nowMs + 1000)
        assertEquals(nowMs + 1000, ModeKeeper.leaveToNormal(s).adultStrictUntil)
    }

    @Test fun `no end date stays active`() {
        assertTrue(ModeState(adultStrictUntil = STRICT_NO_END).isAdultStrictActive(now, nowMs))
        assertTrue(ModeState(mode = Mode.STRICT, strictUntil = STRICT_NO_END).isStrictActive(now, nowMs))
    }
}
