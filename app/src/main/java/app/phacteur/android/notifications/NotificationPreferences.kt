package app.phacteur.android.notifications

import android.content.Context
import java.util.UUID

class NotificationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("phacteur_notifications", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) = preferences.edit().putBoolean("enabled", value).apply()

    var promptHandled: Boolean
        get() = preferences.getBoolean("prompt_handled", false)
        set(value) = preferences.edit().putBoolean("prompt_handled", value).apply()

    var latestNotifiedEmailId: Int
        get() = preferences.getInt("latest_email_id", 0)
        set(value) = preferences.edit().putInt("latest_email_id", value).apply()

    var cursorInitialized: Boolean
        get() = preferences.getBoolean("cursor_initialized", false)
        set(value) = preferences.edit().putBoolean("cursor_initialized", value).apply()

    val generation: String
        get() = preferences.getString("generation", "").orEmpty()

    var boundSession: String?
        get() = preferences.getString("bound_session", null)
        set(value) = preferences.edit().putString("bound_session", value).apply()

    fun invalidateSession() {
        preferences.edit()
            .putString("generation", UUID.randomUUID().toString())
            .remove("bound_session")
            .apply()
    }

    fun hasSeen(emailId: Int): Boolean = preferences.getString("seen_email_ids", "")
        .orEmpty().split(',').any { it.toIntOrNull() == emailId }

    fun markSeen(emailId: Int) {
        val seen = preferences.getString("seen_email_ids", "").orEmpty()
            .split(',').mapNotNull(String::toIntOrNull).filter { it != emailId }
        preferences.edit()
            .putString("seen_email_ids", (seen + emailId).takeLast(512).joinToString(","))
            .apply()
    }

    fun resetCursor() {
        preferences.edit()
            .remove("latest_email_id")
            .remove("cursor_initialized")
            .remove("seen_email_ids")
            .apply()
    }
}
