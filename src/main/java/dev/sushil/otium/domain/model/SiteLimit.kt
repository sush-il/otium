package dev.sushil.otium.domain.model

import java.time.LocalDateTime

/**
 * One stackable rule on a blocked website host (e.g. "youtube.com"). Mirrors [AppLimit]
 * but keyed by host; the host is matched against the browser's address bar, including
 * subdomains. An app is blocked if ANY of its site's rules applies.
 */
data class SiteLimit(
    val id: Long = 0,
    val host: String,
    val type: RuleType,
    val dailyLimitMinutes: Int? = null,
    val hourlyLimitMinutes: Int? = null,
    val launchLimit: Int? = null,
    val blockUntil: LocalDateTime? = null,
    val schedule: Schedule? = null,
) {
    /** As a one-"app" [Profile] where the package key is the host, for the shared evaluator. */
    fun asProfile() = Profile(
        id = -(5000L + id),
        name = host,
        enabled = true,
        type = type,
        packages = setOf(host),
        dailyLimitMinutes = dailyLimitMinutes,
        hourlyLimitMinutes = hourlyLimitMinutes,
        launchLimit = launchLimit,
        blockUntil = blockUntil,
        schedules = schedule?.let { listOf(it) } ?: emptyList(),
    )
}
