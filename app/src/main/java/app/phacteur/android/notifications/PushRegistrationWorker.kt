package app.phacteur.android.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.phacteur.android.data.ApiException
import app.phacteur.android.data.AppGraph
import java.io.IOException

class PushRegistrationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fid = inputData.getString(FID_INPUT) ?: return Result.failure()
        val generation = inputData.getString(GENERATION_INPUT).orEmpty()
        val api = AppGraph.from(applicationContext).api
        if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) {
            return Result.success()
        }
        if (!api.hasSession()) {
            return Result.success()
        }
        return try {
            api.registerPushInstallation(
                fid = fid,
                deviceName = PushRegistrationManager.deviceName(),
                appVersion = PushRegistrationManager.appVersion(),
            )
            Result.success()
        } catch (error: ApiException) {
            if (error.statusCode == 401) {
                if (PushRegistrationManager.isCurrentSession(applicationContext, generation)) {
                    PushRegistrationManager.disable(applicationContext)
                }
                Result.success()
            } else if (isRetryableNotificationStatus(error.statusCode)) {
                Result.retry()
            } else if (error.statusCode in 400..499) {
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        const val FID_INPUT = "fid"
        const val GENERATION_INPUT = "generation"
    }
}
