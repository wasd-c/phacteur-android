package app.phacteur.android.data

import android.net.Uri
import app.phacteur.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(
    val statusCode: Int,
    override val message: String,
) : IOException(message)

class PhacteurApi(
    private val cookieStore: SessionCookieStore,
    baseUrl: String = BuildConfig.PHACTEUR_BASE_URL,
) {
    private val baseUrl = URL(baseUrl)
    private val webOrigin = buildString {
        append(this@PhacteurApi.baseUrl.protocol)
        append("://")
        append(this@PhacteurApi.baseUrl.authority)
    }

    suspend fun me(): User = requestObject("api/auth/me").getJSONObject("user").let(::parseUser)

    suspend fun logout() {
        runCatching { requestObject("api/auth/logout", method = "POST") }
        cookieStore.clear()
    }

    suspend fun passkeyAuthenticationOptions(): String =
        requestText("api/auth/passkeys/authenticate/options", method = "POST")

    suspend fun verifyPasskeyAuthentication(authenticationResponseJson: String): User =
        requestObject(
            path = "api/auth/passkeys/authenticate/verify",
            method = "POST",
            rawJsonBody = authenticationResponseJson,
        ).getJSONObject("user").let(::parseUser)

    suspend fun passkeyRegistrationOptions(): String =
        requestText("api/auth/passkeys/register/options", method = "POST")

    suspend fun verifyPasskeyRegistration(name: String, registrationResponseJson: String): Passkey {
        val responseObject = JSONObject(registrationResponseJson)
        val payload = JSONObject().put("name", name).put("response", responseObject)
        val passkey = requestObject(
            path = "api/auth/passkeys/register/verify",
            method = "POST",
            jsonBody = payload,
        ).getJSONObject("passkey")
        return parsePasskey(passkey)
    }

    suspend fun exchangeMobileGrant(
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String,
    ): User = requestObject(
        path = "api/mobile-auth/exchange",
        method = "POST",
        jsonBody = JSONObject()
            .put("code", code)
            .put("codeVerifier", codeVerifier)
            .put("clientId", clientId)
            .put("redirectUri", redirectUri),
    ).getJSONObject("user").let(::parseUser)

    suspend fun emails(
        status: String? = null,
        search: String = "",
        page: Int = 1,
        limit: Int = 50,
        accountIds: List<Int>? = null,
        category: String? = null,
    ): MailboxPage {
        val mailboxQuery = MailboxQuery(status, search, accountIds, category)
        if (mailboxQuery.isEmptyScope) {
            return MailboxPage(emptyList(), page = 1, totalPages = 0, hasMore = false, totalCount = 0)
        }
        val query = Uri.Builder()
            .apply {
                mailboxQuery.parameters(page, limit).forEach { (key, value) ->
                    appendQueryParameter(key, value)
                }
            }
            .build()
            .encodedQuery
        val response = requestObject("api/emails?$query")
        val pagination = response.getJSONObject("pagination")
        return MailboxPage(
            emails = response.getJSONArray("emails").objectList().map(::parseEmail),
            page = pagination.optInt("page", page),
            totalPages = pagination.optInt("totalPages", 1),
            hasMore = pagination.optBoolean("hasMore"),
            totalCount = pagination.optInt("totalCount"),
            searchLimited = pagination.optBoolean("searchLimited"),
        )
    }

    suspend fun email(emailId: Int): MailboxEmail {
        require(emailId > 0) { "L’identifiant de l’email doit être positif" }
        return requestObject("api/mobile/emails/$emailId")
            .getJSONObject("email")
            .let(::parseEmail)
    }

    suspend fun updateEmailStatus(emailId: Int, status: String) = updateEmailStatus(listOf(emailId), status)

    suspend fun updateEmailStatus(emailIds: List<Int>, status: String) {
        require(status in setOf("UNREAD", "READ", "ARCHIVED", "DELETED")) { "Statut d’email invalide" }
        // A visible deduplicated row can contain more than the API's 500-ID limit.
        for (batch in emailStatusBatches(emailIds)) {
            requestObject(
                path = "api/emails",
                method = "PATCH",
                jsonBody = JSONObject().put("emailIds", JSONArray(batch)).put("status", status),
            )
        }
    }

    suspend fun threads(accountIds: List<Int>? = null): List<MailThread> {
        if (accountIds?.isEmpty() == true) return emptyList()
        require(accountIds == null || accountIds.size <= 100 && accountIds.all { it > 0 })
        val query = Uri.Builder().appendQueryParameter("scope", "conversations").apply {
            accountIds?.let { appendQueryParameter("accountIds", it.distinct().joinToString(",")) }
        }.build().encodedQuery
        return requestObject("api/threads?$query").getJSONArray("threads").objectList().map(::parseThread)
    }

    suspend fun threadMessages(threadId: String, cachedEmails: List<MailboxEmail> = emptyList()): List<ThreadMessage> {
        val values = requestObject("api/threads/${pathSegment(threadId)}/messages")
            .getJSONArray("messages")
            .objectList()
        val cachedById = cachedEmails.associateBy(MailboxEmail::id)
        val requests = Semaphore(4)
        return coroutineScope {
            values.map { value ->
                async {
                    val message = parseThreadMessage(value)
                    // Older thread endpoints omit the HTML alternative entirely.
                    // Explicit null means a newer endpoint has already checked it.
                    if (value.has("htmlBody")) return@async message
                    cachedById[message.id]?.htmlBody?.let { return@async message.copy(htmlBody = it) }
                    requests.withPermit {
                        try {
                            message.copy(htmlBody = email(message.id).htmlBody)
                        } catch (error: IOException) {
                            if (error is ApiException && error.statusCode in setOf(401, 403)) throw error
                            // Keep the thread's text available if the detail endpoint is unavailable.
                            message
                        }
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun markThreadRead(threadId: String, unread: Boolean) {
        requestObject(
            path = "api/threads/${pathSegment(threadId)}/read",
            method = "PATCH",
            jsonBody = JSONObject().put("hasUnread", unread),
        )
    }

    suspend fun starThread(threadId: String, starred: Boolean) {
        requestObject(
            path = "api/threads/${pathSegment(threadId)}/star",
            method = "PATCH",
            jsonBody = JSONObject().put("isStarred", starred),
        )
    }

    suspend fun archiveThread(threadId: String) {
        requestObject(
            path = "api/threads/${pathSegment(threadId)}",
            method = "PATCH",
            jsonBody = JSONObject().put("status", "ARCHIVED"),
        )
    }

    suspend fun contacts(search: String = ""): List<Contact> {
        val query = search.trim().takeIf(String::isNotBlank)?.let {
            "?search=${Uri.encode(it)}"
        }.orEmpty()
        return requestObject("api/contacts$query")
            .getJSONArray("contacts")
            .objectList()
            .map(::parseContact)
    }

    suspend fun dashboard(): Dashboard {
        val response = requestObject("api/dashboard")
        val counts = response.getJSONObject("counts")
        return Dashboard(
            counts = DashboardCounts(
                emails = counts.optInt("emails"),
                unread = counts.optInt("unread"),
                conversations = counts.optInt("conversations"),
                contacts = counts.optInt("contacts"),
            ),
            recentEmails = response.getJSONArray("recentEmails").objectList().map { recent ->
                MailboxEmail(
                    id = recent.getInt("id"),
                    sender = recent.optString("sender"),
                    senderName = recent.optionalString("senderName"),
                    subject = recent.optString("subject", "(Sans objet)"),
                    body = recent.optString("preview"),
                    emailAccountId = null,
                    receivedAt = recent.optString("receivedAt"),
                    status = recent.optString("status", "READ"),
                    hasAttachments = recent.optBoolean("hasAttachments"),
                    threadId = null,
                    isStarred = false,
                    attachments = emptyList(),
                )
            },
            accounts = response.getJSONArray("accounts").objectList().map(::parseEmailAccount),
        )
    }

    suspend fun emailAccounts(): List<EmailAccount> = requestObject("api/email-accounts")
        .getJSONArray("emailAccounts")
        .objectList()
        .map(::parseEmailAccount)

    suspend fun synchronizeAccount(accountId: Int) {
        require(accountId > 0)
        requestObject("api/email-accounts/$accountId/sync", method = "POST")
    }

    suspend fun renewGmailReception(accountId: Int) {
        require(accountId > 0)
        requestObject(
            "api/webhooks/gmail/watch", method = "POST",
            jsonBody = JSONObject().put("emailAccountId", accountId.toString()),
        )
    }

    suspend fun updateAccountProfile(account: EmailAccount, draft: MailboxProfileDraft): EmailAccount {
        require(account.id > 0)
        require(draft.validationError() == null)
        val value = draft.normalized()
        // The server resets omitted identity fields. Preserve the stored avatar.
        val updated = requestObject(
            "api/email-accounts/${account.id}", method = "PATCH",
            jsonBody = JSONObject()
                .put("displayName", value.displayName.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                .put("replyTo", value.replyTo.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                .put("avatarUrl", account.avatarUrl ?: JSONObject.NULL),
        ).getJSONObject("account")
        check(updated.getString("id").toIntOrNull() == account.id)
        return account.copy(
            displayName = updated.optionalString("displayName"),
            replyTo = updated.optionalString("replyTo"),
            avatarUrl = updated.optionalString("avatarUrl"),
        )
    }

    suspend fun mailboxGroups(): List<MailboxGroup> = requestObject("api/mailbox-groups")
        .getJSONArray("mailboxGroups")
        .objectList()
        .map(::parseMailboxGroup)

    suspend fun saveMailboxGroup(groupId: String?, draft: MailboxGroupDraft): MailboxGroup {
        val payload = draft.normalized()
        require(payload.validationError() == null) { payload.validationError().orEmpty() }
        return requestObject(
            path = if (groupId == null) "api/mailbox-groups" else "api/mailbox-groups/${pathSegment(groupId)}",
            method = if (groupId == null) "POST" else "PATCH",
            jsonBody = JSONObject()
                .put("name", payload.name)
                .put("color", payload.color ?: JSONObject.NULL)
                .put("memberAccountIds", JSONArray(payload.memberAccountIds)),
        ).getJSONObject("mailboxGroup").let(::parseMailboxGroup)
    }

    suspend fun deleteMailboxGroup(groupId: String) {
        requestObject("api/mailbox-groups/${pathSegment(groupId)}", method = "DELETE")
    }

    suspend fun calendar(range: CalendarRange): List<MailCalendarEvent> {
        val query = Uri.Builder().apply {
            range.parameters().forEach { (key, value) -> appendQueryParameter(key, value) }
        }.build().encodedQuery
        return requestObject("api/calendar?$query").getJSONArray("events")
            .objectList().map(::parseCalendarEvent)
    }

    suspend fun passkeys(): List<Passkey> = requestObject("api/user/passkeys")
        .getJSONArray("passkeys")
        .objectList()
        .map(::parsePasskey)

    suspend fun sendMessage(
        accountId: Int,
        recipients: List<String>,
        subject: String,
        body: String,
        threadId: String? = null,
    ) {
        val payload = JSONObject()
            .put("emailAccountId", accountId)
            .put("recipients", JSONArray(recipients))
            .put("subject", subject)
            .put("body", body)
        threadId?.let { payload.put("threadId", it) }
        requestObject("api/compose", method = "POST", jsonBody = payload)
    }

    suspend fun registerPushInstallation(fid: String, deviceName: String, appVersion: String) {
        requestObject(
            path = "api/mobile/push-registrations",
            method = "PUT",
            jsonBody = JSONObject()
                .put("firebaseInstallationId", fid)
                .put("deviceName", deviceName)
                .put("appVersion", appVersion),
        )
    }

    suspend fun deletePushInstallation(fid: String) {
        requestObject(
            path = "api/mobile/push-registrations",
            method = "DELETE",
            jsonBody = JSONObject().put("firebaseInstallationId", fid),
        )
    }

    suspend fun notificationCursor(afterId: Int, limit: Int = 100): NotificationCursorPage {
        val response = requestObject("api/mobile/email-notifications?afterId=$afterId&limit=$limit")
        return NotificationCursorPage(
            emails = response.optJSONArray("emails")?.objectList()?.map { value ->
                NotificationCursorItem(
                    id = value.getInt("id"),
                    threadId = value.optionalString("threadId"),
                    receivedAt = value.optString("receivedAt"),
                )
            }.orEmpty(),
            hasMore = response.optBoolean("hasMore"),
            latestEmailId = response.optInt("latestEmailId"),
            nextAfterId = response.optInt("nextAfterId", afterId),
        )
    }

    suspend fun notificationEmail(emailId: Int): NotificationCursorItem {
        require(emailId > 0)
        val value = requestObject("api/mobile/email-notifications/$emailId").getJSONObject("email")
        return NotificationCursorItem(
            id = value.getInt("id"),
            threadId = value.optionalString("threadId"),
            receivedAt = value.optString("receivedAt"),
        )
    }

    fun hasSession(): Boolean = cookieStore.hasSession()

    private suspend fun requestObject(
        path: String,
        method: String = "GET",
        jsonBody: JSONObject? = null,
        rawJsonBody: String? = null,
    ): JSONObject {
        val text = requestText(path, method, jsonBody?.toString() ?: rawJsonBody)
        return if (text.isBlank()) JSONObject() else try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw IOException("Réponse serveur invalide", error)
        }
    }

    private suspend fun requestText(
        path: String,
        method: String = "GET",
        rawJsonBody: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val target = URL(baseUrl, path)
        require(
            target.protocol == baseUrl.protocol &&
                target.host == baseUrl.host &&
                target.port == baseUrl.port
        ) {
            "La requête doit rester sur le serveur Phacteur configuré"
        }

        val connection = (target.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Origin", webOrigin)
            setRequestProperty("User-Agent", "Phacteur-Android/${BuildConfig.VERSION_NAME}")
            cookieStore.cookieHeader()?.let { setRequestProperty("Cookie", it) }
            if (rawJsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (rawJsonBody != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(rawJsonBody) }
            }
            val status = connection.responseCode
            val setCookies = connection.headerFields.entries
                .filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value.orEmpty() }
            cookieStore.update(setCookies)

            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("error") }
                    .getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: "Le serveur a refusé la requête ($status)"
                throw ApiException(status, message)
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun pathSegment(value: String): String = Uri.encode(value)

    private fun parsePasskey(value: JSONObject) = Passkey(
        id = value.getString("id"),
        name = value.optString("name", "Passkey"),
        deviceType = value.optString("deviceType"),
        backedUp = value.optBoolean("backedUp"),
        createdAt = value.optString("createdAt"),
        lastUsedAt = value.optionalString("lastUsedAt"),
    )
}
