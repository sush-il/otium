package dev.sushil.focusblock.ui.lock

import dev.sushil.focusblock.data.ModeStore
import dev.sushil.focusblock.domain.PasswordHasher
import dev.sushil.focusblock.domain.model.Mode
import dev.sushil.focusblock.service.ModeKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class PasswordResult { PASSWORD, RECOVERY_CODE, WRONG }

/**
 * Checks input against the Lock-mode password, then the recovery code. A correct
 * recovery code turns Lock mode off (the user sets a new password afterwards).
 */
suspend fun checkPassword(modeStore: ModeStore, input: String): PasswordResult = withContext(Dispatchers.Default) {
    val state = modeStore.current()
    when {
        state.passwordHash != null && PasswordHasher.verify(input, state.passwordHash) -> PasswordResult.PASSWORD
        state.recoveryHash != null &&
            PasswordHasher.verify(PasswordHasher.normalizeRecoveryCode(input), state.recoveryHash) -> {
            modeStore.update { if (it.mode == Mode.LOCK) ModeKeeper.leaveToNormal(it) else it }
            PasswordResult.RECOVERY_CODE
        }
        else -> {
            delay(1_000) // slows down guessing a little more
            PasswordResult.WRONG
        }
    }
}
