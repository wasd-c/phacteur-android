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
import app.phacteur.android.data.CalendarRange
import app.phacteur.android.data.MailCalendarEvent
import app.phacteur.android.data.MailboxGroupDraft
import app.phacteur.android.data.Dashboard
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailThread
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxPage
import app.phacteur.android.data.MailboxQuery
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.data.Passkey
import app.phacteur.android.data.ThreadMessage
import app.phacteur.android.data.User
import app.phacteur.android.data.accountIds
import app.phacteur.android.data.withDuplicateMetadata
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
import java.time.LocalDate
import java.time.YearMonth

enum class Destination { DASHBOARD, MAILBOX, CONVERSATIONS, CALENDAR, CONTACTS, GROUPS, SETTINGS }

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
    val destination: Destination = Destination.MAILBOX,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val dashboard: Dashboard? = null,
    val emails: List<MailboxEmail> = emptyList(),
    val mailboxScope: MailboxScope = MailboxScope.All,
    val mailboxGroups: List<MailboxGroup> = emptyList(),
    val mailboxStatus: String? = null,
    val mailboxCategory: String? = null,
    val mailboxSearch: String = "",
    val mailboxPage: Int = 1,
    val mailboxHasMore: Boolean = false,
    val mailboxTotalCount: Int = 0,
    val mailboxSearchLimited: Boolean = false,
    val mailboxLoading: Boolean = false,
    val mailboxRefreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val mailboxError: String? = null,
    val selectedEmail: MailboxEmail? = null,
    val threads: List<MailThread> = emptyList(),
    val conversationsLoading: Boolean = false,
    val conversationsRefreshing: Boolean = false,
    val conversationsLoaded: Boolean = false,
    val conversationsError: String? = null,
    val selectedThread: MailThread? = null,
    val threadMessages: List<ThreadMessage> = emptyList(),
    val threadMessagesLoading: Boolean = false,
    val threadMessagesError: String? = null,
    val contacts: List<Contact> = emptyList(),
    val contactSearch: String = "",
    val accounts: List<EmailAccount> = emptyList(),
    val passkeys: List<Passkey> = emptyList(),
    val calendarEvents: List<MailCalendarEvent> = emptyList(),
    val calendarMonth: YearMonth = YearMonth.now(),
    val calendarDate: LocalDate = LocalDate.now(),
    val calendarLoading: Boolean = false,
    val calendarRefreshing: Boolean = false,
    val calendarLoaded: Boolean = false,
    val calendarError: String? = null,
    val groupsLoading: Boolean = false,
    val groupsRefreshing: Boolean = false,
    val groupsSaving: Boolean = false,
    val groupsLoaded: Boolean = false,
    val groupsLoadError: String? = null,
    val groupsActionError: String? = null,
    val groupsMutationVersion: Long = 0,
    val composeDraft: ComposeDraft? = null,
    val notificationsEnabled: Boolean = false,
    val firebaseConfigured: Boolean = false,
    val canRetrySession: Boolean = false,
    val preferBrowserSignIn: Boolean = false,
) {
    val mailboxScopeLabel: String get() = when (val scope = mailboxScope) {
        MailboxScope.All -> "Toutes les boîtes"
        is MailboxScope.Account -> accounts.firstOrNull { it.id == scope.id }?.label ?: "Boîte mail"
        is MailboxScope.Group -> mailboxGroups.firstOrNull { it.id == scope.id }?.name ?: "Groupe indisponible"
    }
}

internal fun PhacteurUiState.mailboxQuery() = MailboxQuery(
    status = mailboxStatus,
    search = mailboxSearch,
    accountIds = mailboxScope.accountIds(mailboxGroups),
    category = mailboxCategory,
)

internal fun PhacteurUiState.defaultSendingAccountId(): Int? {
    val sendable = accounts.filter { it.isActive && it.canSend }
    val scopedIds = mailboxScope.accountIds(mailboxGroups)
    return sendable.firstOrNull { scopedIds?.contains(it.id) == true }?.id
        ?: sendable.firstOrNull { it.isPrimary }?.id
        ?: sendable.firstOrNull()?.id
}

