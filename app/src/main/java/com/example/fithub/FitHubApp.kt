package com.example.fithub

import android.app.Application
import com.example.fithub.notifications.NotificationHelper
import com.example.fithub.notifications.PushManager
import com.example.fithub.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class FitHubApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createChannels(this)

        if (ServiceLocator.session.current != null) {
            SyncScheduler.schedulePeriodic(this)
            if (FirebaseAuth.getInstance().currentUser != null) {
                PushManager.register(ServiceLocator.settings.current.notificationsEnabled)
            }
        }

        // Whenever the connection comes back, upload anything saved while offline.
        appScope.launch {
            ServiceLocator.network.isOnline
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    if (ServiceLocator.session.current != null) {
                        SyncScheduler.syncNow(this@FitHubApp)
                    }
                }
        }
    }
}
