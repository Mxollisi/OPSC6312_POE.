package com.example.fithub.notifications

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/** Registers this device for push notifications and stores its FCM token in Firestore. */
object PushManager {

    const val TOPIC_UPDATES = "fithub_updates"

    fun register(enabled: Boolean) {
        try {
            val messaging = FirebaseMessaging.getInstance()
            if (enabled) {
                messaging.subscribeToTopic(TOPIC_UPDATES)
                messaging.token.addOnSuccessListener { token -> saveToken(token) }
            } else {
                messaging.unsubscribeFromTopic(TOPIC_UPDATES)
            }
        } catch (e: Exception) {
            // Google Play services missing on this device — push simply won't be available.
        }
    }

    fun saveToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(
                    hashMapOf(
                        "fcmToken" to token,
                        "fcmTokenUpdatedAt" to Timestamp.now()
                    ),
                    SetOptions.merge()
                )
        } catch (e: Exception) {
            // Ignore: the token is re-sent on next launch.
        }
    }
}
