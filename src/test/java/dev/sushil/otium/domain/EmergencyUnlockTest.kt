package dev.sushil.otium.domain

import dev.sushil.otium.domain.EmergencyUnlock.HOUR_MS
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.EmergencyState.Phase
import dev.sushil.otium.domain.model.Mode
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
        val saved = EmergencyUnlock.start(EmergencyState(), now)
        val later = EmergencyUnlock.tick(saved, 20 * 60_000L)
        assertEquals(5 * HOUR_MS - 20 * 60_000L, later.remainingMs)
    }

    @Test fun `complete records history and starting twice is a no-op`() {
        val window = EmergencyState(Phase.WINDOW, remainingMs = 1_000)
        val done = EmergencyUnlock.complete(window, now)
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
