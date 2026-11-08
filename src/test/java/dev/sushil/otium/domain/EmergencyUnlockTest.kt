package dev.sushil.otium.domain

import dev.sushil.otium.domain.EmergencyUnlock.HOUR_MS
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.EmergencyState.Phase
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.STRICT_NO_END
import dev.sushil.otium.service.ModeKeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * HOUR_MS

    @Test fun `base wait is five hours`() {
        assertEquals(5 * HOUR_MS, EmergencyUnlock.waitFor(emptyList(), now))
    }

    @Test fun `wait doubles per recent unlock and caps at a week`() {
        assertEquals(10 * HOUR_MS, EmergencyUnlock.waitFor(listOf(now - day), now))
        assertEquals(20 * HOUR_MS, EmergencyUnlock.waitFor(listOf(now - day, now - 2 * day), now))
        assertEquals(7 * day, EmergencyUnlock.waitFor((1..10).map { now - it * HOUR_MS }, now))
    }

    @Test fun `old unlocks don't count`() {
        assertEquals(5 * HOUR_MS, EmergencyUnlock.waitFor(listOf(now - 31 * day), now))
    }

    @Test fun `finished wait is done, extra time doesn't go negative`() {
        val waiting = EmergencyState(Phase.WAITING, remainingMs = 1_000)
        assertTrue(!EmergencyUnlock.isDone(EmergencyUnlock.tick(waiting, 500)))
        val after = EmergencyUnlock.tick(waiting, 61_000)
        assertEquals(0L, after.remainingMs)
        assertTrue(EmergencyUnlock.isDone(after))
    }

    @Test fun `finishing leaves strict and releases adult strict`() {
        val strict = ModeState(
            mode = Mode.STRICT, strictUntil = STRICT_NO_END, adultStrictUntil = STRICT_NO_END,
            emergency = EmergencyState(Phase.WAITING, remainingMs = 0),
        )
        val out = ModeKeeper.finishEmergency(strict, now)
        assertEquals(Mode.NORMAL, out.mode)
        assertEquals(null, out.adultStrictUntil)
        assertEquals(Phase.IDLE, out.emergency.phase)
        assertEquals(listOf(now), out.emergency.history)
    }

    @Test fun `finishing in normal mode still releases adult-only strict`() {
        val out = ModeKeeper.finishEmergency(ModeState(adultStrictUntil = STRICT_NO_END), now)
        assertEquals(null, out.adultStrictUntil)
        assertEquals(Mode.NORMAL, out.mode)
    }

    @Test fun `resuming after reboot just continues from the saved time`() {
        val saved = EmergencyUnlock.start(EmergencyState(), now)
        val later = EmergencyUnlock.tick(saved, 20 * 60_000L)
        assertEquals(5 * HOUR_MS - 20 * 60_000L, later.remainingMs)
    }

    @Test fun `complete records history and starting twice is a no-op`() {
        val done = EmergencyUnlock.complete(EmergencyState(Phase.WAITING), now)
        assertEquals(Phase.IDLE, done.phase)
        assertEquals(listOf(now), done.history)
        val waiting = EmergencyUnlock.start(done, now)
        assertEquals(waiting, EmergencyUnlock.start(waiting, now))
    }

    @Test fun `challenge has no look-alike characters`() {
        val c = EmergencyUnlock.newChallenge()
        assertEquals(100, c.length)
        assertTrue(c.none { it in "0O1IL" })
    }
}
