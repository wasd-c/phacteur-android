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
        if (!NotificationPreferences(applicationContext).enabled) return
        if (message.data["type"] != "new_email") return
        val emailId = message.data["emailId"]?.toIntOrNull()?.takeIf { it > 0 } ?: return
        NotificationHelper.showNewEmail(
            context = applicationContext,
            emailId = emailId,
            threadId = message.data["threadId"]?.takeIf(String::isNotBlank),
        )
    }
}