internal fun PhacteurUiState.applyEmailStatus(emailId: Int, status: String, closeAfter: Boolean): PhacteurUiState =
    applyEmailStatus(listOf(emailId), status, closeAfter)

internal fun PhacteurUiState.applyEmailStatus(emailIds: List<Int>, status: String, closeAfter: Boolean): PhacteurUiState {
    val affectedIds = emailIds.toSet()
    fun MailboxEmail.isAffected() = statusUpdateIds.any { it in affectedIds }
    val updated = emails.map { if (it.isAffected()) it.copy(status = status) else it }
        .filterNot { email ->
            email.isAffected() && when {
                mailboxStatus != null -> status != mailboxStatus
                else -> status in setOf("ARCHIVED", "DELETED")
            }
        }
    val removed = emails.size - updated.size
    return copy(
        emails = updated,
        mailboxTotalCount = (mailboxTotalCount - removed).coerceAtLeast(0),
        // Removing an item shifts server offsets; page two is unsafe until page one reloads.
        mailboxPage = if (removed > 0) 1 else mailboxPage,
        mailboxHasMore = if (removed > 0) false else mailboxHasMore,
        selectedEmail = if (selectedEmail?.isAffected() == true && closeAfter && status in setOf("ARCHIVED", "DELETED")) null
        else selectedEmail?.let { if (it.isAffected()) it.copy(status = status) else it },
        message = when (status) {
            "ARCHIVED" -> "Email archivé"
            "DELETED" -> "Email supprimé"
            else -> null
        },
    )
}

