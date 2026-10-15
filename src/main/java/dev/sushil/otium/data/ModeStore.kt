package dev.sushil.otium.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sushil.otium.domain.model.EmergencyState
import dev.sushil.otium.domain.model.LockOptions
import dev.sushil.otium.domain.model.Mode
import dev.sushil.otium.domain.model.ModeState
import dev.sushil.otium.domain.model.Schedule
import dev.sushil.otium.domain.model.StrictOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mode")

/** Persists [ModeState]. Every change goes through [update] so it's written as one unit. */
class ModeStore(context: Context) {
    private val store = context.modeDataStore

    val state: Flow<ModeState> = store.data.map { it.toModeState() }

    suspend fun current(): ModeState = state.first()

    suspend fun update(transform: (ModeState) -> ModeState) {
        store.edit { prefs -> prefs.write(transform(prefs.toModeState())) }
    }

    private fun Preferences.toModeState() = ModeState(
        mode = this[MODE]?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.NORMAL,
        lockLength = this[LOCK_LENGTH],
        relockAt = this[RELOCK_AT],
        lock = LockOptions(
            lockApp = this[LOCK_APP] ?: false,
            lockSettings = this[LOCK_SETTINGS] ?: false,
            lockProfiles = this[LOCK_PROFILES] ?: true,
        ),
        strict = StrictOptions(
            blockSettings = this[STRICT_SETTINGS] ?: true,
            blockRecents = this[STRICT_RECENTS] ?: false,
            blockPowerMenu = this[STRICT_POWER] ?: false,
            blockPlayStore = this[STRICT_PLAY_STORE] ?: false,
        ),
        strictUntil = this[STRICT_UNTIL],
        strictSchedule = this[STRICT_DAYS]?.let { days ->
            Schedule(days, this[STRICT_START] ?: 0, this[STRICT_END] ?: 0)
        },
        sessionUntil = this[SESSION_UNTIL],
        emergency = EmergencyState(
            phase = this[EMERGENCY_PHASE]?.let { runCatching { EmergencyState.Phase.valueOf(it) }.getOrNull() }
                ?: EmergencyState.Phase.IDLE,
            remainingMs = this[EMERGENCY_REMAINING] ?: 0,
            history = this[EMERGENCY_HISTORY].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
        ),
    )

    private fun MutablePreferences.write(s: ModeState) {
        this[MODE] = s.mode.name
        setOrRemove(LOCK_LENGTH, s.lockLength)
        setOrRemove(RELOCK_AT, s.relockAt)
        this[LOCK_APP] = s.lock.lockApp
        this[LOCK_SETTINGS] = s.lock.lockSettings
        this[LOCK_PROFILES] = s.lock.lockProfiles
        this[STRICT_SETTINGS] = s.strict.blockSettings
        this[STRICT_RECENTS] = s.strict.blockRecents
        this[STRICT_POWER] = s.strict.blockPowerMenu
        this[STRICT_PLAY_STORE] = s.strict.blockPlayStore
        setOrRemove(STRICT_UNTIL, s.strictUntil)
        setOrRemove(STRICT_DAYS, s.strictSchedule?.daysMask)
        setOrRemove(STRICT_START, s.strictSchedule?.startMinute)
        setOrRemove(STRICT_END, s.strictSchedule?.endMinute)
        setOrRemove(SESSION_UNTIL, s.sessionUntil)
        this[EMERGENCY_PHASE] = s.emergency.phase.name
        this[EMERGENCY_REMAINING] = s.emergency.remainingMs
        this[EMERGENCY_HISTORY] = s.emergency.history.joinToString(",")
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val LOCK_LENGTH = intPreferencesKey("lock_length")
        val RELOCK_AT = longPreferencesKey("relock_at")
        val LOCK_APP = booleanPreferencesKey("lock_app")
        val LOCK_SETTINGS = booleanPreferencesKey("lock_settings")
        val LOCK_PROFILES = booleanPreferencesKey("lock_profiles")
        val STRICT_SETTINGS = booleanPreferencesKey("strict_block_settings")
        val STRICT_RECENTS = booleanPreferencesKey("strict_block_recents")
        val STRICT_POWER = booleanPreferencesKey("strict_block_power")
        val STRICT_PLAY_STORE = booleanPreferencesKey("strict_block_play_store")
        val STRICT_UNTIL = longPreferencesKey("strict_until")
        val STRICT_DAYS = intPreferencesKey("strict_days")
        val STRICT_START = intPreferencesKey("strict_start")
        val STRICT_END = intPreferencesKey("strict_end")
        val SESSION_UNTIL = longPreferencesKey("session_until")
        val EMERGENCY_PHASE = stringPreferencesKey("emergency_phase")
        val EMERGENCY_REMAINING = longPreferencesKey("emergency_remaining")
        val EMERGENCY_HISTORY = stringPreferencesKey("emergency_history")
    }
}
