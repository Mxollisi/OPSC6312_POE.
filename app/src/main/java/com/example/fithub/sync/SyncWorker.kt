package com.example.fithub.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fithub.ServiceLocator
import com.example.fithub.notifications.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * Background sync for Offline Mode. WorkManager only runs this when the device has a
 * network connection, and retries automatically (with back-off) if an upload fails.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Uploading needs a real Firebase login. After an offline-only login, unsynced
        // workouts wait safely in SQLite until the user logs in online again.
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        return try {
            val uploaded = ServiceLocator.workouts.syncPending(user.uid)
            ServiceLocator.settings.update { it.copy(lastSyncTime = System.currentTimeMillis()) }
            if (uploaded > 0) NotificationHelper.showSyncComplete(applicationContext, uploaded)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

object SyncScheduler {
    private const val NOW = "fithub_sync_now"
    private const val PERIODIC = "fithub_sync_periodic"

    private val onlineOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Sync as soon as there is a connection (immediately if already online). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineOnly)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Safety net: also sync every 15 minutes while online. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(onlineOnly)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
