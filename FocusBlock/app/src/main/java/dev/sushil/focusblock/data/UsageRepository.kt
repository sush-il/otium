package dev.sushil.focusblock.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.sushil.focusblock.domain.UsageSource
import dev.sushil.focusblock.util.Permissions
import java.time.LocalDateTime
import java.time.ZoneId

/** Usage per app today, read from Android's own records. Returns null without usage access. */
class UsageRepository(private val context: Context) : UsageSource {

    /** Updated from settings; the hour the usage "day" rolls over. */
    @Volatile var dayResetHour: Int = 0

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    override fun usedTodayMs(pkg: String): Long? = foregroundMsSince(pkg, startOfToday())

    override fun usedThisHourMs(pkg: String): Long? = foregroundMsSince(pkg, startOfHour())

    /** Milliseconds [pkg] was in the foreground between [start] and now. */
    private fun foregroundMsSince(pkg: String, start: Long): Long? {
        if (!Permissions.hasUsageAccess(context)) return null
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(start, now)
        val event = UsageEvents.Event()
        var total = 0L
        var resumedAt: Long? = null
        var seenAny = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (resumedAt == null) resumedAt = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    // A pause with no resume before it: the app was already open at [start]
                    val from = resumedAt ?: if (!seenAny) start else null
                    if (from != null) total += event.timeStamp - from
                    resumedAt = null
                }
            }
            seenAny = true
        }
        resumedAt?.let { total += now - it } // still open
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
}
