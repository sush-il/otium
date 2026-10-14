package com.focusblock.domain.model

import com.focusblock.domain.isActive
import java.time.LocalDateTime

/** How hard it is to loosen your own rules. Each mode is stricter than the one before. */
enum class Mode { NORMAL, LOCK, STRICT }

/** What the Lock-mode password protects. */
data class LockOptions(
    val lockApp: Boolean = true,
    val lockSettings: Boolean = true,
    val lockProfiles: Boolean = true,
)

/** Extra things Strict mode blocks. Uninstalling and loosening profiles are always blocked. */
data class StrictOptions(
    val blockSettings: Boolean = true,
    val blockRecents: Boolean = false,
    val blockPowerMenu: Boolean = false,
    val blockPlayStore: Boolean = false,
)

data class ModeState(
    val mode: Mode = Mode.NORMAL,
    /** How many digits the Lock-mode typing challenge has; null until Lock has been set up. */
    val lockLength: Int? = null,
    val lock: LockOptions = LockOptions(),
    val strict: StrictOptions = StrictOptions(),
    /** Strict ends at this wall-clock time (epoch ms)… */
    val strictUntil: Long? = null,
    /** …or is active only while this schedule runs. */
    val strictSchedule: Schedule? = null,
    val emergency: EmergencyState = EmergencyState(),
) {
    val isLock get() = mode == Mode.LOCK
    val hasLock get() = lockLength != null

    fun isStrictActive(now: LocalDateTime, nowMs: Long): Boolean =
        mode == Mode.STRICT &&
            ((strictUntil != null && nowMs < strictUntil) || strictSchedule?.isActive(now) == true)
}

/** State of the slow "emergency unlock" (see PLAN.md 5.13). */
data class EmergencyState(
    val phase: Phase = Phase.IDLE,
    /** Phone-on time left in the current phase. */
    val remainingMs: Long = 0,
    /** When previous emergency unlocks were completed (epoch ms), for escalation. */
    val history: List<Long> = emptyList(),
) {
    enum class Phase { IDLE, WAITING, WINDOW }
}
