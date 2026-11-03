package com.example.fithub.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fithub.MainActivity
import com.example.fithub.R
import com.example.fithub.ServiceLocator

/** Creates channels and shows all FitHub notifications (local alerts + push messages). */
object NotificationHelper {

    const val CHANNEL_ALERTS = "fithub_alerts"
    const val CHANNEL_PUSH = "fithub_push"

    private const val ID_WORKOUT_SAVED = 1001
    private const val ID_SYNC = 1002
    private const val ID_GOAL = 1003

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_alerts_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PUSH,
                context.getString(R.string.channel_push_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.channel_push_desc) }
        )
    }

    fun canNotify(context: Context): Boolean {
        val enabledInApp = try {
            ServiceLocator.settings.current.notificationsEnabled
        } catch (e: Exception) {
            true
        }
        if (!enabledInApp) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showWorkoutSaved(context: Context, points: Int) = show(
        context, ID_WORKOUT_SAVED, CHANNEL_ALERTS,
        context.getString(R.string.notif_workout_saved_title),
        context.getString(R.string.notif_workout_saved_body, points)
    )

    fun showSyncComplete(context: Context, count: Int) = show(
        context, ID_SYNC, CHANNEL_ALERTS,
        context.getString(R.string.notif_sync_title),
        context.resources.getQuantityString(R.plurals.notif_sync_body, count, count)
    )

    fun showGoalReached(context: Context, goal: Int) = show(
        context, ID_GOAL, CHANNEL_ALERTS,
        context.getString(R.string.notif_goal_title),
        context.getString(R.string.notif_goal_body, goal)
    )

    fun showPush(context: Context, title: String, body: String) = show(
        context, (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), CHANNEL_PUSH, title, body
    )

    private fun show(context: Context, id: Int, channel: String, title: String, body: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_fithub)
            .setColor(ContextCompat.getColor(context, R.color.fithub_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked while running — ignore rather than crash.
        }
    }
}
