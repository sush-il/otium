package dev.sushil.otium.domain

import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.EmergencyState.Phase
import java.security.SecureRandom

/**
 * The slow way out of Strict mode (Lock mode is escaped by typing its number instead).
 * When the wait ends, the app switches to Normal by itself. Pure state transitions;
 * the caller feeds in phone-on time with [tick].
 */
object EmergencyUnlock {
    const val HOUR_MS = 3_600_000L
    const val BASE_WAIT_MS = 5 * HOUR_MS
    const val MAX_WAIT_MS = 7 * 24 * HOUR_MS
    const val HISTORY_MS = 30 * 24 * HOUR_MS
    const val CHALLENGE_LENGTH = 100

    /** 5 hours, doubled for every unlock completed in the last 30 days, capped at 7 days. */
    fun waitFor(history: List<Long>, nowMs: Long): Long {
        val recent = history.count { nowMs - it < HISTORY_MS }
        var wait = BASE_WAIT_MS
        repeat(recent) { wait = (wait * 2).coerceAtMost(MAX_WAIT_MS) }
        return wait
    }

    fun start(state: EmergencyState, nowMs: Long): EmergencyState =
        if (state.phase != Phase.IDLE) state
        else state.copy(phase = Phase.WAITING, remainingMs = waitFor(state.history, nowMs))

    fun cancel(state: EmergencyState) = state.copy(phase = Phase.IDLE, remainingMs = 0)

    /** Count [elapsedMs] of phone-on time against the wait. Check [isDone] afterwards. */
    fun tick(state: EmergencyState, elapsedMs: Long): EmergencyState {
        if (state.phase != Phase.WAITING || elapsedMs <= 0) return state
        return state.copy(remainingMs = (state.remainingMs - elapsedMs).coerceAtLeast(0))
    }

    /** The wait is over: the caller should now switch the user out of Strict. */
    fun isDone(state: EmergencyState) = state.phase == Phase.WAITING && state.remainingMs <= 0

    /** Back to idle, remembering this unlock (for the escalating wait). */
    fun complete(state: EmergencyState, nowMs: Long): EmergencyState =
        EmergencyState(history = (state.history + nowMs).filter { nowMs - it < HISTORY_MS })

    fun newChallenge(random: SecureRandom = SecureRandom()): String =
        (1..CHALLENGE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
}
