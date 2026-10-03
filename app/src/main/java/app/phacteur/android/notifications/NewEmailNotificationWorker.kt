package app.phacteur.android.notifications

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.phacteur.android.data.ApiException
import app.phacteur.android.data.AppGraph
import java.io.IOException
import java.util.concurrent.TimeUnit

/** FCM is a wake-up signal; the current authenticated API confirms ownership. */
class NewEmailNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val emailId = inputData.getInt(EMAIL_ID_INPUT, -1)
        val generation = inputData.getString(PushRegistrationWorker.GENERATION_INPUT).orEmpty()
        if (emailId <= 0) return Result.failure()
        if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) return Result.success()
        if (NotificationPreferences(applicationContext).hasSeen(emailId)) return Result.success()
        return try {
            val email = AppGraph.from(applicationContext).api.notificationEmail(emailId)
            if (email.id != emailId) return Result.failure()
            NotificationHelper.showNewEmail(
                applicationContext, email.id, email.threadId, generation,
                receivedAt = email.receivedAt,
            )
            Result.success()
        } catch (error: ApiException) {
            when {
                error.statusCode == 401 -> {
                    if (PushRegistrationManager.isCurrentSession(applicationContext, generation)) {
                        PushRegistrationManager.disable(applicationContext)
                    }
                    Result.success()
                }
                isRetryableNotificationStatus(error.statusCode) -> Result.retry()
                else -> Result.success()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_TAG = "phacteur-new-email-notification"
        private const val EMAIL_ID_INPUT = "emailId"

        fun enqueue(context: Context, emailId: Int, generation: String) {
            val request = OneTimeWorkRequestBuilder<NewEmailNotificationWorker>()
                .addTag(WORK_TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply {
                    // Earlier Android versions require a foreground service for
                    // expedited work; use ordinary work there to avoid an extra
                    // service notification for a metadata ownership check.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(Data.Builder()
                    .putInt(EMAIL_ID_INPUT, emailId)
                    .putString(PushRegistrationWorker.GENERATION_INPUT, generation)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$WORK_TAG-$generation-$emailId", ExistingWorkPolicy.KEEP, request,
            )
        }
    }
}
