package app.phacteur.android.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.phacteur.android.data.ApiException
import app.phacteur.android.data.AppGraph
import java.io.IOException

class InboxSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val preferences = NotificationPreferences(applicationContext)
        val generation = preferences.generation
        val api = AppGraph.from(applicationContext).api
        if (!preferences.enabled) return Result.success()
        if (!api.hasSession()) {
            return Result.success()
        }
        if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) return Result.success()

        return try {
            val previous = preferences.latestNotifiedEmailId
            if (!preferences.cursorInitialized) {
                val baseline = api.notificationCursor(afterId = 0, limit = 1)
                if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) return Result.success()
                NotificationHelper.initializeCursor(applicationContext, generation, maxOf(previous, baseline.latestEmailId))
                return Result.success()
            }

            var cursor = previous
            do {
                val page = api.notificationCursor(afterId = cursor)
                val nextCursor = page.nextAfterId
                if (page.hasMore && nextCursor <= cursor) throw IOException("Curseur de notification invalide")
                if (!NotificationHelper.completeSyncPage(applicationContext, generation, page.emails, nextCursor)) {
                    return Result.success()
                }
                cursor = nextCursor
            } while (page.hasMore)
            Result.success()
        } catch (error: ApiException) {
            if (error.statusCode == 401) {
                if (PushRegistrationManager.isCurrentSession(applicationContext, generation)) {
                    PushRegistrationManager.disable(applicationContext)
                }
                Result.success()
            } else if (error.statusCode in 400..499 && !isRetryableNotificationStatus(error.statusCode)) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }
}
