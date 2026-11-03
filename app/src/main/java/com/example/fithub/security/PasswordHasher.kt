package com.example.fithub.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password protection for the local (offline) copy of the user's credentials.
 *
 * Passwords are never stored in plain text anywhere:
 *  - Firebase Authentication hashes them on Google's servers (scrypt).
 *  - On the device we keep only a salted PBKDF2-HMAC-SHA256 hash so the user can
 *    log in while offline. The hash cannot be reversed into the password.
 *  - Firestore never receives the password at all.
 */
object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 60_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun generateSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt.toHex()
    }

    fun hash(password: String, saltHex: String): String {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, saltHex.hexToBytes(), ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    /** Constant-time comparison so timing doesn't leak how much of the hash matched. */
    fun verify(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val actual = hash(password, saltHex).hexToBytes()
        val expected = expectedHashHex.hexToBytes()
        return MessageDigest.isEqual(actual, expected)
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string" }
        return ByteArray(length / 2) { i ->
            ((Character.digit(this[i * 2], 16) shl 4) + Character.digit(this[i * 2 + 1], 16)).toByte()
        }
    }
}
