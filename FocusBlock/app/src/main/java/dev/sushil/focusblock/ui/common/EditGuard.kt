package dev.sushil.focusblock.ui.common

import dev.sushil.focusblock.data.ModeStore
import dev.sushil.focusblock.domain.StrictPolicy
import dev.sushil.focusblock.domain.model.Profile
import dev.sushil.focusblock.service.UnlockGrants
import java.time.LocalDateTime

sealed interface EditCheck {
    data object Allowed : EditCheck
    data object NeedsPassword : EditCheck
    data class Forbidden(val reason: String) : EditCheck
}

/** Decides whether a profile change may go ahead in the current mode. */
class EditGuard(private val modeStore: ModeStore, private val grants: UnlockGrants) {

    suspend fun check(old: Profile?, new: Profile?): EditCheck {
        // Making rules stricter is always fine, in every mode
        if (StrictPolicy.isAllowed(old, new)) return EditCheck.Allowed
        val state = modeStore.current()
        if (state.isStrictActive(LocalDateTime.now(), System.currentTimeMillis())) {
            return EditCheck.Forbidden(
                "Strict mode is on. You can add apps, lower limits or create profiles, but not loosen or remove rules.",
            )
        }
        if (state.isLock && state.lock.lockProfiles && state.passwordHash != null &&
            !grants.isGranted(UnlockGrants.Target.PROFILES)
        ) {
            return EditCheck.NeedsPassword
        }
        return EditCheck.Allowed
    }

    fun grantProfiles() = grants.grant(UnlockGrants.Target.PROFILES)
}
