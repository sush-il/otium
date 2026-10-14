package com.focusblock.service

import android.os.SystemClock

/** Things unlocked with the Lock-mode password, for a few minutes or until the screen goes off. */
class UnlockGrants {
    enum class Target { APP, SETTINGS, INSTALLER, PROFILES }

    private val until = mutableMapOf<Target, Long>()

    @Synchronized
    fun grant(target: Target, durationMs: Long = DEFAULT_MS) {
        until[target] = SystemClock.elapsedRealtime() + durationMs
    }

    @Synchronized
    fun isGranted(target: Target): Boolean = (until[target] ?: 0) > SystemClock.elapsedRealtime()

    @Synchronized
    fun clear() = until.clear()

    companion object {
        const val DEFAULT_MS = 5 * 60_000L
    }
}
