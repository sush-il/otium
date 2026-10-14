package dev.sushil.focusblock.domain

/**
 * How much the user has used an app, for the limit rules. Each method returns null
 * when usage access isn't granted, which the evaluator treats as "fail closed".
 */
interface UsageSource {
    /** Foreground milliseconds today (since local midnight). */
    fun usedTodayMs(pkg: String): Long?

    /** Foreground milliseconds in the current clock hour. */
    fun usedThisHourMs(pkg: String): Long?

    /** Times the app came to the front today. */
    fun launchesToday(pkg: String): Int?
}
