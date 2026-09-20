package app.phacteur.android.notifications

import android.content.Context
import android.os.Build
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
import java.util.concurrent.TimeUnit

object PushRegistrationManager {
    private const val FID_KEY = "firebase_installation_id"
    private const val REGISTER_WORK = "phacteur-register-push"
    private const val INBOX_BASELINE_WORK = "phacteur-inbox-baseline"
    private const val INBOX_SYNC_WORK = "phacteur-inbox-sync"

    fun isFirebaseConfigured(context: Context): Boolean = firebaseApp(context) != null

    fun enable(context: Context) {
        NotificationPreferences(context).enabled = true
        // Background catch-up must also work in builds without Firebase resources.
        scheduleInboxSync(context)
        enqueueInboxSync(context)
        if (firebaseApp(context) != null) {
            runCatching {
                FirebaseMessaging.getInstance().apply {
                    isAutoInitEnabled = true
                    register()
                }
            }
            lastKnownFid(context)?.let { enqueueRegistration(context, it) }
        }
    }

    suspend fun disable(context: Context) {
        val graph = AppGraph.from(context)
        val fid = lastKnownFid(context)
        NotificationPreferences(context).apply {
            enabled = false
            resetCursor()
        }
        WorkManager.getInstance(context).cancelUniqueWork(REGISTER_WORK)
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
        if (!NotificationPreferences(context).enabled) return
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
            .setInputData(Data.Builder().putString(PushRegistrationWorker.FID_INPUT, fid).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REGISTER_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
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
