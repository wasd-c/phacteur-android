package app.phacteur.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.MailThread
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.data.ThreadMessage
import app.phacteur.android.ui.components.EmailBody
import app.phacteur.android.ui.components.emailPreview
import app.phacteur.android.ui.components.EmptyPane
import app.phacteur.android.ui.components.SenderAvatar
import app.phacteur.android.ui.components.formatTimestamp
import app.phacteur.android.ui.components.MailboxScopeSelector
import app.phacteur.android.ui.components.EmailQuickActionBar
import app.phacteur.android.ui.components.EmailMetadataPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    threads: List<MailThread>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    selectedThread: MailThread?,
    messages: List<ThreadMessage>,
    wide: Boolean,
    onSelect: (MailThread?) -> Unit,
    onToggleStar: (MailThread) -> Unit,
    onArchive: (MailThread) -> Unit,
    onReply: (MailThread) -> Unit,
    messagesLoading: Boolean = false,
    messagesError: String? = null,
    accounts: List<EmailAccount> = emptyList(),
    groups: List<MailboxGroup> = emptyList(),
    scope: MailboxScope = MailboxScope.All,
    loading: Boolean = false,
    loadError: String? = null,
    onScopeChange: (MailboxScope) -> Unit = {},
    modifier: Modifier = Modifier,
    onCreateGroup: () -> Unit = {},
) {
    if (wide) {
        Row(modifier.fillMaxSize()) {
            ConversationListPane(threads, selectedThread, accounts, groups, scope, loading, refreshing,
                loadError, onScopeChange, onRefresh, onSelect, Modifier.weight(0.4f).fillMaxHeight(), onCreateGroup)
            VerticalDivider()
            if (selectedThread == null) {
                EmptyPane("Sélectionnez une conversation", "Tous les messages apparaîtront ici.", Modifier.weight(0.6f))
            } else {
                ThreadDetail(
                    thread = selectedThread,
                    messages = messages,
                    loading = messagesLoading,
                    error = messagesError,
                    onRetry = { onSelect(selectedThread) },
                    showBack = false,
                    onBack = { onSelect(null) },
                    onToggleStar = onToggleStar,
                    onArchive = onArchive,
                    onReply = onReply,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
    } else if (selectedThread != null) {
        ThreadDetail(
            thread = selectedThread,
            messages = messages,
            loading = messagesLoading,
            error = messagesError,
            onRetry = { onSelect(selectedThread) },
            showBack = true,
            onBack = { onSelect(null) },
            onToggleStar = onToggleStar,
            onArchive = onArchive,
            onReply = onReply,
            modifier = modifier,
        )
    } else {
        ConversationListPane(threads, null, accounts, groups, scope, loading, refreshing,
            loadError, onScopeChange, onRefresh, onSelect, modifier, onCreateGroup)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListPane(
    threads: List<MailThread>, selected: MailThread?, accounts: List<EmailAccount>,
    groups: List<MailboxGroup>, scope: MailboxScope, loading: Boolean, refreshing: Boolean,
    error: String?, onScopeChange: (MailboxScope) -> Unit, onRefresh: () -> Unit,
    onSelect: (MailThread) -> Unit, modifier: Modifier,
    onCreateGroup: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        MailboxScopeSelector(scope, accounts, groups, onScopeChange, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            onCreateGroup = onCreateGroup)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        PullToRefreshBox(refreshing, { if (!loading && !refreshing) onRefresh() }, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                if (error != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRefresh, enabled = !loading && !refreshing) { Text("Réessayer") }
                    }
                }
                if (loading && threads.isEmpty()) {
                    app.phacteur.android.ui.components.LoadingPane(Modifier.weight(1f))
                } else {
                    ThreadList(threads, selected, onSelect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThreadList(
    threads: List<MailThread>,
    selectedThread: MailThread?,
    onSelect: (MailThread) -> Unit,
    modifier: Modifier,
) {
    if (threads.isEmpty()) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EmptyPane("Aucune conversation", "Vos fils de discussion apparaîtront ici. Glissez vers le bas pour actualiser.")
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(threads, key = MailThread::id) { thread ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(thread) },
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        selectedThread?.id == thread.id -> MaterialTheme.colorScheme.primaryContainer
                        thread.hasUnread -> MaterialTheme.colorScheme.surfaceContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    SenderAvatar(thread.participants.firstOrNull() ?: thread.subject)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                thread.subject,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (thread.hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (thread.isStarred) {
                                Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = "Favori",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            remember(thread.snippet) { emailPreview(thread.snippet) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${thread.messageCount} message${if (thread.messageCount > 1) "s" else ""} · ${formatTimestamp(thread.lastMessageAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadDetail(
    thread: MailThread,
    messages: List<ThreadMessage>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    onToggleStar: (MailThread) -> Unit,
    onArchive: (MailThread) -> Unit,
    onReply: (MailThread) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                }
            }
            Text(
                thread.subject,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { onToggleStar(thread) }) {
                Icon(
                    if (thread.isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (thread.isStarred) "Retirer des favoris" else "Ajouter aux favoris",
                )
            }
            IconButton(onClick = { onArchive(thread) }) {
                Icon(Icons.Outlined.Archive, contentDescription = "Archiver")
            }
        }
        if (error != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !loading) { Text("Réessayer") }
            }
        }
        if (messages.isEmpty() && loading) {
            app.phacteur.android.ui.components.LoadingPane(Modifier.weight(1f))
        } else if (messages.isEmpty()) {
            EmptyPane(if (error == null) "Aucun message" else "Messages indisponibles",
                if (error == null) "Cette conversation ne contient aucun message." else "Réessayez pour récupérer la conversation.", Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = ThreadMessage::id) { message ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SenderAvatar(message.displaySender)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(message.displaySender, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        formatTimestamp(message.receivedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 14.dp))
                            EmailMetadataPanel((message.metadata ?: app.phacteur.android.data.EmailMetadata()).copy(
                                sender = message.sender, senderName = message.senderName,
                                receivedAt = message.metadata?.receivedAt ?: message.receivedAt,
                                direction = message.direction.takeIf { it != "UNKNOWN" } ?: message.metadata?.direction,
                            ), messageId = message.id)
                            EmailQuickActionBar(message.subject, message.body, message.htmlBody,
                                receivedAt = message.receivedAt, direction = message.direction)
                            EmailBody(messageId = message.id, body = message.body, htmlBody = message.htmlBody)
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onReply(thread) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Répondre à la conversation")
        }
    }
}
