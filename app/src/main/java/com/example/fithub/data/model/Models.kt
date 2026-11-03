package com.example.fithub.data.model

/** A completed activity. Stored in SQLite first, then synced to Firestore. */
data class Workout(
    val localId: Long = 0,
    val userId: String,
    val type: String,
    val startTime: Long,
    val durationSec: Long,
    val distanceMeters: Double,
    val steps: Int,
    val points: Int,
    val synced: Boolean = false
) {
    /** Deterministic Firestore id, so retrying a sync never creates duplicates. */
    val remoteId: String get() = "w_${startTime}_$localId"

    companion object {
        const val TYPE_RUN = "run"
        const val TYPE_WALK = "walk"
        const val TYPE_CYCLE = "cycle"
        val TYPES = listOf(TYPE_RUN, TYPE_WALK, TYPE_CYCLE)
    }
}

/** An exercise from the wger REST API, cached in SQLite for offline use. */
data class Exercise(
    val id: Int,
    val name: String,
    val category: String,
    val description: String,
    val muscles: String,
    val equipment: String,
    val imageUrl: String?
)

/** The signed-in user for this device (works for online and offline logins). */
data class Session(
    val uid: String,
    val email: String,
    val displayName: String,
    val provider: String,
    val offline: Boolean
) {
    companion object {
        const val PROVIDER_PASSWORD = "password"
        const val PROVIDER_GOOGLE = "google"
    }
}

data class AppSettings(
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val dailyStepGoal: Int = 8000,
    val fitnessLevel: String = "beginner",
    val age: Int? = null,
    val lastSyncTime: Long = 0L
)