private fun PhacteurUiState.clearMailboxResults() = copy(
    emails = emptyList(),
    mailboxPage = 1,
    mailboxHasMore = false,
    mailboxTotalCount = 0,
    mailboxSearchLimited = false,
    mailboxLoading = true,
    mailboxRefreshing = false,
    loadingMore = false,
    mailboxError = null,
    selectedEmail = null,
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
    private var mailboxRequestVersion = 0L
    private var mailboxNavigationVersion = 0L
    private var emailSelectionVersion = 0L
    private var contactSearchJob: Job? = null
    private var calendarLoadJob: Job? = null
    private var calendarRequestVersion = 0L
    private var groupsLoadJob: Job? = null
    private var groupsRequestVersion = 0L
    private var sessionVersion = 0L
    private var conversationsLoadJob: Job? = null
    private var conversationsRequestVersion = 0L
    private var threadLoadJob: Job? = null
    private var threadSelectionVersion = 0L

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
        val session = sessionVersion
        val passkey = passkeyClient.register(activity, "Android")
        if (sessionVersion != session) return@launchAction
        _state.update {
            it.copy(
                passkeys = listOf(passkey) + it.passkeys.filterNot { existing -> existing.id == passkey.id },
                message = "Passkey Android ajoutée",
            )
        }
    }

    fun navigate(destination: Destination) {
        emailSelectionVersion++
        threadSelectionVersion++
        threadLoadJob?.cancel()
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

    fun setConversationScope(scope: MailboxScope) {
        if (_state.value.mailboxScope == scope) return
        cancelMailboxLoad()
        _state.update {
            it.copy(mailboxScope = scope, threads = emptyList(), conversationsLoaded = false,
                selectedThread = null, threadMessages = emptyList()).clearMailboxResults().copy(mailboxLoading = false)
        }
        loadConversations()
    }

    private fun loadConversations(refreshing: Boolean = false) {
        val requestVersion = ++conversationsRequestVersion
        conversationsLoadJob?.cancel()
        val userId = _state.value.user?.id ?: return
        val requestedScope = _state.value.mailboxScope
        var accountIds = _state.value.mailboxScope.accountIds(_state.value.mailboxGroups)
        _state.update { it.copy(conversationsLoading = !refreshing, conversationsRefreshing = refreshing, conversationsError = null) }
        conversationsLoadJob = viewModelScope.launch {
            try {
                if (refreshing) {
                    val navigationVersion = ++mailboxNavigationVersion
                    val (accounts, groups) = coroutineScope {
                        val accountsTask = async { api.emailAccounts() }
                        val groupsTask = async { api.mailboxGroups() }
                        accountsTask.await() to groupsTask.await()
                    }
                    if (conversationsRequestVersion != requestVersion || _state.value.user?.id != userId ||
                        _state.value.mailboxScope != requestedScope || navigationVersion != mailboxNavigationVersion) return@launch
                    val queryBefore = _state.value.mailboxQuery()
                    _state.update { it.copy(accounts = accounts, mailboxGroups = groups) }
                    accountIds = _state.value.mailboxScope.accountIds(groups)
                    if (_state.value.mailboxQuery() != queryBefore) {
                        cancelMailboxLoad()
                        _state.update { it.clearMailboxResults().copy(mailboxLoading = false) }
                    }
                }
                val threads = api.threads(accountIds)
                _state.update {
                    if (conversationsRequestVersion == requestVersion && it.user?.id == userId &&
                        it.mailboxScope == requestedScope && it.mailboxScope.accountIds(it.mailboxGroups) == accountIds)
                        it.copy(threads = threads, conversationsLoaded = true) else it
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (conversationsRequestVersion == requestVersion && _state.value.user?.id == userId) {
                    _state.update { it.copy(conversationsError = error.message ?: "Impossible de charger les conversations") }
                    handleActionError(error)
                }
            } finally {
                if (conversationsRequestVersion == requestVersion && _state.value.user?.id == userId)
                    _state.update { it.copy(conversationsLoading = false, conversationsRefreshing = false) }
            }
        }
    }

    fun changeCalendarMonth(month: YearMonth) {
        if (_state.value.calendarMonth == month) return
        _state.update {
            it.copy(calendarMonth = month, calendarDate = month.atDay(1),
                calendarEvents = emptyList(), calendarLoaded = false)
        }
        loadCalendar()
    }

    fun selectCalendarDate(date: LocalDate) {
        val month = YearMonth.from(date)
        if (_state.value.calendarMonth != month) changeCalendarMonth(month)
        _state.update { it.copy(calendarDate = date) }
    }

    fun calendarToday() {
        selectCalendarDate(LocalDate.now())
        if (!_state.value.calendarLoaded && !_state.value.calendarLoading) loadCalendar()
    }

    private fun loadCalendar(refreshing: Boolean = false) {
        val requestVersion = ++calendarRequestVersion
        calendarLoadJob?.cancel()
        val userId = _state.value.user?.id ?: return
        val month = _state.value.calendarMonth
        _state.update { it.copy(calendarLoading = !refreshing, calendarRefreshing = refreshing, calendarError = null) }
        calendarLoadJob = viewModelScope.launch {
            try {
                val events = api.calendar(CalendarRange.forMonth(month))
                _state.update {
                    if (calendarRequestVersion == requestVersion && it.user?.id == userId)
                        it.copy(calendarEvents = events, calendarLoaded = true) else it
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (calendarRequestVersion == requestVersion && _state.value.user?.id == userId) {
                    _state.update { it.copy(calendarError = error.message ?: "Impossible de charger le calendrier") }
                    handleActionError(error)
                }
            } finally {
                if (calendarRequestVersion == requestVersion && _state.value.user?.id == userId) {
                    _state.update { it.copy(calendarLoading = false, calendarRefreshing = false) }
                }
            }
        }
    }

    private fun loadGroups(refreshing: Boolean = false) {
        if (_state.value.groupsSaving) return
        val requestVersion = ++groupsRequestVersion
        groupsLoadJob?.cancel()
        val userId = _state.value.user?.id ?: return
        val navigationVersion = ++mailboxNavigationVersion
        _state.update { it.copy(groupsLoading = !refreshing, groupsRefreshing = refreshing, groupsLoadError = null) }
        groupsLoadJob = viewModelScope.launch {
            try {
                val (accounts, groups) = coroutineScope {
                    val accountTask = async { api.emailAccounts() }
                    val groupTask = async { api.mailboxGroups() }
                    accountTask.await() to groupTask.await()
                }
                val queryBefore = _state.value.mailboxQuery()
                _state.update {
                    if (groupsRequestVersion == requestVersion && it.user?.id == userId &&
                        navigationVersion == mailboxNavigationVersion)
                        it.copy(accounts = accounts, mailboxGroups = groups, groupsLoaded = true) else it
                }
                if (_state.value.mailboxQuery() != queryBefore) invalidateMailboxScope()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (groupsRequestVersion == requestVersion && _state.value.user?.id == userId) {
                    _state.update { it.copy(groupsLoadError = error.message ?: "Impossible de charger les groupes") }
                    handleActionError(error)
                }
            } finally {
                if (groupsRequestVersion == requestVersion && _state.value.user?.id == userId) {
                    _state.update { it.copy(groupsLoading = false, groupsRefreshing = false) }
                }
            }
        }
    }

    fun saveMailboxGroup(groupId: String?, draft: MailboxGroupDraft) = mutateGroup { userId, session ->
        val saved = api.saveMailboxGroup(groupId, draft)
        _state.update { current ->
            if (current.user?.id != userId || sessionVersion != session) current else current.copy(mailboxGroups = if (groupId == null) current.mailboxGroups + saved
                else current.mailboxGroups.map { if (it.id == groupId) saved else it }, message = "Groupe enregistré")
        }
    }

    fun deleteMailboxGroup(group: MailboxGroup) = mutateGroup { userId, session ->
        api.deleteMailboxGroup(group.id)
        _state.update {
            if (it.user?.id != userId || sessionVersion != session) it else it.copy(mailboxGroups = it.mailboxGroups.filterNot { value -> value.id == group.id },
                mailboxScope = if (it.mailboxScope == MailboxScope.Group(group.id)) MailboxScope.All else it.mailboxScope,
                message = "Groupe supprimé")
        }
    }

    private fun mutateGroup(block: suspend (Int, Long) -> Unit) {
        if (_state.value.groupsSaving || _state.value.groupsLoading || _state.value.groupsRefreshing) return
        val userId = _state.value.user?.id ?: return
        val session = sessionVersion
        ++groupsRequestVersion
        groupsLoadJob?.cancel()
        ++mailboxNavigationVersion
        _state.update { it.copy(groupsSaving = true, groupsActionError = null) }
        viewModelScope.launch {
            try {
                block(userId, session)
                if (_state.value.user?.id == userId && sessionVersion == session) {
                    _state.update { it.copy(groupsMutationVersion = it.groupsMutationVersion + 1) }
                    // Cached mail may no longer belong to an edited group's scope.
                    invalidateMailboxScope()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (_state.value.user?.id == userId && sessionVersion == session) {
                    _state.update { it.copy(groupsActionError = error.message ?: "Impossible d’enregistrer le groupe") }
                    handleActionError(error)
                }
            } finally {
                if (_state.value.user?.id == userId && sessionVersion == session) _state.update { it.copy(groupsSaving = false) }
            }
        }
    }

    private fun invalidateMailboxScope() {
        cancelMailboxLoad()
        invalidateConversationScope()
        _state.update { it.clearMailboxResults().copy(mailboxLoading = false) }
        if (_state.value.destination == Destination.MAILBOX) loadMailbox(reset = true)
        if (_state.value.destination == Destination.CONVERSATIONS) loadConversations()
    }

    private fun invalidateConversationScope() {
        ++conversationsRequestVersion
        conversationsLoadJob?.cancel()
        _state.update { it.copy(threads = emptyList(), conversationsLoaded = false,
            conversationsLoading = false, conversationsRefreshing = false, conversationsError = null) }
    }

    private fun invalidateSessionRequests(cancelJobs: Boolean = true) {
        sessionVersion++
        calendarRequestVersion++
        groupsRequestVersion++
        conversationsRequestVersion++
        mailboxNavigationVersion++
        emailSelectionVersion++
        mailboxRequestVersion++
        threadSelectionVersion++
        if (cancelJobs) {
            mailboxSearchJob?.cancel()
            mailboxLoadJob?.cancel()
            calendarLoadJob?.cancel()
            groupsLoadJob?.cancel()
            conversationsLoadJob?.cancel()
            contactSearchJob?.cancel()
            threadLoadJob?.cancel()
        }
    }

    fun searchMailbox(query: String) {
        if (_state.value.mailboxSearch == query) return
        cancelMailboxLoad()
        _state.update { it.copy(mailboxSearch = query).clearMailboxResults() }
        mailboxSearchJob?.cancel()
        mailboxSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            loadMailbox(reset = true)
        }
    }

    fun setMailboxStatus(status: String?) {
        if (_state.value.mailboxStatus == status) return
        _state.update { it.copy(mailboxStatus = status).clearMailboxResults() }
        mailboxSearchJob?.cancel()
        loadMailbox(reset = true)
    }

    fun setMailboxCategory(category: String?) {
        if (_state.value.mailboxCategory == category) return
        _state.update { it.copy(mailboxCategory = category).clearMailboxResults() }
        mailboxSearchJob?.cancel()
        loadMailbox(reset = true)
    }

    fun setMailboxScope(scope: MailboxScope) {
        if (_state.value.mailboxScope == scope) {
            navigate(Destination.MAILBOX)
            return
        }
        invalidateConversationScope()
        _state.update {
            it.copy(
                mailboxScope = scope,
                destination = Destination.MAILBOX,
                threads = emptyList(),
                conversationsLoaded = false,
                selectedThread = null,
                threadMessages = emptyList(),
            ).clearMailboxResults()
        }
        mailboxSearchJob?.cancel()
        loadMailbox(reset = true)
    }

    fun loadMoreEmails() {
        val current = _state.value
        if (!current.mailboxHasMore || current.mailboxLoading || current.mailboxRefreshing || current.loadingMore) return
        loadMailbox(reset = false)
    }

    fun selectEmail(email: MailboxEmail?) {
        emailSelectionVersion++
        _state.update { it.copy(selectedEmail = email) }
        if (email?.status == "UNREAD") updateEmailStatus(email, "READ", closeAfter = false)
    }

    fun updateEmailStatus(email: MailboxEmail, status: String, closeAfter: Boolean = true) = launchAction {
        val session = sessionVersion
        api.updateEmailStatus(email.statusUpdateIds, status)
        if (sessionVersion != session) return@launchAction
        val current = _state.value
        val updated = current.applyEmailStatus(email.statusUpdateIds, status, closeAfter)
        _state.value = updated
        if (updated.emails.size < current.emails.size ||
            mailboxLoadJob?.isActive == true && email.status != status
        ) {
            mailboxSearchJob?.cancel()
            loadMailbox(reset = true, refreshing = true, refreshNavigation = false)
        }
    }

    fun selectThread(thread: MailThread?) {
        val selection = ++threadSelectionVersion
        threadLoadJob?.cancel()
        _state.update { it.copy(selectedThread = thread, threadMessages = emptyList(),
            threadMessagesLoading = thread != null, threadMessagesError = null) }
        if (thread == null) return
        val session = sessionVersion
        val userId = _state.value.user?.id
        threadLoadJob = viewModelScope.launch {
            try {
                val messages = api.threadMessages(thread.id, _state.value.emails)
                if (sessionVersion != session || _state.value.user?.id != userId || selection != threadSelectionVersion) return@launch
                if (thread.hasUnread) api.markThreadRead(thread.id, unread = false)
                _state.update { current ->
                    if (sessionVersion != session || selection != threadSelectionVersion) current else current.copy(
                        threadMessages = if (current.selectedThread?.id == thread.id) messages else current.threadMessages,
                        threads = current.threads.map { if (it.id == thread.id) it.copy(hasUnread = false) else it },
                        selectedThread = current.selectedThread?.let { if (it.id == thread.id) it.copy(hasUnread = false) else it },
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (sessionVersion == session && selection == threadSelectionVersion) {
                    _state.update { it.copy(threadMessagesError = error.message ?: "Impossible de charger les messages") }
                    handleActionError(error)
                }
            } finally {
                if (sessionVersion == session && selection == threadSelectionVersion)
                    _state.update { it.copy(threadMessagesLoading = false) }
            }
        }
    }

    fun toggleThreadStar(thread: MailThread) = launchAction {
        val session = sessionVersion
        api.starThread(thread.id, !thread.isStarred)
        if (sessionVersion != session) return@launchAction
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
        val session = sessionVersion
        api.archiveThread(thread.id)
        if (sessionVersion != session) return@launchAction
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
        val session = sessionVersion
        _state.update { it.copy(contactSearch = query) }
        contactSearchJob?.cancel()
        contactSearchJob = launchAction {
            delay(SEARCH_DEBOUNCE_MILLIS)
            val contacts = api.contacts(query)
            if (_state.value.contactSearch == query && sessionVersion == session) {
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
            ?: _state.value.defaultSendingAccountId()
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
            invalidateSessionRequests()
            mailboxSearchJob?.cancel()
            cancelMailboxLoad()
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
                val selectionVersion = emailSelectionVersion
                val userId = _state.value.user?.id
                val session = sessionVersion
                launchAction {
                    val email = api.email(emailId)
                    if (sessionVersion == session && emailSelectionVersion == selectionVersion && _state.value.user?.id == userId && _state.value.destination == Destination.MAILBOX) {
                        selectEmail(email.withDuplicateMetadata(_state.value.emails))
                    }
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
        invalidateSessionRequests()
        mailboxSearchJob?.cancel()
        cancelMailboxLoad()
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
        val userId = _state.value.user?.id
        val navigationVersion = ++mailboxNavigationVersion
        val mailboxTask = loadMailbox(reset = true)
        suspend fun <T> load(fetch: suspend () -> T, apply: (PhacteurUiState, T) -> PhacteurUiState) {
            try {
                val result = fetch()
                _state.update { if (it.user?.id == userId) apply(it, result) else it }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (_state.value.user?.id == userId) handleActionError(error)
            }
        }
        listOf(
            async { load(api::dashboard) { state, value -> state.copy(dashboard = value) } },
            async { load({ api.contacts() }) { state, value -> state.copy(contacts = value) } },
            async { load(api::emailAccounts) { state, value ->
                if (navigationVersion == mailboxNavigationVersion) state.copy(accounts = value) else state
            } },
            async { load(api::mailboxGroups) { state, value ->
                if (navigationVersion == mailboxNavigationVersion) state.copy(mailboxGroups = value) else state
            } },
            async { load(api::passkeys) { state, value -> state.copy(passkeys = value) } },
        ).awaitAll()
        mailboxTask.join()
        _state.update { it.copy(loading = false) }
    }

    private fun refreshDestination(destination: Destination, force: Boolean = false) {
        val session = sessionVersion
        when (destination) {
            Destination.DASHBOARD -> if (force || _state.value.dashboard == null) launchAction(refreshing = true) {
                val dashboard = api.dashboard()
                _state.update { if (sessionVersion == session) it.copy(dashboard = dashboard) else it }
            }
            Destination.MAILBOX -> if (force || _state.value.emails.isEmpty() &&
                !_state.value.mailboxLoading && !_state.value.mailboxRefreshing && !_state.value.loadingMore
            ) {
                mailboxSearchJob?.cancel()
                loadMailbox(reset = true, refreshing = force)
            }
            Destination.CONVERSATIONS -> if (force || !_state.value.conversationsLoaded && !_state.value.conversationsLoading) loadConversations(force)
            Destination.CALENDAR -> if (force || !_state.value.calendarLoaded && !_state.value.calendarLoading) loadCalendar(force)
            Destination.GROUPS -> if (force || !_state.value.groupsLoaded && !_state.value.groupsLoading) loadGroups(force)
            Destination.CONTACTS -> if (force || _state.value.contacts.isEmpty()) launchAction(refreshing = true) {
                contactSearchJob?.cancel()
                val query = _state.value.contactSearch
                val contacts = api.contacts(query)
                _state.update { if (sessionVersion == session && it.contactSearch == query) it.copy(contacts = contacts) else it }
            }
            Destination.SETTINGS -> launchAction(refreshing = true) {
                val (accounts, passkeys) = coroutineScope {
                    async { api.emailAccounts() } to async { api.passkeys() }
                }.let { (accountsTask, passkeysTask) -> accountsTask.await() to passkeysTask.await() }
                _state.update { if (sessionVersion == session) it.copy(accounts = accounts, passkeys = passkeys) else it }
            }
        }
    }

    private fun cancelMailboxLoad() {
        mailboxRequestVersion += 1
        mailboxLoadJob?.cancel()
        mailboxLoadJob = null
    }

    private fun loadMailbox(
        reset: Boolean,
        refreshing: Boolean = false,
        refreshNavigation: Boolean = refreshing,
    ): Job {
        cancelMailboxLoad()
        val requestVersion = mailboxRequestVersion
        val current = _state.value
        val requestedScope = current.mailboxScope
        var requestedQuery = current.mailboxQuery()
        val targetPage = if (reset) 1 else current.mailboxPage + 1
        _state.update {
            it.copy(
                mailboxLoading = reset && !refreshing,
                mailboxRefreshing = refreshing,
                loadingMore = !reset,
                mailboxError = null,
                error = null,
            )
        }
        return viewModelScope.launch {
            try {
                if (refreshNavigation) {
                    val navigationVersion = ++mailboxNavigationVersion
                    // Refresh identities too: groups can be edited on the website.
                    // A navigation failure must not hide otherwise available mail.
                    try {
                        val (accounts, groups) = coroutineScope {
                            val accountsTask = async { api.emailAccounts() }
                            val groupsTask = async { api.mailboxGroups() }
                            accountsTask.await() to groupsTask.await()
                        }
                        if (requestVersion != mailboxRequestVersion || navigationVersion != mailboxNavigationVersion) return@launch
                        val previousQuery = _state.value.mailboxQuery()
                        _state.update { it.copy(accounts = accounts, mailboxGroups = groups) }
                        requestedQuery = _state.value.mailboxQuery()
                        if (requestedQuery != previousQuery) invalidateConversationScope()
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        if (error is ApiException && error.statusCode == 401) throw error
                        if (requestVersion == mailboxRequestVersion) {
                            _state.update { it.copy(error = "Impossible d’actualiser les boîtes et groupes. Réessayez.") }
                        }
                    }
                }
                val page = api.emails(
                    status = requestedQuery.status,
                    search = requestedQuery.search,
                    page = targetPage,
                    accountIds = requestedQuery.accountIds,
                    category = requestedQuery.category,
                )
                val latest = _state.value
                if (requestVersion == mailboxRequestVersion && latest.mailboxScope == requestedScope &&
                    latest.mailboxQuery() == requestedQuery
                ) {
                    applyMailboxPage(page, reset)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (requestVersion == mailboxRequestVersion) {
                    _state.update {
                        it.copy(mailboxError = error.message?.takeIf(String::isNotBlank)
                            ?: "Impossible de charger les messages. Réessayez.")
                    }
                    handleActionError(error)
                }
            } finally {
                // A cancelled older request cannot dismiss a newer request's spinner.
                if (requestVersion == mailboxRequestVersion) {
                    _state.update { it.copy(mailboxLoading = false, mailboxRefreshing = false, loadingMore = false) }
                }
            }
        }.also { mailboxLoadJob = it }
    }

    private fun applyMailboxPage(page: MailboxPage, reset: Boolean) {
        _state.update {
            it.copy(
                emails = if (reset) page.emails else (it.emails + page.emails).distinctBy(MailboxEmail::id),
                mailboxPage = page.page,
                mailboxHasMore = page.hasMore,
                mailboxTotalCount = page.totalCount,
                mailboxSearchLimited = page.searchLimited,
            )
        }
    }

    private fun launchAction(
        authenticating: Boolean = false,
        refreshing: Boolean = false,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        val session = sessionVersion
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
            if (sessionVersion == session) handleActionError(error)
        } finally {
            _state.update {
                if (sessionVersion != session) it else it.copy(
                    authenticating = if (authenticating) false else it.authenticating,
                    loading = if (!authenticating && !refreshing) false else it.loading,
                    refreshing = false,
                )
            }
        }
    }

    private suspend fun handleActionError(error: Throwable) {
        if (error is ApiException && error.statusCode == 401 && _state.value.user != null) {
            invalidateSessionRequests(cancelJobs = false)
            mailboxSearchJob?.cancel()
            // Invalidate every mailbox response without cancelling this error handler.
            mailboxRequestVersion += 1
            graph.cookieStore.clear()
            _state.value = signedOutState("Votre session a expiré")
            PushRegistrationManager.disable(getApplication())
            _state.update { it.copy(notificationsEnabled = notificationPreferences.enabled) }
        } else {
            _state.update {
                it.copy(error = error.message?.takeIf(String::isNotBlank) ?: "Une erreur est survenue")
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
