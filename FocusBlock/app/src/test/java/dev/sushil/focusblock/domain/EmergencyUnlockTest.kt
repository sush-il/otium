package dev.sushil.focusblock.domain

import dev.sushil.focusblock.domain.EmergencyUnlock.HOUR_MS
import dev.sushil.focusblock.domain.model.EmergencyState
import dev.sushil.focusblock.domain.model.EmergencyState.Phase
import dev.sushil.focusblock.domain.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyUnlockTest {
    private val now = 1_800_000_000_000L
    private val day = 24 * HOUR_MS

    @Test fun `base waits per mode`() {
        assertEquals(24 * HOUR_MS, EmergencyUnlock.waitFor(Mode.LOCK, emptyList(), now))
        assertEquals(72 * HOUR_MS, EmergencyUnlock.waitFor(Mode.STRICT, emptyList(), now))
    }

    @Test fun `wait doubles per recent unlock and caps at a week`() {
        assertEquals(48 * HOUR_MS, EmergencyUnlock.waitFor(Mode.LOCK, listOf(now - day), now))
        assertEquals(96 * HOUR_MS, EmergencyUnlock.waitFor(Mode.LOCK, listOf(now - day, now - 2 * day), now))
        assertEquals(7 * day, EmergencyUnlock.waitFor(Mode.STRICT, listOf(now - day, now - 2 * day), now))
    }

    @Test fun `old unlocks don't count`() {
        assertEquals(24 * HOUR_MS, EmergencyUnlock.waitFor(Mode.LOCK, listOf(now - 31 * day), now))
    }

    @Test fun `waiting rolls into the window, carrying extra time`() {
        val waiting = EmergencyState(Phase.WAITING, remainingMs = 1_000)
        val after = EmergencyUnlock.tick(waiting, 61_000)
        assertEquals(Phase.WINDOW, after.phase)
        assertEquals(EmergencyUnlock.WINDOW_MS - 60_000, after.remainingMs)
    }

    @Test fun `missed window resets to idle`() {
        val window = EmergencyState(Phase.WINDOW, remainingMs = 5_000)
        assertEquals(Phase.IDLE, EmergencyUnlock.tick(window, 6_000).phase)
    }

    @Test fun `resuming after reboot just continues from the saved time`() {
        val saved = EmergencyUnlock.start(EmergencyState(), Mode.LOCK, now)
        val later = EmergencyUnlock.tick(saved, 10 * HOUR_MS)
        assertEquals(14 * HOUR_MS, later.remainingMs)
    }

    @Test fun `complete records history and starting twice is a no-op`() {
        val window = EmergencyState(Phase.WINDOW, remainingMs = 1_000)
        val done = EmergencyUnlock.complete(window, now)
        assertEquals(Phase.IDLE, done.phase)
        assertEquals(listOf(now), done.history)
        val waiting = EmergencyUnlock.start(done, Mode.LOCK, now)
        assertEquals(waiting, EmergencyUnlock.start(waiting, Mode.LOCK, now))
    }

    @Test fun `challenge has no look-alike characters`() {
        val c = EmergencyUnlock.newChallenge()
        assertEquals(100, c.length)
        assertTrue(c.none { it in "0O1IL" })
    }
}
