package dev.sushil.focusblock.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Slow, salted password hashing (PBKDF2). The password itself is never stored: only
 * "iterations:salt:hash". Slow on purpose, so guessing many passwords takes ages.
 */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(password: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, iterations)
        val b64 = Base64.getEncoder()
        return "$iterations:${b64.encodeToString(salt)}:${b64.encodeToString(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val expected = b64.decode(parts[2])
        // Constant-time compare: doesn't leak how many bytes matched
        return MessageDigest.isEqual(derive(password, b64.decode(parts[1]), iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS))
            .encoded

    /** Easy-to-type recovery code like "K7QM-3XRP-W9TD-HV4N" (no 0/O, 1/I/L look-alikes). */
    fun newRecoveryCode(random: SecureRandom = SecureRandom()): String =
        (1..4).joinToString("-") { (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("") }

    /** Recovery codes are compared without dashes/spaces and case-insensitively. */
    fun normalizeRecoveryCode(code: String) = code.uppercase().filter { it.isLetterOrDigit() }

    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
}
