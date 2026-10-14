package dev.sushil.focusblock.domain.model

sealed interface BlockDecision {
    data object Allowed : BlockDecision
    data class Blocked(val reason: String, val profileName: String) : BlockDecision
}
