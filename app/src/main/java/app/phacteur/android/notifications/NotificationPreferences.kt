package app.phacteur.android.notifications

import android.content.Context

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

    fun markSeen(emailId: Int) {
        if (emailId > latestNotifiedEmailId) latestNotifiedEmailId = emailId
    }

    fun resetCursor() {
        preferences.edit()
            .remove("latest_email_id")
            .remove("cursor_initialized")
            .apply()
    }
}
