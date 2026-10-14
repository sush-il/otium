package com.focusblock.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * Finds the app that most recently came to the front, from Android's usage events.
 * Same idea as the reference app: look at the last few seconds of events, take the
 * newest "activity resumed".
 */
class ForegroundPoller(private val usageStats: UsageStatsManager) {

    // Only report each resume once, even though query windows overlap
    private var lastReportedAt = 0L

    /** The package that came to the front since the last call, or null if nothing changed. */
    fun poll(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - LOOKBACK_MS, now) ?: return null
        val event = UsageEvents.Event()
        var newest: String? = null
        var newestAt = lastReportedAt
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > newestAt) {
                newest = event.packageName
                newestAt = event.timeStamp
            }
        }
        lastReportedAt = newestAt
        return newest
    }

    private companion object {
        // Events can show up slightly late, so look back a bit further than the poll interval
        const val LOOKBACK_MS = 5_000L
    }
}
