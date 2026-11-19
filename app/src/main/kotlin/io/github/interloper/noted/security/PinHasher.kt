package io.github.interloper.noted.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the locked-labels PIN, so the PIN itself is never stored. A short numeric PIN can still be
 * brute-forced by someone holding the app's data; this guards against casual reading, not forensic extraction.
 */
object PinHasher {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    data class Hashed(val hash: String, val salt: String)

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String): Hashed {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Hashed(encode(derive(pin, salt)), encode(salt))
    }

    fun verify(pin: String, hash: String, salt: String): Boolean = try {
        MessageDigest.isEqual(derive(pin, Base64.getDecoder().decode(salt)), Base64.getDecoder().decode(hash))
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
