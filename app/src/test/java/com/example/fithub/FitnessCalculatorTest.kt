package com.example.fithub

import com.example.fithub.util.FitnessCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FitnessCalculatorTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun pointsCombineDistanceAndTime() {
        // 2 km = 20 pts, 15 min = 15 pts
        assertEquals(35, FitnessCalculator.points(2000.0, 15 * 60))
        assertEquals(0, FitnessCalculator.points(0.0, 30))
        assertEquals(0, FitnessCalculator.points(-50.0, -10)) // never negative
    }

    @Test
    fun stepConversions() {
        assertEquals(0.762, FitnessCalculator.stepsToKm(1000), 0.0001)
        assertEquals(1312, FitnessCalculator.estimateSteps(1000.0))
        assertEquals(0, FitnessCalculator.estimateSteps(-1.0))
        assertEquals(50, FitnessCalculator.stepPoints(5000))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5))
        assertEquals(3, FitnessCalculator.calculateStreak(days, today))
    }

    @Test
    fun streakStillCountsIfLastWorkoutWasYesterday() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, FitnessCalculator.calculateStreak(days, today))
    }

    @Test
    fun streakIsZeroAfterAMissedDay() {
        assertEquals(0, FitnessCalculator.calculateStreak(setOf(today.minusDays(2)), today))
        assertEquals(0, FitnessCalculator.calculateStreak(emptySet(), today))
    }

    @Test
    fun formatting() {
        assertEquals("01:01:05", FitnessCalculator.formatDuration(3665))
        assertEquals("00:00:00", FitnessCalculator.formatDuration(-3))
        assertEquals("1.50 km", FitnessCalculator.formatKm(1500.0))
    }

    @Test
    fun paceNeedsEnoughDistance() {
        assertNull(FitnessCalculator.paceMinPerKm(10.0, 60))
        val pace = FitnessCalculator.paceMinPerKm(5000.0, 25 * 60)
        assertNotNull(pace)
        assertEquals(5.0, pace!!, 0.001)
    }
}
