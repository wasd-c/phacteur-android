package app.phacteur.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.phacteur.android.BuildConfig
import app.phacteur.android.notifications.NotificationHelper
import app.phacteur.android.notifications.NotificationPreferences
import app.phacteur.android.ui.screens.AuthScreen
import app.phacteur.android.ui.screens.ComposeSheet
import app.phacteur.android.ui.screens.ContactsScreen
import app.phacteur.android.ui.screens.ConversationsScreen
import app.phacteur.android.ui.screens.DashboardScreen
import app.phacteur.android.ui.screens.MailboxScreen
import app.phacteur.android.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private data class NavigationItem(
    val destination: Destination,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val navigationItems = listOf(
    NavigationItem(Destination.DASHBOARD, "Accueil", Icons.Outlined.Dashboard),
    NavigationItem(Destination.MAILBOX, "Emails", Icons.Outlined.Inbox),
    NavigationItem(Destination.CONVERSATIONS, "Fils", Icons.Outlined.Forum),
    NavigationItem(Destination.CONTACTS, "Contacts", Icons.Outlined.Contacts),
    NavigationItem(Destination.SETTINGS, "Réglages", Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhacteurApp(
    activity: ComponentActivity,
    viewModel: PhacteurViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notificationPreferences = remember(context) { NotificationPreferences(context) }
    var showNotificationPrompt by rememberSaveable {
        mutableStateOf(!notificationPreferences.promptHandled && !notificationPreferences.enabled)
    }
    var notificationsAllowed by remember { mutableStateOf(NotificationHelper.canShowNotifications(context)) }
    val openNotificationSettings = {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = NotificationHelper.canShowNotifications(context)
        if (granted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            scope.launch {
                snackbar.showSnackbar("Notifications refusées. Vous pouvez les autoriser dans les réglages Android.")
            }
        }
    }
    val enableNotifications = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsEnabled(true)
            if (!NotificationHelper.canShowNotifications(context)) openNotificationSettings()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationHelper.canShowNotifications(context)
        viewModel.refreshNotificationDelivery()
    }

    LaunchedEffect(state.user, state.error, state.message) {
        // The login screen displays persistent errors and has no SnackbarHost.
        if (state.user == null) return@LaunchedEffect
        (state.error ?: state.message)?.let { snackbar.showSnackbar(it) }
        if (state.error != null || state.message != null) viewModel.clearTransientMessage()
    }

    if (state.user == null) {
        AuthScreen(
            loading = state.authenticating,
            error = state.error,
            canRetrySession = state.canRetrySession,
            preferBrowserSignIn = state.preferBrowserSignIn,
            onPasskey = { viewModel.signInWithPasskey(activity) },
            onRetrySession = viewModel::retrySession,
            onBrowserSignIn = {
                context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.browserAuthorizationUri()))
            },
            modifier = modifier,
        )
        return
    }

    if (showNotificationPrompt) {
        val dismissPrompt = {
            notificationPreferences.promptHandled = true
            showNotificationPrompt = false
        }
        AlertDialog(
            onDismissRequest = dismissPrompt,
            title = { Text("Recevoir les nouveaux emails") },
            text = {
                Text(
                    if (state.firebaseConfigured) {
                        "Activez les notifications pour être prévenu lorsqu’un email arrive, même lorsque Phacteur est fermé. Le contenu du message reste privé."
                    } else {
                        "Phacteur peut vérifier vos nouveaux emails en arrière-plan et vous prévenir, avec un délai d’environ 15 minutes ou plus selon Android. Le contenu du message reste privé."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dismissPrompt()
                    enableNotifications()
                }) { Text("Activer") }
            },
            dismissButton = {
                TextButton(onClick = dismissPrompt) { Text("Plus tard") }
            },
        )
    }

    BackHandler(enabled = state.composeDraft != null) { viewModel.closeCompose() }
    BackHandler(enabled = state.composeDraft == null && state.selectedEmail != null) { viewModel.selectEmail(null) }
    BackHandler(enabled = state.composeDraft == null && state.selectedThread != null) { viewModel.selectThread(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                when (state.destination) {
                                    Destination.DASHBOARD -> "Vue d’ensemble"
                                    Destination.MAILBOX -> "Boîte de réception"
                                    Destination.CONVERSATIONS -> "Conversations"
                                    Destination.CONTACTS -> "Contacts"
                                    Destination.SETTINGS -> "Réglages"
                                },
                            )
                        },
                        actions = {
                            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser")
                            }
                        },
                    )
                    if (state.loading || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        navigationItems.forEach { item ->
                            NavigationBarItem(
                                selected = state.destination == item.destination,
                                onClick = { viewModel.navigate(item.destination) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (state.destination != Destination.SETTINGS) {
                    FloatingActionButton(onClick = { viewModel.openCompose() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nouveau message")
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        navigationItems.forEach { item ->
                            NavigationRailItem(
                                selected = state.destination == item.destination,
                                onClick = { viewModel.navigate(item.destination) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (state.destination) {
                        Destination.DASHBOARD -> DashboardScreen(
                            dashboard = state.dashboard,
                            userName = state.user!!.displayName,
                            onEmailClick = { preview ->
                                viewModel.navigate(Destination.MAILBOX)
                                viewModel.selectEmail(state.emails.firstOrNull { it.id == preview.id } ?: preview)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Destination.MAILBOX -> MailboxScreen(
                            emails = state.emails,
                            selectedEmail = state.selectedEmail,
                            search = state.mailboxSearch,
                            status = state.mailboxStatus,
                            hasMore = state.mailboxHasMore,
                            wide = wide,
                            onSearch = viewModel::searchMailbox,
                            onStatus = viewModel::setMailboxStatus,
                            onSelect = viewModel::selectEmail,
                            onLoadMore = viewModel::loadMoreEmails,
                            onStatusUpdate = { email, status -> viewModel.updateEmailStatus(email, status) },
                            onReply = { email ->
                                viewModel.openCompose(
                                    recipients = email.sender,
                                    subject = if (email.subject.startsWith("Re:", true)) email.subject else "Re: ${email.subject}",
                                    threadId = email.threadId,
                                    emailAccountId = email.emailAccountId,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Destination.CONVERSATIONS -> ConversationsScreen(
                            threads = state.threads,
                            selectedThread = state.selectedThread,
                            messages = state.threadMessages,
                            wide = wide,
                            onSelect = viewModel::selectThread,
                            onToggleStar = viewModel::toggleThreadStar,
                            onArchive = viewModel::archiveThread,
                            onReply = { thread ->
                                val recipient = thread.participants.firstOrNull { it != state.user!!.email }.orEmpty()
                                viewModel.openCompose(
                                    recipients = recipient,
                                    subject = if (thread.subject.startsWith("Re:", true)) thread.subject else "Re: ${thread.subject}",
                                    threadId = thread.id,
                                    emailAccountId = state.threadMessages.lastOrNull()?.emailAccountId,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Destination.CONTACTS -> ContactsScreen(
                            contacts = state.contacts,
                            search = state.contactSearch,
                            onSearch = viewModel::searchContacts,
                            onCompose = { viewModel.openCompose(recipients = it.email.orEmpty()) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Destination.SETTINGS -> SettingsScreen(
                            user = state.user!!,
                            accounts = state.accounts,
                            passkeys = state.passkeys,
                            notificationsEnabled = state.notificationsEnabled,
                            firebaseConfigured = state.firebaseConfigured,
                            notificationsAllowed = notificationsAllowed,
                            onOpenNotificationSettings = openNotificationSettings,
                            onNotificationChange = { enabled ->
                                if (enabled) {
                                    enableNotifications()
                                } else {
                                    viewModel.setNotificationsEnabled(false)
                                }
                            },
                            onAddPasskey = { viewModel.registerPasskey(activity) },
                            onOpenWeb = { path ->
                                val uri = Uri.parse(BuildConfig.PHACTEUR_BASE_URL).buildUpon()
                                    .appendEncodedPath(path)
                                    .build()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            onLogout = viewModel::logout,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    state.composeDraft?.let { draft ->
        ComposeSheet(
            draft = draft,
            accounts = state.accounts.filter { it.isActive && it.canSend },
            sending = state.loading,
            onDraftChange = viewModel::updateComposeDraft,
            onDismiss = viewModel::closeCompose,
            onSend = viewModel::sendDraft,
        )
    }
}
