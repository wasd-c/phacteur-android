package app.phacteur.android.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.phacteur.android.R

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

    fun showNewEmail(context: Context, emailId: Int, threadId: String?) {
        if (!NotificationPreferences(context).enabled) return
        NotificationPreferences(context).markSeen(emailId)
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            putExtra(EXTRA_EMAIL_ID, emailId)
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
            .setContentIntent(pendingIntent)
            .setGroup("phacteur-email")
            .build()
        NotificationManagerCompat.from(context).notify(emailId, notification)
    }
}
