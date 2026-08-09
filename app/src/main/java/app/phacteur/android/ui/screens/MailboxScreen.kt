package app.phacteur.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.ui.components.EmptyPane
import app.phacteur.android.ui.components.SenderAvatar
import app.phacteur.android.ui.components.TitleAndMeta
import app.phacteur.android.ui.components.compactFileSize
import app.phacteur.android.ui.components.formatTimestamp

@Composable
fun MailboxScreen(
    emails: List<MailboxEmail>,
    selectedEmail: MailboxEmail?,
    search: String,
    status: String?,
    hasMore: Boolean,
    wide: Boolean,
    onSearch: (String) -> Unit,
    onStatus: (String?) -> Unit,
    onSelect: (MailboxEmail?) -> Unit,
    onLoadMore: () -> Unit,
    onStatusUpdate: (MailboxEmail, String) -> Unit,
    onReply: (MailboxEmail) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        Row(modifier.fillMaxSize()) {
            EmailList(
                emails = emails,
                selectedEmail = selectedEmail,
                search = search,
                status = status,
                hasMore = hasMore,
                onSearch = onSearch,
                onStatus = onStatus,
                onSelect = onSelect,
                onLoadMore = onLoadMore,
                modifier = Modifier.weight(0.44f).fillMaxHeight(),
            )
            HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
            if (selectedEmail == null) {
                EmptyPane(
                    "Sélectionnez un email",
                    "Le contenu s’affichera ici sans charger de ressources distantes.",
                    Modifier.weight(0.56f),
                )
            } else {
                EmailDetail(
                    email = selectedEmail,
                    showBack = false,
                    onBack = { onSelect(null) },
                    onStatusUpdate = onStatusUpdate,
                    onReply = onReply,
                    modifier = Modifier.weight(0.56f),
                )
            }
        }
    } else if (selectedEmail != null) {
        EmailDetail(
            email = selectedEmail,
            showBack = true,
            onBack = { onSelect(null) },
            onStatusUpdate = onStatusUpdate,
            onReply = onReply,
            modifier = modifier,
        )
    } else {
        EmailList(
            emails = emails,
            selectedEmail = null,
            search = search,
            status = status,
            hasMore = hasMore,
            onSearch = onSearch,
            onStatus = onStatus,
            onSelect = onSelect,
            onLoadMore = onLoadMore,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmailList(
    emails: List<MailboxEmail>,
    selectedEmail: MailboxEmail?,
    search: String,
    status: String?,
    hasMore: Boolean,
    onSearch: (String) -> Unit,
    onStatus: (String?) -> Unit,
    onSelect: (MailboxEmail) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Rechercher dans les emails") },
            shape = RoundedCornerShape(18.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = status == null, onClick = { onStatus(null) }, label = { Text("Tous") })
            FilterChip(selected = status == "UNREAD", onClick = { onStatus("UNREAD") }, label = { Text("Non lus") })
            FilterChip(selected = status == "ARCHIVED", onClick = { onStatus("ARCHIVED") }, label = { Text("Archivés") })
        }
        if (emails.isEmpty()) {
            EmptyPane(
                title = if (search.isBlank()) "Boîte de réception vide" else "Aucun résultat",
                description = if (search.isBlank()) "Les nouveaux messages apparaîtront ici." else "Essayez une autre recherche.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(emails, key = MailboxEmail::id) { email ->
                    val selected = selectedEmail?.id == email.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(email) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else if (email.status == "UNREAD") MaterialTheme.colorScheme.surfaceContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            SenderAvatar(email.displaySender)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                TitleAndMeta(
                                    title = email.displaySender,
                                    meta = formatTimestamp(email.receivedAt),
                                    unread = email.status == "UNREAD",
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    email.subject,
                                    fontWeight = if (email.status == "UNREAD") FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    email.body.replace(Regex("\\s+"), " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (email.hasAttachments) {
                                Icon(
                                    Icons.Outlined.AttachFile,
                                    contentDescription = "Pièces jointes",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (hasMore) {
                    item {
                        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Charger la suite")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailDetail(
    email: MailboxEmail,
    showBack: Boolean,
    onBack: () -> Unit,
    onStatusUpdate: (MailboxEmail, String) -> Unit,
    onReply: (MailboxEmail) -> Unit,
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
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onStatusUpdate(email, if (email.status == "UNREAD") "READ" else "UNREAD") }) {
                Icon(
                    if (email.status == "UNREAD") Icons.Outlined.Drafts else Icons.Outlined.MailOutline,
                    contentDescription = if (email.status == "UNREAD") "Marquer comme lu" else "Marquer comme non lu",
                )
            }
            IconButton(onClick = { onStatusUpdate(email, "ARCHIVED") }) {
                Icon(Icons.Outlined.Archive, contentDescription = "Archiver")
            }
            IconButton(onClick = { onStatusUpdate(email, "DELETED") }) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Supprimer")
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text(email.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(email.displaySender)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(email.displaySender, fontWeight = FontWeight.SemiBold)
                    Text(email.sender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatTimestamp(email.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Text(
                email.body.ifBlank { "Ce message ne contient pas de version texte." },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (email.attachments.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Pièces jointes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                email.attachments.forEach { attachment ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("${attachment.filename} · ${compactFileSize(attachment.fileSize)}") },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                    )
                }
                Text(
                    "Le téléchargement authentifié sera proposé dans une prochaine version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = { onReply(email) }) {
                Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Répondre")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
