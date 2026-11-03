package com.example.fithub

import com.example.fithub.security.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun hashIsNotThePlainPassword() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("fithub2026", salt)
        assertNotEquals("fithub2026", hash)
        assertFalse(hash.contains("fithub2026"))
        assertEquals(64, hash.length) // 256-bit key as hex
    }

    @Test
    fun correctPasswordVerifies() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("fithub2026", salt)
        assertTrue(PasswordHasher.verify("fithub2026", salt, hash))
    }

    @Test
    fun wrongPasswordFails() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hash("fithub2026", salt)
        assertFalse(PasswordHasher.verify("fithub2027", salt, hash))
        assertFalse(PasswordHasher.verify("", salt, hash))
    }

    @Test
    fun samePasswordWithDifferentSaltsGivesDifferentHashes() {
        val a = PasswordHasher.hash("fithub2026", PasswordHasher.generateSalt())
        val b = PasswordHasher.hash("fithub2026", PasswordHasher.generateSalt())
        assertNotEquals(a, b)
    }

    @Test
    fun hashingIsDeterministicForTheSameSalt() {
        val salt = PasswordHasher.generateSalt()
        assertEquals(PasswordHasher.hash("abc12345", salt), PasswordHasher.hash("abc12345", salt))
    }
}
