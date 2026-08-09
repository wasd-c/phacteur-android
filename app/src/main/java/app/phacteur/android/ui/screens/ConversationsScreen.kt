package app.phacteur.android.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.MailThread
import app.phacteur.android.data.ThreadMessage
import app.phacteur.android.ui.components.EmptyPane
import app.phacteur.android.ui.components.SenderAvatar
import app.phacteur.android.ui.components.formatTimestamp

@Composable
fun ConversationsScreen(
    threads: List<MailThread>,
    selectedThread: MailThread?,
    messages: List<ThreadMessage>,
    wide: Boolean,
    onSelect: (MailThread?) -> Unit,
    onToggleStar: (MailThread) -> Unit,
    onArchive: (MailThread) -> Unit,
    onReply: (MailThread) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        Row(modifier.fillMaxSize()) {
            ThreadList(threads, selectedThread, onSelect, Modifier.weight(0.4f).fillMaxHeight())
            HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
            if (selectedThread == null) {
                EmptyPane("Sélectionnez une conversation", "Tous les messages apparaîtront ici.", Modifier.weight(0.6f))
            } else {
                ThreadDetail(
                    thread = selectedThread,
                    messages = messages,
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
            showBack = true,
            onBack = { onSelect(null) },
            onToggleStar = onToggleStar,
            onArchive = onArchive,
            onReply = onReply,
            modifier = modifier,
        )
    } else {
        ThreadList(threads, null, onSelect, modifier)
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
        EmptyPane("Aucune conversation", "Vos fils de discussion apparaîtront ici.", modifier)
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
                            thread.snippet.replace(Regex("\\s+"), " "),
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
        if (messages.isEmpty()) {
            EmptyPane("Chargement des messages", "La conversation est en cours de récupération.", Modifier.weight(1f))
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
                            Text(message.body.ifBlank { "Message sans contenu texte." })
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
