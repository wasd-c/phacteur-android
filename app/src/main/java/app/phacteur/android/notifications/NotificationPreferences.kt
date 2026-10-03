package app.phacteur.android.notifications

import android.content.Context
import java.util.UUID

class NotificationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("phacteur_notifications", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) {
            preferences.edit().putBoolean("enabled", value).apply {
                if (!value) remove(BADGE_OWNER).remove(BADGE_COUNT).remove(BADGE_EMAIL_IDS).remove(BADGE_ARRIVAL_IDS)
            }.apply()
        }

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
            .remove(BADGE_OWNER)
            .remove(BADGE_COUNT)
            .remove(BADGE_EMAIL_IDS)
            .remove(BADGE_ARRIVAL_IDS)
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
            .remove(BADGE_OWNER)
            .remove(BADGE_COUNT)
            .remove(BADGE_EMAIL_IDS)
            .remove(BADGE_ARRIVAL_IDS)
            .apply()
    }

    internal fun badgeState(owner: String): EmailBadgeState {
        val openedAt = if (preferences.contains(LAST_OPENED_AT)) preferences.getLong(LAST_OPENED_AT, 0) else {
            // An upgraded installation has no reliable previous-open timestamp.
            // Start now instead of counting an old catch-up queue as new mail.
            System.currentTimeMillis().also { preferences.edit().putLong(LAST_OPENED_AT, it).commit() }
        }
        if (preferences.getString(BADGE_OWNER, null) != owner) return EmailBadgeState(owner, openedAt)
        val unreadIds = preferences.getString(BADGE_EMAIL_IDS, "").orEmpty().split(',')
            .mapNotNull(String::toIntOrNull).filter { it > 0 }.toSet()
        return EmailBadgeState(
            owner = owner,
            lastOpenedAtMillis = openedAt,
            count = preferences.getInt(BADGE_COUNT, 0).coerceAtLeast(0),
            emailIds = unreadIds,
            arrivalIds = preferences.getString(BADGE_ARRIVAL_IDS, "").orEmpty().split(',')
                .mapNotNull(String::toIntOrNull).filter { it > 0 }.toSet() + unreadIds,
        )
    }

    internal fun saveBadgeState(state: EmailBadgeState, newlySeenIds: Collection<Int> = emptyList()) {
        val seen = preferences.getString("seen_email_ids", "").orEmpty().split(',')
            .mapNotNull(String::toIntOrNull).filter { it > 0 && it !in newlySeenIds }
        // Keep all badge IDs since the latest opening, while the older delivery
        // ledger remains bounded. A delayed FCM duplicate must not grow the count.
        preferences.edit()
            .putString(BADGE_OWNER, state.owner)
            .putInt(BADGE_COUNT, state.count)
            .putString(BADGE_EMAIL_IDS, state.emailIds.sorted().joinToString(","))
            .putString(BADGE_ARRIVAL_IDS, state.arrivalIds.sorted().joinToString(","))
            .putLong(LAST_OPENED_AT, state.lastOpenedAtMillis)
            .putString("seen_email_ids", (seen + newlySeenIds.filter { it > 0 }.distinct()).takeLast(512).joinToString(","))
            .commit()
    }

    internal fun markAppOpened(atMillis: Long) {
        saveBadgeState(badgeState(generation).opened(atMillis))
    }

    private companion object {
        const val BADGE_OWNER = "badge_generation"
        const val BADGE_COUNT = "badge_count"
        const val BADGE_EMAIL_IDS = "badge_email_ids"
        const val BADGE_ARRIVAL_IDS = "badge_arrival_ids"
        const val LAST_OPENED_AT = "app_last_opened_at_ms"
    }
}
