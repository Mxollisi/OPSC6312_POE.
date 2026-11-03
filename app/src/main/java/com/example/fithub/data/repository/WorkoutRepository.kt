package com.example.fithub.data.repository

import com.example.fithub.data.local.FitHubDatabase
import com.example.fithub.data.model.Workout
import com.example.fithub.util.FitnessCalculator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.Date

data class WorkoutStats(
    val todayDistanceMeters: Double = 0.0,
    val todayDurationSec: Long = 0,
    val todaySteps: Int = 0,
    val todayPoints: Int = 0,
    val totalPoints: Int = 0,
    val totalWorkouts: Int = 0,
    val streakDays: Int = 0,
    val pendingSync: Int = 0
)

/**
 * Offline-first workout storage.
 * 1. save() always writes to SQLite (works with no connection).
 * 2. syncPending() uploads unsynced rows to Firestore: users/{uid}/workouts/{id}
 *    It's called by SyncWorker whenever the device is online.
 */
class WorkoutRepository(
    private val db: FitHubDatabase,
    private val firestoreProvider: () -> FirebaseFirestore
) {
    /** Bumped after every change so screens know to reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    suspend fun save(workout: Workout): Long = withContext(Dispatchers.IO) {
        val id = db.insertWorkout(workout.copy(synced = false))
        _version.value++
        id
    }

    suspend fun delete(workout: Workout) {
        withContext(Dispatchers.IO) { db.deleteWorkout(workout.localId) }
        if (workout.synced) {
            // Firestore queues the delete if offline.
            try {
                firestoreProvider().collection("users").document(workout.userId)
                    .collection("workouts").document(workout.remoteId).delete()
            } catch (e: Exception) {
                // Local copy is already gone; cloud copy can be removed from the console.
            }
        }
        _version.value++
    }

    suspend fun getWorkouts(userId: String): List<Workout> = withContext(Dispatchers.IO) {
        db.getWorkouts(userId)
    }

    suspend fun getStats(userId: String): WorkoutStats = withContext(Dispatchers.IO) {
        val all = db.getWorkouts(userId)
        val today = LocalDate.now()
        val todays = all.filter { FitnessCalculator.toLocalDate(it.startTime) == today }
        val activeDays = all.map { FitnessCalculator.toLocalDate(it.startTime) }.toSet()
        WorkoutStats(
            todayDistanceMeters = todays.sumOf { it.distanceMeters },
            todayDurationSec = todays.sumOf { it.durationSec },
            todaySteps = todays.sumOf { it.steps },
            todayPoints = todays.sumOf { it.points },
            totalPoints = all.sumOf { it.points },
            totalWorkouts = all.size,
            streakDays = FitnessCalculator.calculateStreak(activeDays, today),
            pendingSync = all.count { !it.synced }
        )
    }

    /** Uploads unsynced workouts. Returns how many were uploaded. Throws if the upload fails. */
    suspend fun syncPending(userId: String): Int = withContext(Dispatchers.IO) {
        val pending = db.getUnsyncedWorkouts(userId)
        if (pending.isEmpty()) return@withContext 0

        val firestore = firestoreProvider()
        val userDoc = firestore.collection("users").document(userId)
        var uploaded = 0

        for (w in pending) {
            val data = hashMapOf(
                "type" to w.type,
                "startTime" to Timestamp(Date(w.startTime)),
                "durationSec" to w.durationSec,
                "distanceMeters" to w.distanceMeters,
                "steps" to w.steps,
                "points" to w.points,
                "syncedAt" to Timestamp.now(),
                "source" to "android-offline-sync"
            )
            withTimeout(15_000L) {
                userDoc.collection("workouts").document(w.remoteId).set(data).await()
            }
            db.markWorkoutSynced(w.localId)
            uploaded++
        }

        // Keep the summary on the user document up to date.
        val stats = getStats(userId)
        withTimeout(15_000L) {
            userDoc.set(
                hashMapOf(
                    "points" to stats.totalPoints,
                    "currentStreak" to stats.streakDays,
                    "totalWorkouts" to stats.totalWorkouts,
                    "lastActiveDate" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()
        }

        _version.value++
        uploaded
    }
}
