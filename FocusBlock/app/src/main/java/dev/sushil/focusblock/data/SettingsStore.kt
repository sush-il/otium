package dev.sushil.focusblock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class TimerDisplay { FLOATING, NOTIFICATION, HIDDEN }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small app-wide settings (not rules). Stored with DataStore, read as Flows. */
class SettingsStore(context: Context) {
    private val store = context.settingsDataStore

    val timerDisplay: Flow<TimerDisplay> = store.data.map { prefs ->
        prefs[TIMER_DISPLAY]?.let { runCatching { TimerDisplay.valueOf(it) }.getOrNull() } ?: TimerDisplay.FLOATING
    }

    val lowTimeAlerts: Flow<Boolean> = store.data.map { it[LOW_TIME_ALERTS] ?: true }

    suspend fun setTimerDisplay(value: TimerDisplay) {
        store.edit { it[TIMER_DISPLAY] = value.name }
    }

    suspend fun setLowTimeAlerts(value: Boolean) {
        store.edit { it[LOW_TIME_ALERTS] = value }
    }

    private companion object {
        val TIMER_DISPLAY = stringPreferencesKey("timer_display")
        val LOW_TIME_ALERTS = booleanPreferencesKey("low_time_alerts")
    }
}
