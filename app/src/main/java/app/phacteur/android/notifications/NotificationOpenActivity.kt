package app.phacteur.android.notifications

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.phacteur.android.MainActivity
import java.util.concurrent.atomic.AtomicReference

internal data class NotificationTarget(val emailId: Int?, val threadId: String?)

internal object NotificationLaunchStore {
    private val pending = AtomicReference<NotificationTarget?>(null)

    fun publish(emailId: Int?, threadId: String?) {
        pending.set(NotificationTarget(emailId, threadId))
    }

    fun consume(): NotificationTarget? = pending.getAndSet(null)

    fun clear() { pending.set(null) }
}

/**
 * Non-exported trampoline used only by this app's immutable notification
 * PendingIntent. MainActivity stays exported for the launcher and PKCE callback
 * without trusting caller-controlled email extras.
 */
class NotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val generation = intent.getStringExtra(NotificationHelper.EXTRA_GENERATION).orEmpty()
        if (!PushRegistrationManager.isCurrentSession(this, generation)) {
            finish()
            return
        }
        val emailId = intent.getIntExtra(NotificationHelper.EXTRA_EMAIL_ID, -1).takeIf { it > 0 }
        val threadId = intent.getStringExtra(NotificationHelper.EXTRA_THREAD_ID)
            ?.takeIf { it.isNotBlank() && it.length <= 512 }
        if (emailId != null || threadId != null) {
            NotificationLaunchStore.publish(emailId, threadId)
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }
}
