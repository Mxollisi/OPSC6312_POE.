package com.example.fithub.notifications

import com.example.fithub.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives real-time push notifications from Firebase Cloud Messaging.
 * Messages can be sent from Firebase console → Messaging, either to a single
 * device token or to the "fithub_updates" topic every user is subscribed to.
 */
class FitHubMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushManager.saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return
        NotificationHelper.showPush(this, title, body)
    }
}
