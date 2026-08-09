package app.phacteur.android.data

import org.json.JSONArray
import org.json.JSONObject

data class User(
    val id: Int,
    val username: String,
    val email: String,
    val name: String?,
    val pseudonym: String?,
    val avatarUrl: String?,
    val isAdmin: Boolean,
    val twoFactorEnabled: Boolean,
) {
    val displayName: String get() = name?.takeIf(String::isNotBlank) ?: pseudonym?.takeIf(String::isNotBlank) ?: username
}

data class EmailAttachment(
    val id: Int,
    val filename: String,
    val mimeType: String,
    val fileSize: Long,
)

data class MailboxEmail(
    val id: Int,
    val sender: String,
    val senderName: String?,
    val subject: String,
    val body: String,
    val emailAccountId: Int?,
    val receivedAt: String,
    val status: String,
    val hasAttachments: Boolean,
    val threadId: String?,
    val isStarred: Boolean,
    val attachments: List<EmailAttachment>,
) {
    val displaySender: String get() = senderName?.takeIf(String::isNotBlank) ?: sender
}

data class MailboxPage(
    val emails: List<MailboxEmail>,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean,
)

data class MailThread(
    val id: String,
    val subject: String,
    val snippet: String,
    val participants: List<String>,
    val lastMessageAt: String,
    val messageCount: Int,
    val hasUnread: Boolean,
    val isStarred: Boolean,
    val isMuted: Boolean,
)

data class ThreadMessage(
    val id: Int,
    val sender: String,
    val senderName: String?,
    val subject: String,
    val body: String,
    val emailAccountId: Int?,
    val receivedAt: String,
    val status: String,
) {
    val displaySender: String get() = senderName?.takeIf(String::isNotBlank) ?: sender
}

data class Contact(
    val id: Int,
    val email: String?,
    val name: String?,
    val displayName: String?,
    val company: String?,
    val jobTitle: String?,
    val phone: String?,
    val isFavorite: Boolean,
    val isFrequent: Boolean,
) {
    val label: String get() = displayName?.takeIf(String::isNotBlank)
        ?: name?.takeIf(String::isNotBlank)
        ?: email.orEmpty()
}

data class DashboardCounts(
    val emails: Int,
    val unread: Int,
    val conversations: Int,
    val contacts: Int,
)

data class Dashboard(
    val counts: DashboardCounts,
    val recentEmails: List<MailboxEmail>,
    val accounts: List<EmailAccount>,
)

data class EmailAccount(
    val id: Int,
    val email: String,
    val provider: String,
    val isPrimary: Boolean,
    val isActive: Boolean,
    val canSend: Boolean,
    val syncStatus: String,
)

data class Passkey(
    val id: String,
    val name: String,
    val deviceType: String,
    val backedUp: Boolean,
    val createdAt: String,
    val lastUsedAt: String?,
)

data class NotificationCursorItem(
    val id: Int,
    val threadId: String?,
    val receivedAt: String,
)

data class NotificationCursorPage(
    val emails: List<NotificationCursorItem>,
    val hasMore: Boolean,
    val latestEmailId: Int,
    val nextAfterId: Int,
)

internal fun JSONObject.optionalString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

internal fun JSONArray.objectList(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

internal fun parseUser(value: JSONObject) = User(
    id = value.getInt("id"),
    username = value.getString("username"),
    email = value.getString("email"),
    name = value.optionalString("name"),
    pseudonym = value.optionalString("pseudonym"),
    avatarUrl = value.optionalString("avatarUrl"),
    isAdmin = value.optBoolean("isAdmin"),
    twoFactorEnabled = value.optBoolean("twoFactorEnabled"),
)

internal fun parseEmail(value: JSONObject): MailboxEmail {
    val thread = value.optJSONObject("thread")
    val emailAccountId = value.optJSONObject("emailAccount")?.optInt("id")?.takeIf { it > 0 }
        ?: value.optInt("emailAccountId").takeIf { it > 0 }
    return MailboxEmail(
        id = value.getInt("id"),
        sender = value.optString("sender"),
        senderName = value.optionalString("senderName"),
        subject = value.optString("subject", "(Sans objet)"),
        body = value.optString("body"),
        emailAccountId = emailAccountId,
        receivedAt = value.optString("receivedAt"),
        status = value.optString("status", "READ"),
        hasAttachments = value.optBoolean("hasAttachments"),
        threadId = thread?.optionalString("externalThreadId") ?: value.optionalString("threadId"),
        isStarred = thread?.optBoolean("isStarred") ?: false,
        attachments = value.optJSONArray("attachments")?.objectList()?.map { attachment ->
            EmailAttachment(
                id = attachment.getInt("id"),
                filename = attachment.optString("filename", "Pièce jointe"),
                mimeType = attachment.optString("mimeType", "application/octet-stream"),
                fileSize = attachment.optLong("fileSize"),
            )
        }.orEmpty(),
    )
}

internal fun parseThread(value: JSONObject) = MailThread(
    id = value.getString("externalThreadId"),
    subject = value.optString("subject", "(Sans objet)"),
    snippet = value.optString("snippet"),
    participants = value.optJSONArray("participants")?.let { participants ->
        buildList {
            for (index in 0 until participants.length()) {
                participants.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.orEmpty(),
    lastMessageAt = value.optString("lastMessageAt"),
    messageCount = value.optInt("messageCount"),
    hasUnread = value.optBoolean("hasUnread"),
    isStarred = value.optBoolean("isStarred"),
    isMuted = value.optBoolean("isMuted"),
)

internal fun parseThreadMessage(value: JSONObject) = ThreadMessage(
    id = value.getInt("id"),
    sender = value.optString("sender"),
    senderName = value.optionalString("senderName"),
    subject = value.optString("subject", "(Sans objet)"),
    body = value.optString("body"),
    emailAccountId = value.optInt("emailAccountId").takeIf { it > 0 },
    receivedAt = value.optString("receivedAt"),
    status = value.optString("status", "READ"),
)

internal fun parseContact(value: JSONObject) = Contact(
    id = value.getInt("id"),
    email = value.optionalString("email"),
    name = value.optionalString("name"),
    displayName = value.optionalString("displayName"),
    company = value.optionalString("company"),
    jobTitle = value.optionalString("jobTitle"),
    phone = value.optionalString("phone"),
    isFavorite = value.optBoolean("isFavorite"),
    isFrequent = value.optBoolean("isFrequent"),
)

internal fun parseEmailAccount(value: JSONObject) = EmailAccount(
    id = value.optString("id").toIntOrNull() ?: value.optInt("id"),
    email = value.optString("email"),
    provider = value.optString("provider", "Phacteur"),
    isPrimary = value.optBoolean("isPrimary"),
    isActive = value.optBoolean("isActive", true),
    canSend = value.optBoolean("canSend", true),
    syncStatus = value.optString("syncStatus", "IDLE"),
)
