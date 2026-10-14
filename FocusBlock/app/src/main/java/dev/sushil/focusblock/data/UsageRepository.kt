package dev.sushil.focusblock.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.sushil.focusblock.util.Permissions
import java.time.LocalDate
import java.time.ZoneId

/** Today's foreground time per app, read from Android's own usage records. */
class UsageRepository(private val context: Context) {

    private val usageStats = context.getSystemService(UsageStatsManager::class.java)

    /** Milliseconds [pkg] was in the foreground since local midnight, or null without usage access. */
    fun usedTodayMs(pkg: String): Long? {
        if (!Permissions.hasUsageAccess(context)) return null
        // Recomputed every call: the date can change (midnight, time zone, manual clock change)
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val events = usageStats.queryEvents(startOfDay, now)
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
                    // A pause with no resume before it: the app was already open at midnight
                    val start = resumedAt ?: if (!seenAny) startOfDay else null
                    if (start != null) total += event.timeStamp - start
                    resumedAt = null
                }
            }
            seenAny = true
        }
        resumedAt?.let { total += now - it } // still open
        return total
    }
}
