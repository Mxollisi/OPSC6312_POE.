package com.example.fithub.util

/**
 * Input validation used by every form in the app. Pure Kotlin so it can be unit tested
 * on the JVM (see ValidatorsTest) and so bad input never reaches Firebase or SQLite.
 */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val MIN_AGE = 13
    const val MAX_AGE = 100
    const val MIN_STEP_GOAL = 500
    const val MAX_STEP_GOAL = 100_000
    const val MIN_PASSWORD_LENGTH = 8

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    /** At least 8 characters with at least one letter and one digit. */
    fun isStrongPassword(password: String): Boolean =
        password.length >= MIN_PASSWORD_LENGTH &&
            password.any { it.isLetter() } &&
            password.any { it.isDigit() }

    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in 2..50 && trimmed.none { it.isDigit() }
    }

    /** Returns the age if it is a whole number between 13 and 100, otherwise null. */
    fun parseAge(input: String): Int? =
        input.trim().toIntOrNull()?.takeIf { it in MIN_AGE..MAX_AGE }

    /** Returns the goal if it is a whole number between 500 and 100 000, otherwise null. */
    fun parseStepGoal(input: String): Int? =
        input.trim().toIntOrNull()?.takeIf { it in MIN_STEP_GOAL..MAX_STEP_GOAL }

    /** Keeps only digits and caps the length, so number fields can't hold junk. */
    fun digitsOnly(input: String, maxLength: Int): String =
        input.filter { it.isDigit() }.take(maxLength)
}
