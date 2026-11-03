package com.example.fithub

import com.example.fithub.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun validEmailsAreAccepted() {
        assertTrue(Validators.isValidEmail("nyiko@fithub.co.za"))
        assertTrue(Validators.isValidEmail("  user.name+tag@example.com  "))
    }

    @Test
    fun invalidEmailsAreRejected() {
        assertFalse(Validators.isValidEmail(""))
        assertFalse(Validators.isValidEmail("no-at-sign.com"))
        assertFalse(Validators.isValidEmail("user@nodot"))
        assertFalse(Validators.isValidEmail("user@@example.com"))
    }

    @Test
    fun passwordStrengthRules() {
        assertTrue(Validators.isStrongPassword("fithub2026"))
        assertFalse(Validators.isStrongPassword("short1"))      // too short
        assertFalse(Validators.isStrongPassword("onlyletters")) // no digit
        assertFalse(Validators.isStrongPassword("12345678"))    // no letter
    }

    @Test
    fun namesMustBeReasonable() {
        assertTrue(Validators.isValidName("Nyiko"))
        assertFalse(Validators.isValidName("A"))
        assertFalse(Validators.isValidName("R2D2"))
        assertFalse(Validators.isValidName("   "))
    }

    @Test
    fun ageParsingHandlesBadInputWithoutCrashing() {
        assertEquals(21, Validators.parseAge("21"))
        assertEquals(21, Validators.parseAge(" 21 "))
        assertNull(Validators.parseAge("abc"))
        assertNull(Validators.parseAge(""))
        assertNull(Validators.parseAge("12"))
        assertNull(Validators.parseAge("101"))
        assertNull(Validators.parseAge("99999999999999"))
    }

    @Test
    fun stepGoalParsing() {
        assertEquals(8000, Validators.parseStepGoal("8000"))
        assertNull(Validators.parseStepGoal("100"))
        assertNull(Validators.parseStepGoal("200000"))
        assertNull(Validators.parseStepGoal("8k"))
    }

    @Test
    fun digitsOnlyStripsJunkAndCapsLength() {
        assertEquals("123", Validators.digitsOnly("1a2-3", 5))
        assertEquals("12", Validators.digitsOnly("12345", 2))
        assertEquals("", Validators.digitsOnly("abc", 3))
    }
}
