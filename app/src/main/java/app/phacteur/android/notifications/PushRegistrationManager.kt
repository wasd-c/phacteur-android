package app.phacteur.android.notifications

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.phacteur.android.BuildConfig
import app.phacteur.android.data.AppGraph
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object PushRegistrationManager {
    private const val FID_KEY = "firebase_installation_id"
    private const val REGISTER_WORK = "phacteur-register-push"
    private const val FIREBASE_REGISTER_WORK = "phacteur-firebase-register"
    private const val INBOX_BASELINE_WORK = "phacteur-inbox-baseline"
    private const val INBOX_SYNC_WORK = "phacteur-inbox-sync"

    fun isFirebaseConfigured(context: Context): Boolean = firebaseApp(context) != null

    fun enable(context: Context) {
        val preferences = NotificationPreferences(context)
        val session = sessionFingerprint(context) ?: return
        synchronized(NotificationHelper) {
            if (!preferences.enabled || preferences.boundSession != session) {
                preferences.invalidateSession()
                preferences.resetCursor()
                preferences.boundSession = session
                NotificationHelper.clearNotifications(context)
                NotificationLaunchStore.clear()
            }
            preferences.enabled = true
        }
        // Background catch-up must also work in builds without Firebase resources.
        scheduleInboxSync(context)
        enqueueInboxSync(context)
        if (firebaseApp(context) != null) {
            runCatching {
                FirebaseMessaging.getInstance().apply {
                    isAutoInitEnabled = true
                }
            }
            enqueueFirebaseRegistration(context)
            lastKnownFid(context)?.let { enqueueRegistration(context, it) }
        }
    }

    suspend fun disable(context: Context) {
        val graph = AppGraph.from(context)
        val fid = lastKnownFid(context)
        synchronized(NotificationHelper) {
            NotificationPreferences(context).apply {
                enabled = false
                invalidateSession()
                resetCursor()
            }
            NotificationHelper.clearNotifications(context)
            NotificationLaunchStore.clear()
        }
        WorkManager.getInstance(context).cancelUniqueWork(REGISTER_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(FIREBASE_REGISTER_WORK)
        WorkManager.getInstance(context).cancelAllWorkByTag(NewEmailNotificationWorker.WORK_TAG)
        WorkManager.getInstance(context).cancelUniqueWork(INBOX_BASELINE_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(INBOX_SYNC_WORK)
        firebaseApp(context)?.let {
            runCatching {
                FirebaseMessaging.getInstance().apply {
                    isAutoInitEnabled = false
                    unregister()
                }
            }
        }
        graph.secureStorage.remove(FID_KEY)
        if (fid != null && graph.api.hasSession()) {
            runCatching { graph.api.deletePushInstallation(fid) }
        }
    }

    fun refreshAfterLogin(context: Context) {
        if (!NotificationPreferences(context).enabled) return
        enable(context)
    }

    fun onRegistered(context: Context, fid: String) {
        if (!isCurrentSession(context, NotificationPreferences(context).generation)) return
        AppGraph.from(context).secureStorage.putString(FID_KEY, fid)
        enqueueRegistration(context, fid)
    }

    fun onUnregistered(context: Context, fid: String) {
        if (lastKnownFid(context) == fid) AppGraph.from(context).secureStorage.remove(FID_KEY)
    }

    private fun lastKnownFid(context: Context): String? =
        AppGraph.from(context).secureStorage.getString(FID_KEY)

    private fun enqueueRegistration(context: Context, fid: String) {
        val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(Data.Builder()
                .putString(PushRegistrationWorker.FID_INPUT, fid)
                .putString(PushRegistrationWorker.GENERATION_INPUT, NotificationPreferences(context).generation)
                .build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REGISTER_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun enqueueFirebaseRegistration(context: Context) {
        val request = OneTimeWorkRequestBuilder<FirebaseRegistrationWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(Data.Builder()
                .putString(PushRegistrationWorker.GENERATION_INPUT, NotificationPreferences(context).generation)
                .build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FIREBASE_REGISTER_WORK, ExistingWorkPolicy.KEEP, request,
        )
    }

    internal fun isCurrentSession(context: Context, generation: String): Boolean {
        val preferences = NotificationPreferences(context)
        return generation.isNotBlank() && preferences.enabled && preferences.generation == generation &&
            preferences.boundSession != null && preferences.boundSession == sessionFingerprint(context)
    }

    private fun sessionFingerprint(context: Context): String? {
        val cookie = AppGraph.from(context).cookieStore.cookieHeader()
            ?.split(';')?.map(String::trim)?.firstOrNull { it.startsWith("user-session=") }
            ?: return null
        return MessageDigest.getInstance("SHA-256").digest(cookie.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun scheduleInboxSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<InboxSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            INBOX_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun enqueueInboxSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<InboxSyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            INBOX_BASELINE_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun firebaseApp(context: Context): FirebaseApp? = runCatching {
        FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }.getOrNull()

    internal fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .take(100)

    internal fun appVersion(): String = BuildConfig.VERSION_NAME
}
