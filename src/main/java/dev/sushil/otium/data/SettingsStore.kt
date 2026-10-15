package dev.sushil.otium.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /** Apps a whole-phone profile should never block (e.g. Phone, Messages, Maps). */
    val excludedApps: Flow<Set<String>> = store.data.map { it[EXCLUDED_APPS] ?: emptySet() }

    /** Hosts blocked in browsers, e.g. "instagram.com". Always on when the service runs. */
    val blockedSites: Flow<Set<String>> = store.data.map { it[BLOCKED_SITES] ?: emptySet() }

    /** Words that block a page/search when they appear in the address bar. */
    val blockedKeywords: Flow<Set<String>> = store.data.map { it[BLOCKED_KEYWORDS] ?: emptySet() }

    val blockYoutubeShorts: Flow<Boolean> = store.data.map { it[BLOCK_YT_SHORTS] ?: false }
    val blockInstagramReels: Flow<Boolean> = store.data.map { it[BLOCK_IG_REELS] ?: false }
    val blockFacebookReels: Flow<Boolean> = store.data.map { it[BLOCK_FB_REELS] ?: false }
    val blockAdult: Flow<Boolean> = store.data.map { it[BLOCK_ADULT] ?: false }

    /** Apps blocked directly from the dashboard, without a profile (always blocked). */
    val quickBlockedApps: Flow<Set<String>> = store.data.map { it[QUICK_BLOCKED_APPS] ?: emptySet() }

    /** Master switch for app blocking (profiles + the direct list). On by default. */
    val appBlockingEnabled: Flow<Boolean> = store.data.map { it[APP_BLOCKING] ?: true }

    /** Whether the keyword list is active (sites are always active when non-empty). */
    val keywordBlockingEnabled: Flow<Boolean> = store.data.map { it[KEYWORD_BLOCKING] ?: true }

    /** One switch for all three feeds: on if any feed is blocked. */
    val reelsBlockingEnabled: Flow<Boolean> =
        combine(blockYoutubeShorts, blockInstagramReels, blockFacebookReels) { yt, ig, fb -> yt || ig || fb }

    /** Whether the app picker also lists system apps. Off by default. */
    val showSystemApps: Flow<Boolean> = store.data.map { it[SHOW_SYSTEM_APPS] ?: false }

    /** Hour (0-23) when the "day" resets for daily limits and launch counts. Default midnight. */
    val dayResetHour: Flow<Int> = store.data.map { (it[DAY_RESET_HOUR] ?: 0).coerceIn(0, 23) }

    /** Apps/sites whose limits are paused: "app:<package>" or "site:<host>". Limits stay saved. */
    val pausedLimits: Flow<Set<String>> = store.data.map { it[PAUSED_LIMITS] ?: emptySet() }

    suspend fun setPausedLimits(value: Set<String>) = store.edit { it[PAUSED_LIMITS] = value }

    suspend fun setTimerDisplay(value: TimerDisplay) {
        store.edit { it[TIMER_DISPLAY] = value.name }
    }

    suspend fun setLowTimeAlerts(value: Boolean) {
        store.edit { it[LOW_TIME_ALERTS] = value }
    }

    suspend fun setExcludedApps(value: Set<String>) {
        store.edit { it[EXCLUDED_APPS] = value }
    }

    suspend fun setBlockedSites(value: Set<String>) {
        store.edit { it[BLOCKED_SITES] = value }
    }

    suspend fun setBlockedKeywords(value: Set<String>) {
        store.edit { it[BLOCKED_KEYWORDS] = value }
    }

    suspend fun setBlockYoutubeShorts(value: Boolean) = store.edit { it[BLOCK_YT_SHORTS] = value }
    suspend fun setBlockInstagramReels(value: Boolean) = store.edit { it[BLOCK_IG_REELS] = value }
    suspend fun setBlockFacebookReels(value: Boolean) = store.edit { it[BLOCK_FB_REELS] = value }
    suspend fun setBlockAdult(value: Boolean) = store.edit { it[BLOCK_ADULT] = value }

    suspend fun setQuickBlockedApps(value: Set<String>) = store.edit { it[QUICK_BLOCKED_APPS] = value }
    suspend fun setAppBlockingEnabled(value: Boolean) = store.edit { it[APP_BLOCKING] = value }
    suspend fun setKeywordBlockingEnabled(value: Boolean) = store.edit { it[KEYWORD_BLOCKING] = value }

    /** Flip all three feed blockers together from the home quick control. */
    suspend fun setReelsBlockingEnabled(value: Boolean) = store.edit {
        it[BLOCK_YT_SHORTS] = value
        it[BLOCK_IG_REELS] = value
        it[BLOCK_FB_REELS] = value
    }

    suspend fun setShowSystemApps(value: Boolean) = store.edit { it[SHOW_SYSTEM_APPS] = value }
    suspend fun setDayResetHour(value: Int) = store.edit { it[DAY_RESET_HOUR] = value.coerceIn(0, 23) }

    private companion object {
        val PAUSED_LIMITS = stringSetPreferencesKey("paused_limits")
        val TIMER_DISPLAY = stringPreferencesKey("timer_display")
        val LOW_TIME_ALERTS = booleanPreferencesKey("low_time_alerts")
        val EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        val BLOCKED_SITES = stringSetPreferencesKey("blocked_sites")
        val BLOCKED_KEYWORDS = stringSetPreferencesKey("blocked_keywords")
        val BLOCK_YT_SHORTS = booleanPreferencesKey("block_youtube_shorts")
        val BLOCK_IG_REELS = booleanPreferencesKey("block_instagram_reels")
        val BLOCK_FB_REELS = booleanPreferencesKey("block_facebook_reels")
        val BLOCK_ADULT = booleanPreferencesKey("block_adult")
        val QUICK_BLOCKED_APPS = stringSetPreferencesKey("quick_blocked_apps")
        val APP_BLOCKING = booleanPreferencesKey("app_blocking_enabled")
        val KEYWORD_BLOCKING = booleanPreferencesKey("keyword_blocking_enabled")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val DAY_RESET_HOUR = intPreferencesKey("day_reset_hour")
    }
}
