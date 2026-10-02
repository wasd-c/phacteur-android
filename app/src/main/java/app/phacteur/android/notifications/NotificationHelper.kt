package app.phacteur.android.notifications

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
import app.phacteur.android.R
import app.phacteur.android.data.NotificationCursorItem

object NotificationHelper {
    const val CHANNEL_ID = "new-email"
    const val EXTRA_EMAIL_ID = "emailId"
    const val EXTRA_THREAD_ID = "threadId"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canShowNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @Synchronized
    fun showNewEmail(
        context: Context,
        emailId: Int,
        threadId: String?,
        expectedGeneration: String = NotificationPreferences(context).generation,
    ): Boolean {
        val preferences = NotificationPreferences(context)
        if (!PushRegistrationManager.isCurrentSession(context, expectedGeneration) || emailId <= 0) return false
        if (preferences.hasSeen(emailId)) return false
        if (!canShowNotifications(context)) return false

        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            putExtra(EXTRA_EMAIL_ID, emailId)
            putExtra(EXTRA_GENERATION, expectedGeneration)
            threadId?.let { putExtra(EXTRA_THREAD_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            emailId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle("Nouvel email")
            .setContentText("Ouvrez Phacteur pour consulter le message.")
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setGroup("phacteur-email")
            .build()
        try {
            NotificationManagerCompat.from(context).notify(emailId, notification)
        } catch (_: SecurityException) {
            // The permission may have changed since the check above. Keep the cursor
            // so the catch-up worker can deliver this message after permission returns.
            return false
        }
        preferences.markSeen(emailId)
        return true
    }

    @Synchronized
    fun clearNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Synchronized
    fun initializeCursor(context: Context, generation: String, latestEmailId: Int) {
        if (!PushRegistrationManager.isCurrentSession(context, generation)) return
        NotificationPreferences(context).apply {
            latestNotifiedEmailId = maxOf(latestNotifiedEmailId, latestEmailId)
            cursorInitialized = true
        }
    }

    @Synchronized
    fun completeSyncPage(
        context: Context,
        generation: String,
        emails: List<NotificationCursorItem>,
        nextAfterId: Int,
    ): Boolean {
        if (!PushRegistrationManager.isCurrentSession(context, generation)) return false
        val preferences = NotificationPreferences(context)
        val newest = emails.filterNot { preferences.hasSeen(it.id) }.maxByOrNull { it.id }
        if (newest != null && !showNewEmail(context, newest.id, newest.threadId, generation)) return false
        // The scan cursor is independent of FCM arrival order. Only a completed
        // authenticated page may advance it, preserving catch-up for lost pushes.
        emails.forEach { preferences.markSeen(it.id) }
        preferences.latestNotifiedEmailId = maxOf(preferences.latestNotifiedEmailId, nextAfterId)
        return true
    }

    const val EXTRA_GENERATION = "notificationGeneration"
}
