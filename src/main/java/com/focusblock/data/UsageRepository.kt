package com.focusblock.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.focusblock.domain.UsageSource
import com.focusblock.util.Permissions
import java.time.LocalDateTime
import java.time.ZoneId

/** Usage per app today, read from Android's own records. Returns null without usage access. */
class UsageRepository(private val context: Context) : UsageSource {

    /** Updated from settings; the hour the usage "day" rolls over. */
    @Volatile var dayResetHour: Int = 0

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    override fun usedTodayMs(pkg: String): Long? = foregroundMsSince(pkg, startOfToday())

    override fun usedThisHourMs(pkg: String): Long? = foregroundMsSince(pkg, startOfHour())

    /**
     * Foreground milliseconds for [pkg] between [start] and now, from raw events.
     *
     * A foreground span starts on ACTIVITY_RESUMED and ends on ACTIVITY_PAUSED/STOPPED,
     * on the screen turning off, or on shutdown. Ending on screen-off fixes the old
     * over-count (a resume with no pause used to count as "open all day"), and counting
     * the still-open span up to now includes the session the user is in right now, which
     * Android's aggregated daily total doesn't report until later.
     */
    private fun foregroundMsSince(pkg: String, start: Long): Long? {
        if (!Permissions.hasUsageAccess(context)) return null
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(start, now)
        val event = UsageEvents.Event()
        var total = 0L
        var since: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (event.packageName == pkg && since == null) since = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == pkg) since = endSpan(since, event.timeStamp) { total += it }
                // Global events (any package): the app is no longer in front
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    since = endSpan(since, event.timeStamp) { total += it }
            }
        }
        since?.let { total += now - it } // still in front right now
        return total
    }

    private inline fun endSpan(since: Long?, endAt: Long, add: (Long) -> Unit): Long? {
        since?.let { add(endAt - it) }
        return null
    }

    /** A launch = the app comes to the front after a different app was in front. */
    override fun launchesToday(pkg: String): Int? {
        if (!Permissions.hasUsageAccess(context)) return null
        val events = usageStats.queryEvents(startOfToday(), System.currentTimeMillis())
        val event = UsageEvents.Event()
        var count = 0
        var lastResumed: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.packageName == pkg && lastResumed != pkg) count++
                lastResumed = event.packageName
            }
        }
        return count
    }

    // Recomputed each call: the date/hour can change (midnight, time zone, manual clock change)
    private fun startOfToday(): Long {
        val now = LocalDateTime.now()
        val resetToday = now.toLocalDate().atTime(dayResetHour, 0)
        val start = if (now.isBefore(resetToday)) resetToday.minusDays(1) else resetToday
        return start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun startOfHour() = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
