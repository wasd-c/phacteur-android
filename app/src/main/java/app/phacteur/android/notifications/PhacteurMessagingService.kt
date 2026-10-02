package app.phacteur.android.notifications

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PhacteurMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        PushRegistrationManager.onRegistered(applicationContext, installationId)
    }

    override fun onUnregistered(installationId: String) {
        PushRegistrationManager.onUnregistered(applicationContext, installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val emailId = newEmailId(message.data) ?: return
        val generation = NotificationPreferences(applicationContext).generation
        if (!PushRegistrationManager.isCurrentSession(applicationContext, generation)) return
        NewEmailNotificationWorker.enqueue(applicationContext, emailId, generation)
    }
}
