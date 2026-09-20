package app.phacteur.android.ui

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.phacteur.android.auth.MobileAuthManager
import app.phacteur.android.auth.PasskeyClient
import app.phacteur.android.auth.PasskeySignInUnavailableException
import app.phacteur.android.data.ApiException
import app.phacteur.android.data.AppGraph
import app.phacteur.android.data.Contact
import app.phacteur.android.data.Dashboard
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailThread
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.Passkey
import app.phacteur.android.data.ThreadMessage
import app.phacteur.android.data.User
import app.phacteur.android.notifications.NotificationPreferences
import app.phacteur.android.notifications.PushRegistrationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Destination { DASHBOARD, MAILBOX, CONVERSATIONS, CONTACTS, SETTINGS }

data class ComposeDraft(
    val recipients: String = "",
    val subject: String = "",
    val body: String = "",
    val threadId: String? = null,
    val emailAccountId: Int? = null,
)

data class PhacteurUiState(
    val authenticating: Boolean = true,
    val user: User? = null,
    val destination: Destination = Destination.DASHBOARD,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val dashboard: Dashboard? = null,
    val emails: List<MailboxEmail> = emptyList(),
    val mailboxStatus: String? = null,
    val mailboxSearch: String = "",
    val mailboxPage: Int = 1,
    val mailboxHasMore: Boolean = false,
    val selectedEmail: MailboxEmail? = null,
    val threads: List<MailThread> = emptyList(),
    val selectedThread: MailThread? = null,
    val threadMessages: List<ThreadMessage> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val contactSearch: String = "",
    val accounts: List<EmailAccount> = emptyList(),
    val passkeys: List<Passkey> = emptyList(),
    val composeDraft: ComposeDraft? = null,
    val notificationsEnabled: Boolean = false,
    val firebaseConfigured: Boolean = false,
    val canRetrySession: Boolean = false,
    val preferBrowserSignIn: Boolean = false,
)

class PhacteurViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = AppGraph.from(application)
    private val api = graph.api
    private val passkeyClient = PasskeyClient(api)
    private val mobileAuth = MobileAuthManager(graph.secureStorage, api)
    private val notificationPreferences = NotificationPreferences(application)
    private val authMutex = Mutex()
    private var pendingNotificationTarget: Pair<Int?, String?>? = null
    private var mailboxSearchJob: Job? = null
    private var mailboxLoadJob: Job? = null
    private var contactSearchJob: Job? = null

    private val _state = MutableStateFlow(
        PhacteurUiState(
            notificationsEnabled = notificationPreferences.enabled,
            firebaseConfigured = PushRegistrationManager.isFirebaseConfigured(application),
        ),
    )
    val state: StateFlow<PhacteurUiState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    fun browserAuthorizationUri(): Uri = mobileAuth.createAuthorizationUri()

    fun completeMobileAuthorization(uri: Uri) = launchAction(authenticating = true) {
        authMutex.withLock {
            val user = mobileAuth.completeCallback(uri)
            onAuthenticated(user)
        }
    }

    fun signInWithPasskey(activity: ComponentActivity) = launchAction(authenticating = true) {
        authMutex.withLock {
            _state.update { it.copy(preferBrowserSignIn = false) }
            try {
                val user = passkeyClient.signIn(activity)
                onAuthenticated(user)
            } catch (error: PasskeySignInUnavailableException) {
                _state.update { it.copy(preferBrowserSignIn = true) }
                throw error
            }
        }
    }

    fun retrySession() = restoreSession()

    fun registerPasskey(activity: ComponentActivity) = launchAction {
        val passkey = passkeyClient.register(activity, "Android")
        _state.update {
            it.copy(
                passkeys = listOf(passkey) + it.passkeys.filterNot { existing -> existing.id == passkey.id },
                message = "Passkey Android ajoutée",
            )
        }
    }

    fun navigate(destination: Destination) {
        _state.update {
            it.copy(
                destination = destination,
                selectedEmail = null,
                selectedThread = null,
                threadMessages = emptyList(),
            )
        }
        refreshDestination(destination)
    }

    fun refresh() = refreshDestination(_state.value.destination, force = true)

    fun searchMailbox(query: String) {
        _state.update { it.copy(mailboxSearch = query) }
        mailboxSearchJob?.cancel()
        mailboxSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            loadMailbox(reset = true)
        }
    }

    fun setMailboxStatus(status: String?) {
        _state.update { it.copy(mailboxStatus = status) }
        mailboxSearchJob?.cancel()
        loadMailbox(reset = true)
    }

    fun loadMoreEmails() {
        if (!_state.value.mailboxHasMore || _state.value.loading) return
        loadMailbox(reset = false)
    }

    fun selectEmail(email: MailboxEmail?) {
        _state.update { it.copy(selectedEmail = email) }
        if (email?.status == "UNREAD") updateEmailStatus(email, "READ", closeAfter = false)
    }

    fun updateEmailStatus(email: MailboxEmail, status: String, closeAfter: Boolean = true) = launchAction {
        api.updateEmailStatus(email.id, status)
        _state.update { current ->
            current.copy(
                emails = current.emails.map { item ->
                    if (item.id == email.id) item.copy(status = status) else item
                }.filterNot { status in setOf("ARCHIVED", "DELETED") && it.id == email.id },
                selectedEmail = if (closeAfter && status in setOf("ARCHIVED", "DELETED")) null
                else current.selectedEmail?.let { if (it.id == email.id) it.copy(status = status) else it },
                message = when (status) {
                    "ARCHIVED" -> "Email archivé"
                    "DELETED" -> "Email supprimé"
                    else -> null
                },
            )
        }
    }

    fun selectThread(thread: MailThread?) {
        _state.update { it.copy(selectedThread = thread, threadMessages = emptyList()) }
        if (thread == null) return
        launchAction {
            val messages = api.threadMessages(thread.id)
            if (thread.hasUnread) api.markThreadRead(thread.id, unread = false)
            _state.update { current ->
                current.copy(
                    threadMessages = messages,
                    threads = current.threads.map {
                        if (it.id == thread.id) it.copy(hasUnread = false) else it
                    },
                    selectedThread = current.selectedThread?.let {
                        if (it.id == thread.id) it.copy(hasUnread = false) else it
                    },
                )
            }
        }
    }

    fun toggleThreadStar(thread: MailThread) = launchAction {
        api.starThread(thread.id, !thread.isStarred)
        _state.update { current ->
            current.copy(
                threads = current.threads.map {
                    if (it.id == thread.id) it.copy(isStarred = !thread.isStarred) else it
                },
                selectedThread = current.selectedThread?.let {
                    if (it.id == thread.id) it.copy(isStarred = !thread.isStarred) else it
                },
            )
        }
    }

    fun archiveThread(thread: MailThread) = launchAction {
        api.archiveThread(thread.id)
        _state.update {
            it.copy(
                threads = it.threads.filterNot { item -> item.id == thread.id },
                selectedThread = null,
                threadMessages = emptyList(),
                message = "Conversation archivée",
            )
        }
    }

    fun searchContacts(query: String) {
        _state.update { it.copy(contactSearch = query) }
        contactSearchJob?.cancel()
        contactSearchJob = launchAction {
            delay(SEARCH_DEBOUNCE_MILLIS)
            val contacts = api.contacts(query)
            if (_state.value.contactSearch == query) {
                _state.update { it.copy(contacts = contacts) }
            }
        }
    }

    fun openCompose(
        recipients: String = "",
        subject: String = "",
        threadId: String? = null,
        emailAccountId: Int? = null,
    ) {
        val selectedAccountId = emailAccountId
            ?: _state.value.accounts.firstOrNull { it.isActive && it.canSend }?.id
        _state.update {
            it.copy(
                composeDraft = ComposeDraft(
                    recipients = recipients,
                    subject = subject,
                    threadId = threadId,
                    emailAccountId = selectedAccountId,
                ),
            )
        }
    }

    fun updateComposeDraft(draft: ComposeDraft) {
        _state.update { it.copy(composeDraft = draft) }
    }

    fun closeCompose() {
        _state.update { it.copy(composeDraft = null) }
    }

    fun sendDraft() {
        val draft = _state.value.composeDraft ?: return
        val recipients = draft.recipients.split(',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)
        val sendableAccounts = _state.value.accounts.filter { it.isActive && it.canSend }
        val account = sendableAccounts.firstOrNull { it.id == draft.emailAccountId }
            ?: sendableAccounts.firstOrNull()
        if (recipients.isEmpty() || account == null || draft.body.isBlank()) {
            _state.update { it.copy(error = "Destinataire, compte expéditeur et message sont requis") }
            return
        }
        launchAction {
            api.sendMessage(
                accountId = account.id,
                recipients = recipients,
                subject = draft.subject,
                body = draft.body,
                threadId = draft.threadId,
            )
            _state.update { it.copy(composeDraft = null, message = "Message envoyé") }
            refreshDestination(_state.value.destination, force = true)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = launchAction {
        if (enabled) {
            PushRegistrationManager.enable(getApplication())
        } else {
            PushRegistrationManager.disable(getApplication())
        }
        _state.update {
            it.copy(
                notificationsEnabled = notificationPreferences.enabled,
                firebaseConfigured = PushRegistrationManager.isFirebaseConfigured(getApplication()),
            )
        }
    }

    fun refreshNotificationDelivery() {
        if (_state.value.user != null) PushRegistrationManager.refreshAfterLogin(getApplication())
    }

    fun logout() = launchAction {
        authMutex.withLock {
            PushRegistrationManager.disable(getApplication())
            api.logout()
            _state.value = signedOutState()
        }
    }

    fun clearTransientMessage() {
        _state.update { it.copy(error = null, message = null) }
    }

    fun handleNotification(emailId: Int?, threadId: String?) {
        if (_state.value.user == null) {
            pendingNotificationTarget = emailId to threadId
            return
        }
        navigate(Destination.MAILBOX)
        if (emailId != null) {
            val cached = _state.value.emails.firstOrNull { it.id == emailId }
            if (cached != null) {
                selectEmail(cached)
            } else {
                launchAction {
                    selectEmail(api.email(emailId))
                }
            }
        } else if (threadId != null) {
            navigate(Destination.CONVERSATIONS)
            _state.value.threads.firstOrNull { it.id == threadId }?.let(::selectThread)
        }
    }

    private fun restoreSession() = viewModelScope.launch {
        authMutex.withLock {
            if (!api.hasSession()) {
                if (notificationPreferences.enabled) {
                    PushRegistrationManager.disable(getApplication())
                }
                _state.value = signedOutState()
                return@withLock
            }
            _state.update { it.copy(authenticating = true, error = null, canRetrySession = false) }
            try {
                onAuthenticated(api.me())
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is ApiException && error.statusCode in setOf(401, 404)) {
                    PushRegistrationManager.disable(getApplication())
                    graph.cookieStore.clear()
                    _state.value = signedOutState("Votre session a expiré")
                } else {
                    _state.update {
                        it.copy(
                            authenticating = false,
                            error = "Connexion impossible. Votre session locale a été conservée.",
                            canRetrySession = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun onAuthenticated(user: User) {
        _state.value = PhacteurUiState(
            authenticating = false,
            user = user,
            notificationsEnabled = notificationPreferences.enabled,
            firebaseConfigured = PushRegistrationManager.isFirebaseConfigured(getApplication()),
            canRetrySession = false,
        )
        PushRegistrationManager.refreshAfterLogin(getApplication())
        loadInitialData()
        pendingNotificationTarget?.let { (emailId, threadId) ->
            pendingNotificationTarget = null
            handleNotification(emailId, threadId)
        }
    }

    private suspend fun loadInitialData() = coroutineScope {
        _state.update { it.copy(loading = true) }
        listOf(
            async { runCatching { api.dashboard() }.onSuccess { value -> _state.update { it.copy(dashboard = value) } } },
            async { runCatching { api.emails() }.onSuccess { page -> applyMailboxPage(page, reset = true) } },
            async { runCatching { api.threads() }.onSuccess { value -> _state.update { it.copy(threads = value) } } },
            async { runCatching { api.contacts() }.onSuccess { value -> _state.update { it.copy(contacts = value) } } },
            async { runCatching { api.emailAccounts() }.onSuccess { value -> _state.update { it.copy(accounts = value) } } },
            async { runCatching { api.passkeys() }.onSuccess { value -> _state.update { it.copy(passkeys = value) } } },
        ).awaitAll()
        _state.update { it.copy(loading = false) }
    }

    private fun refreshDestination(destination: Destination, force: Boolean = false) {
        when (destination) {
            Destination.DASHBOARD -> if (force || _state.value.dashboard == null) launchAction(refreshing = true) {
                _state.update { it.copy(dashboard = api.dashboard()) }
            }
            Destination.MAILBOX -> if (force || _state.value.emails.isEmpty()) loadMailbox(reset = true, refreshing = true)
            Destination.CONVERSATIONS -> if (force || _state.value.threads.isEmpty()) launchAction(refreshing = true) {
                _state.update { it.copy(threads = api.threads()) }
            }
            Destination.CONTACTS -> if (force || _state.value.contacts.isEmpty()) launchAction(refreshing = true) {
                contactSearchJob?.cancel()
                _state.update { it.copy(contacts = api.contacts(_state.value.contactSearch)) }
            }
            Destination.SETTINGS -> launchAction(refreshing = true) {
                val (accounts, passkeys) = coroutineScope {
                    async { api.emailAccounts() } to async { api.passkeys() }
                }.let { (accountsTask, passkeysTask) -> accountsTask.await() to passkeysTask.await() }
                _state.update { it.copy(accounts = accounts, passkeys = passkeys) }
            }
        }
    }

    private fun loadMailbox(reset: Boolean, refreshing: Boolean = false) {
        mailboxLoadJob?.cancel()
        mailboxLoadJob = launchAction(refreshing = refreshing) {
            val current = _state.value
            val requestedStatus = current.mailboxStatus
            val requestedSearch = current.mailboxSearch
            val targetPage = if (reset) 1 else current.mailboxPage + 1
            val page = api.emails(
                status = requestedStatus,
                search = requestedSearch,
                page = targetPage,
            )
            val latest = _state.value
            if (latest.mailboxStatus == requestedStatus && latest.mailboxSearch == requestedSearch) {
                applyMailboxPage(page, reset)
            }
        }
    }

    private fun applyMailboxPage(page: app.phacteur.android.data.MailboxPage, reset: Boolean) {
        _state.update {
            it.copy(
                emails = if (reset) page.emails else (it.emails + page.emails).distinctBy(MailboxEmail::id),
                mailboxPage = page.page,
                mailboxHasMore = page.hasMore,
            )
        }
    }

    private fun launchAction(
        authenticating: Boolean = false,
        refreshing: Boolean = false,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        _state.update {
            it.copy(
                authenticating = if (authenticating) true else it.authenticating,
                loading = if (!authenticating && !refreshing) true else it.loading,
                refreshing = refreshing,
                error = null,
            )
        }
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is ApiException && error.statusCode == 401 && _state.value.user != null) {
                PushRegistrationManager.disable(getApplication())
                graph.cookieStore.clear()
                _state.value = signedOutState("Votre session a expiré")
            } else {
                _state.update {
                    it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Une erreur est survenue")
                }
            }
        } finally {
            _state.update {
                it.copy(
                    authenticating = if (authenticating) false else it.authenticating,
                    loading = if (!authenticating && !refreshing) false else it.loading,
                    refreshing = false,
                )
            }
        }
    }

    private fun signedOutState(error: String? = null) = PhacteurUiState(
        authenticating = false,
        error = error,
        notificationsEnabled = notificationPreferences.enabled,
        firebaseConfigured = PushRegistrationManager.isFirebaseConfigured(getApplication()),
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
    }
}
