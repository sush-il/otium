package dev.sushil.focusblock.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    // Fewer iterations keep the test fast; the format and logic are the same
    @Test fun `verifies the right password only`() {
        val stored = PasswordHasher.hash("hunter22", iterations = 1_000)
        assertTrue(PasswordHasher.verify("hunter22", stored))
        assertFalse(PasswordHasher.verify("hunter23", stored))
    }

    @Test fun `same password hashes differently each time (salt)`() {
        assertNotEquals(PasswordHasher.hash("hunter22", 1_000), PasswordHasher.hash("hunter22", 1_000))
    }

    @Test fun `garbage stored value never verifies`() = assertFalse(PasswordHasher.verify("x", "nope"))

    @Test fun `recovery code normalisation`() {
        val code = PasswordHasher.newRecoveryCode()
        assertTrue(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}").matches(code))
        assertTrue(PasswordHasher.normalizeRecoveryCode(code.lowercase().replace("-", " ")) == code.replace("-", ""))
    }
}
