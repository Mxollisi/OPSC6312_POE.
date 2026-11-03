package com.example.fithub.data.repository

import android.content.Context
import com.example.fithub.data.model.AppSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * User settings. Saved on the device immediately (works offline) and mirrored to the
 * user's Firestore document. Firestore queues the write while offline and sends it
 * automatically when the connection returns.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fithub_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit()
            .putBoolean(K_DARK, updated.darkMode)
            .putBoolean(K_NOTIF, updated.notificationsEnabled)
            .putInt(K_GOAL, updated.dailyStepGoal)
            .putString(K_LEVEL, updated.fitnessLevel)
            .putInt(K_AGE, updated.age ?: -1)
            .putLong(K_LAST_SYNC, updated.lastSyncTime)
            .apply()
        _settings.value = updated
    }

    /** Pushes profile + preferences to Firestore without blocking the UI. */
    fun syncToCloud(uid: String, displayName: String, languageTag: String) {
        val s = _settings.value
        val data = hashMapOf<String, Any?>(
            "fullName" to displayName,
            "fitnessLevel" to s.fitnessLevel,
            "age" to s.age,
            "dailyStepGoal" to s.dailyStepGoal,
            "notificationsEnabled" to s.notificationsEnabled,
            "darkMode" to s.darkMode,
            "preferredLanguage" to languageTag,
            "settingsUpdatedAt" to com.google.firebase.Timestamp.now()
        )
        try {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(data, SetOptions.merge())
        } catch (e: Exception) {
            // Firestore not available; settings are still saved locally.
        }
    }

    /** Returns true only the first time it's called on a given day (goal-reached alert). */
    fun shouldNotifyGoalToday(): Boolean {
        val today = LocalDate.now().toString()
        if (prefs.getString(K_GOAL_NOTIFIED, null) == today) return false
        prefs.edit().putString(K_GOAL_NOTIFIED, today).apply()
        return true
    }

    fun clear() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
    }

    private fun load() = AppSettings(
        darkMode = prefs.getBoolean(K_DARK, false),
        notificationsEnabled = prefs.getBoolean(K_NOTIF, true),
        dailyStepGoal = prefs.getInt(K_GOAL, 8000),
        fitnessLevel = prefs.getString(K_LEVEL, "beginner") ?: "beginner",
        age = prefs.getInt(K_AGE, -1).takeIf { it > 0 },
        lastSyncTime = prefs.getLong(K_LAST_SYNC, 0L)
    )

    private companion object {
        const val K_DARK = "dark_mode"
        const val K_NOTIF = "notifications"
        const val K_GOAL = "step_goal"
        const val K_LEVEL = "fitness_level"
        const val K_AGE = "age"
        const val K_LAST_SYNC = "last_sync"
        const val K_GOAL_NOTIFIED = "goal_notified_date"
    }
}
