package app.phacteur.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.ui.components.*
import app.phacteur.android.ui.theme.mailboxIdentityColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MailboxScreen(
    emails: List<MailboxEmail>,
    selectedEmail: MailboxEmail?,
    accounts: List<EmailAccount>,
    groups: List<MailboxGroup>,
    scope: MailboxScope,
    search: String,
    status: String?,
    category: String?,
    hasMore: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    totalCount: Int,
    searchLimited: Boolean,
    loadError: String?,
    wide: Boolean,
    onScopeChange: (MailboxScope) -> Unit,
    onSearch: (String) -> Unit,
    onStatus: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (MailboxEmail?) -> Unit,
    onLoadMore: () -> Unit,
    onStatusUpdate: (MailboxEmail, String) -> Unit,
    onReply: (MailboxEmail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list: @Composable (Modifier) -> Unit = { listModifier ->
        EmailList(
            emails, selectedEmail, accounts, groups, scope, search, status, category,
            hasMore, loading, refreshing, loadingMore, totalCount, searchLimited, loadError,
            onScopeChange, onSearch, onStatus, onCategory, onRefresh, onSelect, onLoadMore,
            listModifier,
        )
    }
    if (wide) {
        Row(modifier.fillMaxSize()) {
            list(Modifier.weight(0.44f).fillMaxHeight())
            VerticalDivider()
            if (selectedEmail == null) {
                EmptyPane(
                    "Votre courrier, au même endroit",
                    "Choisissez un message pour le lire et y répondre.",
                    Modifier.weight(0.56f),
                )
            } else {
                EmailDetail(
                    email = selectedEmail,
                    account = accounts.firstOrNull { it.id == selectedEmail.emailAccountId },
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
            account = accounts.firstOrNull { it.id == selectedEmail.emailAccountId },
            showBack = true,
            onBack = { onSelect(null) },
            onStatusUpdate = onStatusUpdate,
            onReply = onReply,
            modifier = modifier,
        )
    } else {
        list(modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailList(
    emails: List<MailboxEmail>,
    selectedEmail: MailboxEmail?,
    accounts: List<EmailAccount>,
    groups: List<MailboxGroup>,
    scope: MailboxScope,
    search: String,
    status: String?,
    category: String?,
    hasMore: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    loadingMore: Boolean,
    totalCount: Int,
    searchLimited: Boolean,
    loadError: String?,
    onScopeChange: (MailboxScope) -> Unit,
    onSearch: (String) -> Unit,
    onStatus: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (MailboxEmail) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var filtersExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(scope, search, status, category) { listState.scrollToItem(0) }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        MailboxScopeSelector(
            scope, accounts, groups, onScopeChange,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (search.isNotEmpty()) {
                { IconButton(onClick = { onSearch("") }) { Icon(Icons.Outlined.Close, "Effacer la recherche") } }
            } else null,
            placeholder = { Text("Rechercher dans cette sélection", style = MaterialTheme.typography.bodyMedium) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(category == null, { onCategory(null) }, { Text("Tous") }, leadingIcon = { Icon(Icons.Outlined.Inbox, null, Modifier.size(16.dp)) }) }
            item { FilterChip(category == "important", { onCategory("important") }, { Text("Important") }, leadingIcon = { Icon(Icons.Outlined.StarBorder, null, Modifier.size(16.dp)) }) }
            item { FilterChip(category == "newsletter", { onCategory("newsletter") }, { Text("Newsletters") }, leadingIcon = { Icon(Icons.Outlined.Newspaper, null, Modifier.size(16.dp)) }) }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    loading -> "Chargement des messages…"
                    refreshing -> "Actualisation…"
                    else -> "$totalCount message${if (totalCount > 1) "s" else ""}"
                },
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                TextButton(onClick = { filtersExpanded = true }) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(when (status) {
                        "UNREAD" -> "Non lus"
                        "READ" -> "Lus"
                        "ARCHIVED" -> "Archivés"
                        "DELETED" -> "Corbeille"
                        else -> "Réception"
                    })
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                }
                DropdownMenu(filtersExpanded, onDismissRequest = { filtersExpanded = false }) {
                    listOf(null to "Réception", "UNREAD" to "Non lus", "READ" to "Lus", "ARCHIVED" to "Archivés", "DELETED" to "Corbeille").forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { filtersExpanded = false; onStatus(value) },
                            trailingIcon = { if (status == value) Icon(Icons.Outlined.Check, "Sélectionné") },
                        )
                    }
                }
            }
        }
        if (searchLimited) {
            Text(
                "La recherche couvre jusqu’à 1 000 messages. Précisez une boîte, un expéditeur ou une date.",
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        // Always keep a scrollable child, including empty/error states, so a downward
        // swipe can refresh a mailbox with no messages or retry a failed request.
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { if (!loading && !refreshing) onRefresh() },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = if (emails.isEmpty()) 0.dp else 88.dp),
            ) {
                if (loadError != null) {
                    item(key = "error") {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Impossible d’actualiser les messages", fontWeight = FontWeight.SemiBold)
                            Text(loadError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onRefresh, enabled = !refreshing && !loading) { Text("Réessayer") }
                        }
                    }
                }
                if (emails.isEmpty() && loadError == null) {
                    item(key = "empty") {
                        if (loading) {
                            Box(Modifier.fillParentMaxHeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            EmptyPane(
                                title = if (search.isBlank() && category == null && status == null) "Vous êtes à jour" else "Aucun message",
                                description = when {
                                    search.isNotBlank() -> "Essayez une autre recherche dans cette sélection."
                                    scope is MailboxScope.Group -> "Les messages des boîtes de ce groupe apparaîtront ici. Glissez vers le bas pour actualiser."
                                    else -> "Glissez vers le bas pour relever votre courrier."
                                },
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    }
                }
                items(emails, key = MailboxEmail::id) { email ->
                    EmailRow(
                        email,
                        accounts.firstOrNull { it.id == email.emailAccountId },
                        accounts.filter { it.isActive }.indexOfFirst { it.id == email.emailAccountId }.coerceAtLeast(0),
                        selected = selectedEmail?.id == email.id,
                        onClick = { onSelect(email) },
                    )
                }
                if (hasMore) {
                    item(key = "more") {
                        TextButton(
                            onClick = onLoadMore,
                            enabled = !loading && !refreshing && !loadingMore,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            if (loadingMore) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (loadingMore) "Chargement…" else "Charger la suite")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailRow(email: MailboxEmail, account: EmailAccount?, accountIndex: Int, selected: Boolean, onClick: () -> Unit) {
    val unread = email.status == "UNREAD"
    Surface(
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            unread -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                ))
                Column(Modifier.weight(1f).padding(horizontal = 17.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(email.displaySender, Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (email.hasAttachments) Icon(Icons.Outlined.AttachFile, "Pièces jointes", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(compactMailDate(email.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (unread) Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(email.subject.ifBlank { "(Sans objet)" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(remember(email.body, email.htmlBody) { emailPreview(email.body, email.htmlBody) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    account?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(mailboxIdentityColor(accountIndex)))
                            Text(it.email, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

private fun compactMailDate(raw: String): String = runCatching {
    val date = Instant.parse(raw).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val pattern = when {
        date.toLocalDate() == today -> "HH:mm"
        date.year == today.year -> "d MMM"
        else -> "dd/MM/yy"
    }
    DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(date)
}.getOrDefault("")

@Composable
private fun EmailDetail(
    email: MailboxEmail,
    account: EmailAccount?,
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
            account?.let {
                Spacer(Modifier.height(12.dp))
                Text("Boîte · ${it.email}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            EmailBody(messageId = email.id, body = email.body, htmlBody = email.htmlBody)
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
