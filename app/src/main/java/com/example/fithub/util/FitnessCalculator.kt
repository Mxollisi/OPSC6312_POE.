package com.example.fithub.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Pure fitness maths used by the dashboard, tracking and history screens. */
object FitnessCalculator {

    /** Average adult stride length in metres. */
    const val METERS_PER_STEP = 0.762

    /** Activities shorter than this are not saved. */
    const val MIN_WORKOUT_SECONDS = 10L

    fun stepsToKm(steps: Int): Double = steps.coerceAtLeast(0) * METERS_PER_STEP / 1000.0

    fun estimateSteps(distanceMeters: Double): Int =
        if (distanceMeters <= 0.0) 0 else (distanceMeters / METERS_PER_STEP).toInt()

    /** 10 points per kilometre + 1 point per active minute. */
    fun points(distanceMeters: Double, durationSec: Long): Int {
        val distancePoints = (distanceMeters.coerceAtLeast(0.0) / 100.0).toInt()
        val timePoints = (durationSec.coerceAtLeast(0) / 60).toInt()
        return distancePoints + timePoints
    }

    /** Points earned from passive steps on the dashboard (1 point per 100 steps). */
    fun stepPoints(steps: Int): Int = steps.coerceAtLeast(0) / 100

    /**
     * Number of consecutive active days ending today (or yesterday, so the streak
     * isn't lost before the user has had a chance to work out today).
     */
    fun calculateStreak(activeDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays) today else today.minusDays(1)
        var streak = 0
        while (day in activeDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun toLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun formatDuration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun formatKm(distanceMeters: Double): String =
        String.format(Locale.US, "%.2f km", distanceMeters.coerceAtLeast(0.0) / 1000.0)

    /** Pace in minutes per km, or null when there isn't enough distance to measure. */
    fun paceMinPerKm(distanceMeters: Double, durationSec: Long): Double? {
        if (distanceMeters < 50.0 || durationSec <= 0) return null
        return (durationSec / 60.0) / (distanceMeters / 1000.0)
    }
}
