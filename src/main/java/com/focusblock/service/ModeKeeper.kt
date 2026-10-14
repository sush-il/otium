package com.focusblock.service

import android.content.Context
import android.os.SystemClock
import com.focusblock.admin.FocusAdminReceiver
import com.focusblock.data.ModeStore
import com.focusblock.domain.EmergencyUnlock
import com.focusblock.domain.model.EmergencyState
import com.focusblock.domain.model.Mode
import com.focusblock.domain.model.ModeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Background timekeeping for modes: ends Strict mode at its end time, and counts
 * down the emergency unlock using phone-on time only.
 */
class ModeKeeper(
    private val context: Context,
    private val modeStore: ModeStore,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            modeStore.state.map { it.mode to it.strictUntil }.distinctUntilChanged().collectLatest { (mode, until) ->
                if (mode == Mode.STRICT && until != null) {
                    delay((until - System.currentTimeMillis()).coerceAtLeast(0))
                    modeStore.update { leaveToNormal(it) }
                    FocusAdminReceiver.deactivate(context)
                }
            }
        }
        // After typing the Lock number the user is in Normal mode; switch Lock back on when its time is up
        scope.launch {
            modeStore.state.map { it.mode to it.relockAt }.distinctUntilChanged().collectLatest { (mode, at) ->
                if (mode == Mode.NORMAL && at != null) {
                    delay((at - System.currentTimeMillis()).coerceAtLeast(0))
                    modeStore.update { if (it.mode == Mode.NORMAL && it.hasLock) it.copy(mode = Mode.LOCK, relockAt = null) else it }
                }
            }
        }
        scope.launch {
            modeStore.state.map { it.emergency.phase }.distinctUntilChanged().collectLatest { phase ->
                if (phase != EmergencyState.Phase.IDLE) countEmergencyTime()
            }
        }
    }

    /**
     * elapsedRealtime can't be changed by the user (unlike the date/time) and restarts
     * at 0 on boot, so time with the phone off is simply never counted.
     */
    private suspend fun countEmergencyTime() {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - last
            last = now
            modeStore.update { it.copy(emergency = EmergencyUnlock.tick(it.emergency, elapsed)) }
        }
    }

    companion object {
        private const val TICK_MS = 30_000L
        const val RELOCK_MS = 60 * 60_000L

        /** Back to Normal: password, strict settings and any running unlock are cleared. History stays. */
        fun leaveToNormal(state: ModeState) = ModeState(
            lock = state.lock,
            strict = state.strict,
            emergency = EmergencyState(history = state.emergency.history),
        )
    }
}
