package dev.sushil.otium.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.sushil.otium.domain.UsageSource
import dev.sushil.otium.util.Permissions
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
     * The app is in front while at least one of its activities is resumed. Moving between
     * two screens of one app (e.g. Instagram feed -> comments) arrives as PAUSED(A),
     * RESUMED(B), STOPPED(A); tracking activities individually stops that STOPPED(A) from
     * ending B's span, which used to make the used time drop and the timer jump back up.
     *
     * A span also ends on the screen turning off or on shutdown, and the still-open span
     * counts up to now (Android's aggregated daily total only reports it later).
     */
    private fun foregroundMsSince(pkg: String, start: Long): Long? {
        if (!Permissions.hasUsageAccess(context)) return null
        val now = System.currentTimeMillis()
        // Look back before [start] so a session already open at the hour/day boundary is
        // still seen; its time is clipped to [start] below.
        val events = usageStats.queryEvents(start - LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        val resumed = HashSet<String>()
        var total = 0L
        var since: Long? = null
        fun endSpan(at: Long) {
            since?.let { total += (at - maxOf(it, start)).coerceAtLeast(0) }
            since = null
            resumed.clear()
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    if (event.packageName == pkg) {
                        resumed.add(event.className.orEmpty())
                        if (since == null) since = event.timeStamp
                    }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == pkg) {
                        resumed.remove(event.className.orEmpty())
                        if (resumed.isEmpty()) endSpan(event.timeStamp)
                    }
                // Global events (any package): the app is no longer in front
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    endSpan(event.timeStamp)
            }
        }
        since?.let { total += (now - maxOf(it, start)).coerceAtLeast(0) } // still in front right now
        return total
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

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
