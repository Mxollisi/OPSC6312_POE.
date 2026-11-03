package com.example.fithub

import android.content.Context
import com.example.fithub.data.local.FitHubDatabase
import com.example.fithub.data.remote.ApiClients
import com.example.fithub.data.repository.ExerciseRepository
import com.example.fithub.data.repository.LocalCredentialStore
import com.example.fithub.data.repository.NetworkMonitor
import com.example.fithub.data.repository.SessionManager
import com.example.fithub.data.repository.SettingsRepository
import com.example.fithub.data.repository.WorkoutRepository
import com.google.firebase.firestore.FirebaseFirestore

/** Simple dependency container, initialised once in [FitHubApp.onCreate]. */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database: FitHubDatabase by lazy { FitHubDatabase.get(appContext) }
    val session: SessionManager by lazy { SessionManager(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val network: NetworkMonitor by lazy { NetworkMonitor(appContext) }
    val credentials: LocalCredentialStore by lazy { LocalCredentialStore(database) }
    val workouts: WorkoutRepository by lazy {
        WorkoutRepository(database) { FirebaseFirestore.getInstance() }
    }
    val exercises: ExerciseRepository by lazy { ExerciseRepository(database, ApiClients.wger) }
}
