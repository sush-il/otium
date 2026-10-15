package dev.sushil.otium.domain.model

sealed interface BlockDecision {
    data object Allowed : BlockDecision
    data class Blocked(val reason: String, val profileName: String) : BlockDecision
}

/** Time until an open app gets blocked, for the on-screen timer and alerts. */
data class TimeLeft(val kind: Kind, val duration: java.time.Duration) {
    enum class Kind {
        /** Daily limit runs out: "12:34 left today" */
        LIMIT,
        /** A schedule window starts soon: "blocked in 12:34" */
        SCHEDULE_START,
    }
}
