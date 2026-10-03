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
    private const val EMAIL_GROUP = "phacteur-email"
    private const val BADGE_SUMMARY_TAG = "phacteur-email-badge"
    private const val BADGE_SUMMARY_ID = 1
    // Process death must leave background delivery enabled. The activity lifecycle
    // updates this flag only while the process can actually display the app.
    private var appInForeground = false

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
        receivedAt: String,
    ): Boolean {
        if (!PushRegistrationManager.isCurrentSession(context, expectedGeneration) || emailId <= 0) return false
        return deliverEmails(context, expectedGeneration, listOf(NotificationCursorItem(emailId, threadId, receivedAt)))
    }

    private fun deliverEmails(context: Context, generation: String, emails: List<NotificationCursorItem>): Boolean {
        val preferences = NotificationPreferences(context)
        val unseen = emails.filter { it.id > 0 && !preferences.hasSeen(it.id) }.distinctBy { it.id }
        if (unseen.isEmpty()) return true
        val current = preferences.badgeState(generation)
        val updated = current.record(
            unseen.map { BadgeEmail(generation, it.id, badgeReceivedAtMillis(it.receivedAt)) },
            enabled = preferences.enabled,
            foreground = appInForeground,
        )
        val addedIds = updated.emailIds - current.emailIds
        if (addedIds.isEmpty()) {
            // These messages predate this app opening or were already counted.
            // Do not revive dismissed alerts or include old unread mail in the badge.
            preferences.saveBadgeState(updated, unseen.map { it.id })
            return true
        }
        if (!canShowNotifications(context)) {
            preferences.saveBadgeState(current.copy(count = 0, emailIds = emptySet()))
            cancelEmailNotifications(context)
            return false
        }
        val newest = unseen.filter { it.id in addedIds }.maxBy { it.id }
        val notification = newEmailNotification(context, newest.id, newest.threadId, generation)
        try {
            NotificationManagerCompat.from(context).notify(newest.id, notification)
        } catch (_: SecurityException) {
            // Keep both ledgers and the scan cursor unchanged if permission changed.
            return false
        }
        preferences.saveBadgeState(updated, unseen.map { it.id })
        updateBadgeSummary(context, updated)
        return true
    }

    private fun emailPendingIntent(context: Context, emailId: Int, threadId: String?, generation: String,
        summary: Boolean = false): PendingIntent {
        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            putExtra(EXTRA_EMAIL_ID, emailId)
            putExtra(EXTRA_GENERATION, generation)
            threadId?.let { putExtra(EXTRA_THREAD_ID, it) }
            if (summary) action = "${context.packageName}.OPEN_EMAIL_SUMMARY"
        }
        return PendingIntent.getActivity(
            context,
            emailId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun newEmailNotification(context: Context, emailId: Int, threadId: String?, generation: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle("Nouvel email")
            .setContentText("Ouvrez Phacteur pour consulter le message.")
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(emailPendingIntent(context, emailId, threadId, generation))
            .setGroup(EMAIL_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setNumber(1)
            .build()

    private fun updateBadgeSummary(context: Context, state: EmailBadgeState) {
        val manager = NotificationManagerCompat.from(context)
        val newestId = state.emailIds.maxOrNull()
        if (state.count <= 0 || newestId == null) {
            manager.cancel(BADGE_SUMMARY_TAG, BADGE_SUMMARY_ID)
            return
        }
        if (!canShowNotifications(context)) return
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_color))
            .setContentTitle("Nouveaux emails")
            .setContentText("${state.count} message${if (state.count > 1) "s" else ""} non lu${if (state.count > 1) "s" else ""} depuis votre dernière ouverture.")
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(emailPendingIntent(context, newestId, null, state.owner, summary = true))
            .setGroup(EMAIL_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setNumber(state.count)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(BADGE_SUMMARY_TAG, BADGE_SUMMARY_ID, summary)
        } catch (_: SecurityException) {
            // The individual alert was already accepted. Do not replay it merely
            // because Android stopped accepting the optional summary badge.
        }
    }

    @Synchronized
    fun clearNotifications(context: Context) {
        cancelEmailNotifications(context)
        val preferences = NotificationPreferences(context)
        preferences.saveBadgeState(preferences.badgeState(preferences.generation).copy(count = 0, emailIds = emptySet()))
    }

    /** Reset the "since opening" counter while retaining delivery deduplication and the scan cursor. */
    @Synchronized
    fun onAppForeground(context: Context) {
        if (appInForeground) return
        appInForeground = true
        NotificationPreferences(context).markAppOpened(System.currentTimeMillis())
        cancelEmailNotifications(context)
    }

    @Synchronized
    fun onAppBackground(@Suppress("UNUSED_PARAMETER") context: Context) {
        appInForeground = false
    }

    /** Called after an owned email mutation succeeds; UNREAD does not create a new arrival. */
    @Synchronized
    fun onEmailStatusesChanged(
        context: Context,
        emailIds: Collection<Int>,
        status: String,
        expectedGeneration: String = NotificationPreferences(context).generation,
    ) {
        if (!PushRegistrationManager.isCurrentSession(context, expectedGeneration)) return
        val preferences = NotificationPreferences(context)
        val current = preferences.badgeState(expectedGeneration)
        val updated = current.statusChanged(emailIds, status)
        if (updated == current) return
        preferences.saveBadgeState(updated)
        cancelEmailNotifications(context, emailIds.toSet())
        updateBadgeSummary(context, updated)
    }

    @Synchronized
    fun badgeCount(context: Context): Int {
        val preferences = NotificationPreferences(context)
        if (!PushRegistrationManager.isCurrentSession(context, preferences.generation) ||
            !canShowNotifications(context)) return 0
        return preferences.badgeState(preferences.generation).count
    }

    private fun cancelEmailNotifications(context: Context, emailIds: Set<Int>? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { active ->
            (active.notification.channelId == CHANNEL_ID || active.notification.group == EMAIL_GROUP) &&
                (emailIds == null || active.tag != BADGE_SUMMARY_TAG && active.id in emailIds)
        }.forEach { active -> manager.cancel(active.tag, active.id) }
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
        if (!deliverEmails(context, generation, emails)) return false
        // The scan cursor is independent of FCM arrival order. Only a completed
        // authenticated page may advance it, preserving catch-up for lost pushes.
        preferences.latestNotifiedEmailId = maxOf(preferences.latestNotifiedEmailId, nextAfterId)
        return true
    }

    const val EXTRA_GENERATION = "notificationGeneration"
}
