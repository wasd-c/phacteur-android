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
        val api = AppGraph.from(applicationContext).api
        if (!NotificationPreferences(applicationContext).enabled) {
            return Result.success()
        }
        if (!api.hasSession()) {
            PushRegistrationManager.disable(applicationContext)
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
                PushRegistrationManager.disable(applicationContext)
                Result.success()
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
    }
}
