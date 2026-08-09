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
        val api = AppGraph.from(applicationContext).api
        if (!preferences.enabled) return Result.success()
        if (!api.hasSession()) {
            PushRegistrationManager.disable(applicationContext)
            return Result.success()
        }

        return try {
            val previous = preferences.latestNotifiedEmailId
            if (!preferences.cursorInitialized) {
                val baseline = api.notificationCursor(afterId = 0, limit = 1)
                preferences.latestNotifiedEmailId = maxOf(previous, baseline.latestEmailId)
                preferences.cursorInitialized = true
                return Result.success()
            }

            var cursor = previous
            var newest: app.phacteur.android.data.NotificationCursorItem? = null
            do {
                val page = api.notificationCursor(afterId = cursor)
                page.emails.maxByOrNull { it.id }?.let { item ->
                    if (newest == null || item.id > newest.id) newest = item
                }
                val nextCursor = page.nextAfterId
                if (page.hasMore && nextCursor <= cursor) throw IOException("Curseur de notification invalide")
                cursor = nextCursor
            } while (page.hasMore)

            if (newest != null) {
                NotificationHelper.showNewEmail(applicationContext, newest.id, newest.threadId)
            }
            Result.success()
        } catch (error: ApiException) {
            if (error.statusCode == 401) {
                PushRegistrationManager.disable(applicationContext)
                Result.success()
            } else if (error.statusCode == 404) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: IOException) {
            Result.retry()
        }
    }
}
