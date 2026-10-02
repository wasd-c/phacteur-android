package app.phacteur.android.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Also retries SDK registration when no FID was available during an offline opt-in. */
class FirebaseRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val generation = inputData.getString(PushRegistrationWorker.GENERATION_INPUT).orEmpty()
        if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) return Result.success()
        if (!PushRegistrationManager.isFirebaseConfigured(applicationContext)) return Result.success()
        val registered = suspendCancellableCoroutine { continuation ->
            try {
                FirebaseMessaging.getInstance().register().addOnCompleteListener { task ->
                    if (continuation.isActive) continuation.resume(task.isSuccessful)
                }
            } catch (_: IllegalStateException) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
        return if (registered) Result.success() else Result.retry()
    }
}
