package com.softandtech.tuitionmanager.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the app-lock PIN only as a salted, slow hash (PBKDF2), never as the PIN itself.
 * HMAC-SHA1 is used because PBKDF2WithHmacSHA256 isn't available before Android 8; with
 * PBKDF2 that's still sound.
 */
object PinHasher {
    const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    val PIN_LENGTHS = 4..6

    fun isValidPin(pin: String): Boolean = pin.length in PIN_LENGTHS && pin.all(Char::isDigit)

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison so timing can't reveal how much of the PIN was right. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)
}
