package app.phacteur.android.notifications

internal fun newEmailId(data: Map<String, String>): Int? {
    if (data["type"] != "new_email") return null
    val raw = data["emailId"] ?: return null
    if (!raw.matches(Regex("[0-9]{1,10}"))) return null
    return raw.toIntOrNull()?.takeIf { it > 0 }
}

internal fun isRetryableNotificationStatus(status: Int): Boolean =
    status == 408 || status == 429 || status in 500..599
