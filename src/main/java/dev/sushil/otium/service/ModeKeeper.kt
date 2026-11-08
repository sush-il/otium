package dev.sushil.otium.service

import android.content.Context
import android.os.SystemClock
import dev.sushil.otium.admin.FocusAdminReceiver
import dev.sushil.otium.data.ModeStore
import dev.sushil.otium.domain.EmergencyUnlock
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.STRICT_NO_END
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
                if (mode == Mode.STRICT && until != null && until != STRICT_NO_END) {
                    delay((until - System.currentTimeMillis()).coerceAtLeast(0))
                    modeStore.update { leaveToNormal(it) }
                    FocusAdminReceiver.deactivate(context)
                }
            }
        }
        scope.launch {
            modeStore.state.map { it.emergency.phase }.distinctUntilChanged().collectLatest { phase ->
                if (phase == EmergencyState.Phase.WAITING) countEmergencyTime()
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
            var finished = false
            var leftStrict = false
            modeStore.update { s ->
                val ticked = s.copy(emergency = EmergencyUnlock.tick(s.emergency, elapsed))
                if (!EmergencyUnlock.isDone(ticked.emergency)) return@update ticked
                finished = true
                leftStrict = s.mode != Mode.NORMAL
                finishEmergency(ticked, System.currentTimeMillis())
            }
            if (leftStrict) FocusAdminReceiver.deactivate(context)
            if (finished) return
        }
    }

    companion object {
        private const val TICK_MS = 30_000L

        /** The emergency wait is over: back to Normal, adult-only Strict released, the unlock recorded. */
        fun finishEmergency(state: ModeState, nowMs: Long): ModeState {
            val done = state.copy(
                emergency = EmergencyUnlock.complete(state.emergency, nowMs),
                adultStrictUntil = null,
                adultStrictSchedule = null,
            )
            return if (state.mode != Mode.NORMAL) leaveToNormal(done) else done
        }

        /** Back to Normal: password, strict settings and any running unlock are cleared. History and adult-strict stay. */
        fun leaveToNormal(state: ModeState) = ModeState(
            lock = state.lock,
            strict = state.strict,
            adultStrictUntil = state.adultStrictUntil,
            adultStrictSchedule = state.adultStrictSchedule,
            sessionUntil = state.sessionUntil,
            emergency = EmergencyState(history = state.emergency.history),
        )
    }
}
