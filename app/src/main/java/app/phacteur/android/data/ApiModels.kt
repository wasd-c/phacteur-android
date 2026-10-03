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
    val htmlBody: String? = null,
    val duplicateIds: List<Int> = emptyList(),
    val duplicateCount: Int = 1,
    val direction: String = "UNKNOWN",
    val metadata: EmailMetadata? = null,
) {
    val displaySender: String get() = senderName?.takeIf(String::isNotBlank) ?: sender

    /** A mailbox row represents only the copies included by its server-side scope. */
    val statusUpdateIds: List<Int> get() = (listOf(id) + duplicateIds).filter { it > 0 }.distinct()
}

internal fun emailStatusBatches(emailIds: List<Int>): List<List<Int>> {
    require(emailIds.isNotEmpty() && emailIds.all { it > 0 }) { "Les identifiants des emails doivent être positifs" }
    return emailIds.distinct().chunked(500)
}

/** Retain the exact source email while recovering copies visible in the loaded mailbox scope. */
fun MailboxEmail.withDuplicateMetadata(rows: List<MailboxEmail>): MailboxEmail {
    val row = rows.firstOrNull { id in it.statusUpdateIds } ?: return this
    return copy(
        duplicateIds = row.statusUpdateIds,
        duplicateCount = maxOf(row.duplicateCount, row.statusUpdateIds.size),
        direction = direction.takeIf { it != "UNKNOWN" } ?: row.direction.takeIf { row.id == id } ?: direction,
    )
}

data class MailboxPage(
    val emails: List<MailboxEmail>,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean,
    val totalCount: Int = emails.size,
    val searchLimited: Boolean = false,
)

sealed interface MailboxScope {
    data object All : MailboxScope
    data class Account(val id: Int) : MailboxScope
    data class Group(val id: String) : MailboxScope
}

data class MailboxGroupMember(
    val emailAccountId: Int,
    val email: String,
    val displayName: String?,
    val isActive: Boolean,
)

data class MailboxGroup(
    val id: String,
    val name: String,
    val color: String?,
    val members: List<MailboxGroupMember>,
) {
    val activeAccountIds: List<Int> get() = members.filter { it.isActive }
        .map(MailboxGroupMember::emailAccountId).distinct()
}

/** A missing or empty group must stay empty, never fall back to all mailboxes. */
fun MailboxScope.accountIds(groups: List<MailboxGroup>): List<Int>? = when (this) {
    MailboxScope.All -> null
    is MailboxScope.Account -> listOf(id)
    is MailboxScope.Group -> groups.firstOrNull { it.id == id }?.activeAccountIds.orEmpty()
}

internal data class MailboxQuery(
    val status: String? = null,
    val search: String = "",
    val accountIds: List<Int>? = null,
    val category: String? = null,
) {
    val isEmptyScope: Boolean get() = accountIds?.isEmpty() == true

    fun parameters(page: Int, limit: Int): Map<String, String> {
        require(page > 0 && limit in 1..100)
        require(accountIds == null || accountIds.size <= 100 && accountIds.all { it > 0 })
        require(category == null || category in setOf("important", "newsletter", "other"))
        require(status == null || status in setOf("READ", "UNREAD", "ARCHIVED", "DELETED"))
        // The API treats an omitted/blank accountIds as all accounts.
        check(!isEmptyScope) { "Un groupe vide ne doit pas lancer une requête globale" }
        return buildMap {
            put("page", page.toString())
            put("limit", limit.toString())
            put("sort", "priority")
            if (status != "ARCHIVED" && status != "DELETED") put("inbox", "true")
            status?.let { put("status", it) }
            search.trim().takeIf(String::isNotBlank)?.let { put("search", it) }
            accountIds?.let { put("accountIds", it.distinct().joinToString(",")) }
            category?.let { put("category", it) }
        }
    }
}

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
    val htmlBody: String? = null,
    val direction: String = "UNKNOWN",
    val metadata: EmailMetadata? = null,
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
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val accountType: String = "UNKNOWN",
    val isLocal: Boolean = false,
    val canSync: Boolean = false,
    val lastSyncAt: String? = null,
    val customDomain: String? = null,
    val customDomainReady: Boolean? = null,
    val replyTo: String? = null,
    val addedAt: String? = null,
) {
    val label: String get() = displayName?.takeIf(String::isNotBlank) ?: email
}

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
    val id = value.getInt("id")
    val thread = value.optJSONObject("thread")
    val emailAccountId = value.optJSONObject("emailAccount")?.optInt("id")?.takeIf { it > 0 }
        ?: value.optInt("emailAccountId").takeIf { it > 0 }
    val duplicateIds = value.optJSONArray("duplicateIds")?.let { ids ->
        buildList {
            for (index in 0 until ids.length()) {
                ids.optString(index).toIntOrNull()?.takeIf { it > 0 }?.let(::add)
            }
        }.distinct()
    }.orEmpty()
    val representedCount = (listOf(id) + duplicateIds).filter { it > 0 }.distinct().size.coerceAtLeast(1)
    return MailboxEmail(
        id = id,
        sender = value.optString("sender"),
        senderName = value.optionalString("senderName"),
        subject = value.optString("subject", "(Sans objet)"),
        body = value.optionalString("body").orEmpty(),
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
        htmlBody = value.optionalString("htmlBody"),
        duplicateIds = duplicateIds,
        duplicateCount = maxOf(value.optionalString("duplicateCount")?.toIntOrNull() ?: 1, representedCount),
        direction = value.optString("direction", "UNKNOWN"),
        metadata = parseEmailMetadata(value),
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
    body = value.optionalString("body").orEmpty(),
    emailAccountId = value.optJSONObject("emailAccount")?.optInt("id")?.takeIf { it > 0 }
        ?: value.optInt("emailAccountId").takeIf { it > 0 },
    receivedAt = value.optString("receivedAt"),
    status = value.optString("status", "READ"),
    htmlBody = value.optionalString("htmlBody"),
    direction = value.optString("direction", "UNKNOWN"),
    metadata = parseEmailMetadata(value),
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
    provider = value.optString("provider", "Messagerie"),
    isPrimary = value.optBoolean("isPrimary"),
    isActive = value.optBoolean("isActive", true),
    canSend = value.optBoolean("canSend", false),
    syncStatus = value.optString("syncStatus", "UNKNOWN"),
    displayName = value.optionalString("displayName"),
    avatarUrl = value.optionalString("avatarUrl"),
    accountType = value.optString("accountType", "UNKNOWN"),
    isLocal = value.optBoolean("isLocal", false),
    canSync = value.optBoolean("canSync", false),
    lastSyncAt = value.optionalString("lastSyncAt"),
    customDomain = value.optionalString("customDomain"),
    customDomainReady = if (value.has("customDomainReady") && !value.isNull("customDomainReady")) {
        value.optBoolean("customDomainReady")
    } else null,
    replyTo = value.optionalString("replyTo"),
    addedAt = value.optionalString("addedAt"),
)

internal fun parseMailboxGroup(value: JSONObject) = MailboxGroup(
    id = value.getString("id"),
    name = value.getString("name"),
    color = value.optionalString("color"),
    members = value.optJSONArray("members")?.objectList()?.map { member ->
        MailboxGroupMember(
            emailAccountId = member.getString("emailAccountId").toInt(),
            email = member.optString("email"),
            displayName = member.optionalString("displayName"),
            isActive = member.optBoolean("isActive", true),
        )
    }.orEmpty(),
)
