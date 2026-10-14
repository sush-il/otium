package com.focusblock.ui.common

import com.focusblock.data.ModeStore
import com.focusblock.domain.StrictPolicy
import com.focusblock.domain.model.Profile
import java.time.LocalDateTime

sealed interface EditCheck {
    data object Allowed : EditCheck
    data object NeedsPassword : EditCheck
    data class Forbidden(val reason: String) : EditCheck
}

/** Decides whether a profile change may go ahead in the current mode. */
class EditGuard(private val modeStore: ModeStore) {

    suspend fun check(old: Profile?, new: Profile?): EditCheck {
        // Making rules stricter is always fine, in every mode
        if (StrictPolicy.isAllowed(old, new)) return EditCheck.Allowed
        val state = modeStore.current()
        if (state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) {
            return EditCheck.Forbidden(
                "Strict mode is on. You can add apps, lower limits or create profiles, but not loosen or remove rules.",
            )
        }
        if (state.isLock && state.lock.lockProfiles && state.hasLock)  {
            return EditCheck.NeedsPassword
        }
        return EditCheck.Allowed
    }

    /** Pausing is loosening, so it follows the same mode rules as editing. */
    suspend fun checkPause(profile: Profile): EditCheck {
        if (!profile.isEnforcing(LocalDateTime.now())) return EditCheck.Allowed
        val state = modeStore.current()
        if (state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) {
            return EditCheck.Forbidden("Strict mode is on, so profiles can't be paused.")
        }
        if (state.isLock && state.lock.lockProfiles && state.hasLock)  {
            return EditCheck.NeedsPassword
        }
        return EditCheck.Allowed
    }

    /** Turning any protection OFF is loosening, so it follows the mode rules. */
    suspend fun checkLoosen(): EditCheck {
        val state = modeStore.current()
        if (state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) {
            return EditCheck.Forbidden("Strict mode is on, so blocking can't be turned off.")
        }
        if (state.isLock && state.lock.lockProfiles && state.hasLock)  {
            return EditCheck.NeedsPassword
        }
        return EditCheck.Allowed
    }
}
