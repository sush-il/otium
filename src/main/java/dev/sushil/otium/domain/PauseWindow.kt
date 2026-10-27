package dev.sushil.otium.domain

import java.time.LocalDateTime

/** How long pausing an app's or site's limits lasts. */
object PauseWindow {
    /** In Lock mode a pause is short; for longer the user switches to Normal mode. */
    const val LOCK_MODE_MINUTES = 30L

    /** In Normal mode a pause lasts until the day resets, the same moment daily usage starts again. */
    fun nextReset(now: LocalDateTime, dayResetHour: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(dayResetHour, 0)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }
}
