package com.prempoddar.campfire.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Room password hashing: PBKDF2-SHA256 with a random salt, stored as `iterations$salt$hash`. */
object Passwords {
    private const val ITERATIONS = 10_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder().withoutPadding()

    fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return "$ITERATIONS$${b64.encodeToString(salt)}$${b64.encodeToString(derive(password, salt, ITERATIONS))}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}
