package com.focusblock.domain

import com.focusblock.domain.model.EmergencyState
import com.focusblock.domain.model.EmergencyState.Phase
import com.focusblock.domain.model.Mode
import java.security.SecureRandom

/**
 * The slow way out of Lock/Strict mode when the password or recovery code is lost.
 * Pure state transitions; the caller feeds in phone-on time with [tick].
 */
object EmergencyUnlock {
    const val HOUR_MS = 3_600_000L
    const val LOCK_WAIT_MS = 24 * HOUR_MS
    const val STRICT_WAIT_MS = 72 * HOUR_MS
    const val MAX_WAIT_MS = 7 * 24 * HOUR_MS
    const val WINDOW_MS = 15 * 60_000L
    const val HISTORY_MS = 30 * 24 * HOUR_MS
    const val CHALLENGE_LENGTH = 100

    /** Base wait for the mode, doubled for every unlock completed in the last 30 days, capped at 7 days. */
    fun waitFor(mode: Mode, history: List<Long>, nowMs: Long): Long {
        val base = if (mode == Mode.STRICT) STRICT_WAIT_MS else LOCK_WAIT_MS
        val recent = history.count { nowMs - it < HISTORY_MS }
        var wait = base
        repeat(recent) { wait = (wait * 2).coerceAtMost(MAX_WAIT_MS) }
        return wait
    }

    fun start(state: EmergencyState, mode: Mode, nowMs: Long): EmergencyState =
        if (state.phase != Phase.IDLE) state
        else state.copy(phase = Phase.WAITING, remainingMs = waitFor(mode, state.history, nowMs))

    fun cancel(state: EmergencyState) = state.copy(phase = Phase.IDLE, remainingMs = 0)

    /** Count [elapsedMs] of phone-on time. Waiting rolls into the window; a missed window resets. */
    fun tick(state: EmergencyState, elapsedMs: Long): EmergencyState {
        if (state.phase == Phase.IDLE || elapsedMs <= 0) return state
        val left = state.remainingMs - elapsedMs
        if (left > 0) return state.copy(remainingMs = left)
        return when (state.phase) {
            Phase.WAITING -> tick(state.copy(phase = Phase.WINDOW, remainingMs = WINDOW_MS), -left)
            else -> cancel(state) // window missed: start over
        }
    }

    /** Used the window: back to idle, and remember it for escalation. */
    fun complete(state: EmergencyState, nowMs: Long): EmergencyState {
        require(state.phase == Phase.WINDOW) { "Emergency unlock isn't ready" }
        return EmergencyState(history = (state.history + nowMs).filter { nowMs - it < HISTORY_MS })
    }

    fun newChallenge(random: SecureRandom = SecureRandom()): String =
        (1..CHALLENGE_LENGTH).map { PasswordHasher.ALPHABET[random.nextInt(PasswordHasher.ALPHABET.length)] }.joinToString("")
}
