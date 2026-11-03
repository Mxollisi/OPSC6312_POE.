package com.example.fithub.data.repository

import com.example.fithub.data.local.FitHubDatabase
import com.example.fithub.data.model.Exercise
import com.example.fithub.data.remote.WgerApi
import com.example.fithub.data.remote.WgerMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExerciseResult(
    val exercises: List<Exercise>,
    val fromCache: Boolean,
    val failed: Boolean
)

/**
 * Exercise library backed by the wger REST API, with an SQLite cache so the
 * library still works offline.
 */
class ExerciseRepository(
    private val db: FitHubDatabase,
    private val api: WgerApi
) {
    suspend fun load(forceRefresh: Boolean): ExerciseResult = withContext(Dispatchers.IO) {
        val cached = try { db.getExercises() } catch (e: Exception) { emptyList() }

        if (!forceRefresh && cached.isNotEmpty()) {
            return@withContext ExerciseResult(cached, fromCache = true, failed = false)
        }

        try {
            val fresh = WgerMapper.toExercises(api.getExercises())
            if (fresh.isNotEmpty()) {
                db.replaceExercises(fresh)
                ExerciseResult(fresh, fromCache = false, failed = false)
            } else {
                ExerciseResult(cached, fromCache = true, failed = cached.isEmpty())
            }
        } catch (e: Exception) {
            // Offline or API unavailable: fall back to the cache.
            ExerciseResult(cached, fromCache = true, failed = true)
        }
    }
}
