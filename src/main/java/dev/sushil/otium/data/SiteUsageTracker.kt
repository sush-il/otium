package dev.sushil.otium.data

import android.os.SystemClock
import dev.sushil.otium.domain.UsageSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Approximate foreground time per website host, sampled while a browser shows that host.
 * Unlike apps (where Android gives exact time), this is estimated from address-bar changes,
 * and it is in-memory, so it resets if the app's process is killed (e.g. a reboot).
 *
 * [dayResetHour] controls when daily counters roll over.
 */
class SiteUsageTracker : UsageSource {

    private class Bucket {
        var day: LocalDate = LocalDate.MIN
        var dayMs = 0L
        var hour = -1
        var hourMs = 0L
        var visitsDay: LocalDate = LocalDate.MIN
        var visits = 0
    }

    @Volatile var dayResetHour: Int = 0

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private var currentHost: String? = null
    private var sinceElapsed = 0L

    /** Switch the host currently shown in the browser (null = none/left the browser). */
    @Synchronized
    fun onHost(host: String?) {
        val now = SystemClock.elapsedRealtime()
        currentHost?.let { addTime(it, now - sinceElapsed) }
        if (host != null && host != currentHost) addVisit(host)
        currentHost = host
        sinceElapsed = now
    }

    fun pause() = onHost(null)

    override fun usedTodayMs(pkg: String): Long = read(pkg) { b -> b.dayMs } + liveMs(pkg)
    override fun usedThisHourMs(pkg: String): Long = read(pkg) { b -> b.hourMs } + liveMs(pkg)
    override fun launchesToday(pkg: String): Int = read(pkg) { b -> b.visits }

    @Synchronized
    private fun liveMs(host: String): Long =
        if (host == currentHost) SystemClock.elapsedRealtime() - sinceElapsed else 0L

    @Synchronized
    private inline fun <T> read(host: String, pick: (Bucket) -> T): T {
        val b = buckets.getOrPut(host) { Bucket() }
        roll(b)
        return pick(b)
    }

    @Synchronized
    private fun addTime(host: String, elapsed: Long) {
        if (elapsed <= 0) return
        val b = buckets.getOrPut(host) { Bucket() }
        roll(b)
        b.dayMs += elapsed
        b.hourMs += elapsed
    }

    @Synchronized
    private fun addVisit(host: String) {
        val b = buckets.getOrPut(host) { Bucket() }
        roll(b)
        b.visits++
    }

    /** Reset the day/hour counters when their period has passed. */
    private fun roll(b: Bucket) {
        val now = LocalDateTime.now()
        val today = if (now.hour < dayResetHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
        if (b.day != today) {
            b.day = today
            b.dayMs = 0
        }
        if (b.visitsDay != today) {
            b.visitsDay = today
            b.visits = 0
        }
        if (b.hour != now.hour) {
            b.hour = now.hour
            b.hourMs = 0
        }
    }
}
