package app.phacteur.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxGroupDraft
import app.phacteur.android.ui.theme.mailboxGroupColor

private val GroupDraftSaver = listSaver<MailboxGroupDraft, String>(
    save = { listOf(it.name, it.color.orEmpty(), it.memberAccountIds.joinToString(",")) },
    restore = { values ->
        MailboxGroupDraft(
            name = values[0],
            color = values[1].takeIf(String::isNotEmpty),
            memberAccountIds = values[2].split(",").mapNotNull(String::toIntOrNull),
        )
    },
)

private val GroupColors = listOf(
    "Bleu marine" to "#1f467d",
    "Ambre" to "#c87933",
    "Olive" to "#68775a",
    "Prune" to "#765f7c",
    "Argile" to "#a45145",
)

/** [mutationVersion] advances only after a successful save/delete, keeping failed drafts editable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    groups: List<MailboxGroup>,
    accounts: List<EmailAccount>,
    loading: Boolean,
    refreshing: Boolean,
    saving: Boolean,
    loadError: String?,
    actionError: String?,
    mutationVersion: Long,
    onRefresh: () -> Unit,
    onSave: (String?, MailboxGroupDraft) -> Unit,
    onDelete: (MailboxGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val usableAccounts = accounts.filter { it.accountType != "RELAY" }
    val accountIds = usableAccounts.map(EmailAccount::id).toSet()
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(stateSaver = GroupDraftSaver) { mutableStateOf(MailboxGroupDraft()) }
    var observedMutation by rememberSaveable { mutableStateOf(mutationVersion) }
    var showActionError by rememberSaveable { mutableStateOf(false) }
    val busy = saving || loading || refreshing
    LaunchedEffect(mutationVersion) {
        if (observedMutation != mutationVersion) {
            observedMutation = mutationVersion
            editing = false
            editingId = null
            deletingId = null
            showActionError = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { if (!busy) onRefresh() },
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rassemblez vos boîtes liées dans une même réception.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            draft = MailboxGroupDraft(memberAccountIds = usableAccounts.firstOrNull()?.let { listOf(it.id) }.orEmpty())
                            editingId = null
                            editing = true
                            showActionError = false
                        },
                        enabled = !busy && !editing && loadError == null && usableAccounts.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nouveau groupe")
                    }
                }
            }
            if (loading) {
                item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Chargement des groupes…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (loadError != null) {
                item(key = "load-error") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Impossible de charger les groupes", fontWeight = FontWeight.SemiBold)
                            Text(loadError, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onRefresh, enabled = !busy) { Text("Réessayer") }
                        }
                    }
                }
            }
            if (!loading && usableAccounts.isEmpty()) {
                item(key = "no-accounts") {
                    Text("Connectez une boîte mail dans les réglages pour créer un groupe.",
                        Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (editing) {
                item(key = "editor") {
                    GroupEditor(
                        accounts = usableAccounts,
                        draft = draft,
                        isNew = editingId == null,
                        saving = saving,
                        canSave = !busy,
                        error = actionError.takeIf { showActionError },
                        onChange = { draft = it; showActionError = false },
                        onCancel = { editing = false; editingId = null; showActionError = false },
                        onSave = { showActionError = true; onSave(editingId, draft.normalized()) },
                    )
                }
            }
            if (!loading && loadError == null && groups.isEmpty() && !editing) {
                item(key = "empty") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Inbox, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Aucun groupe pour le moment", style = MaterialTheme.typography.titleMedium)
                            Text("Travail, courrier personnel, abonnements : à vous de les organiser.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(groups, key = { "group-${it.id}" }) { group ->
                GroupCard(
                    group = group,
                    enabled = !busy && !editing,
                    onEdit = {
                        draft = MailboxGroupDraft.fromGroup(group, accountIds)
                        editingId = group.id
                        editing = true
                        showActionError = false
                    },
                    onDelete = { deletingId = group.id; showActionError = false },
                )
            }
            if (actionError != null && showActionError && !editing && deletingId == null) {
                item(key = "action-error") { GroupActionError(actionError) }
            }
            item(key = "footer") { Spacer(Modifier.height(24.dp)) }
        }
    }

    groups.firstOrNull { it.id == deletingId }?.let { group ->
        AlertDialog(
            onDismissRequest = { if (!saving) deletingId = null },
            title = { Text("Supprimer « ${group.name} » ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Les boîtes mail et leurs messages seront conservés.")
                    if (showActionError && actionError != null) GroupActionError(actionError)
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionError = true; onDelete(group) }, enabled = !busy) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (saving) "Suppression…" else "Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }, enabled = !saving) { Text("Annuler") } },
        )
    }
}

@Composable
private fun GroupCard(group: MailboxGroup, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = mailboxGroupColor(group.color), shape = CircleShape, modifier = Modifier.size(10.dp)) {}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${group.members.size} boîte${if (group.members.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit, enabled = enabled) { Icon(Icons.Outlined.Edit, "Modifier ${group.name}", Modifier.size(20.dp)) }
                IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Outlined.DeleteOutline, "Supprimer ${group.name}", Modifier.size(20.dp)) }
            }
            group.members.forEach { member ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(member.displayName?.takeIf(String::isNotBlank) ?: member.email,
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!member.displayName.isNullOrBlank()) Text(member.email, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!member.isActive) Text("En pause", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupEditor(
    accounts: List<EmailAccount>,
    draft: MailboxGroupDraft,
    isNew: Boolean,
    saving: Boolean,
    canSave: Boolean,
    error: String?,
    onChange: (MailboxGroupDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val ownedIds = accounts.map(EmailAccount::id).toSet()
    val validationError = draft.validationError(ownedIds)
    val accountById = accounts.associateBy(EmailAccount::id)
    val orderedAccounts = draft.memberAccountIds.mapNotNull(accountById::get) + accounts.filter { it.id !in draft.memberAccountIds }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "Nouveau groupe" else "Modifier le groupe", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onCancel, enabled = !saving) { Icon(Icons.Outlined.Close, "Annuler la modification") }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nom du groupe") },
                placeholder = { Text("Travail et clients") },
                singleLine = true,
                enabled = !saving,
                isError = draft.name.isNotBlank() && draft.normalized().name.length > 80,
                supportingText = { Text("${draft.normalized().name.length}/80") },
                shape = MaterialTheme.shapes.small,
            )
            Text("Couleur", style = MaterialTheme.typography.labelLarge)
            Row {
                GroupColors.forEach { (label, value) ->
                    val selected = draft.color.equals(value, ignoreCase = true)
                    Box(Modifier.size(48.dp).semantics { contentDescription = "Couleur $label" }
                        .selectable(selected = selected, enabled = !saving, role = Role.RadioButton,
                            onClick = { onChange(draft.copy(color = value)) }), contentAlignment = Alignment.Center) {
                        Surface(color = mailboxGroupColor(value), shape = CircleShape, modifier = Modifier.size(32.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
                            }
                        }
                    }
                }
            }
            Text("Boîtes mail · ${draft.memberAccountIds.size} sélectionnée${if (draft.memberAccountIds.size > 1) "s" else ""}",
                style = MaterialTheme.typography.labelLarge)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column {
                    orderedAccounts.forEachIndexed { index, account ->
                        GroupAccountRow(account, draft, saving, onChange)
                        if (index < orderedAccounts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            if (draft.memberAccountIds.size > 1) {
                Text("Les flèches règlent l’ordre des boîtes dans le groupe.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (validationError != null && (draft.name.isNotBlank() || draft.memberAccountIds.isEmpty())) {
                Text(validationError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            error?.let { GroupActionError(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onCancel, enabled = !saving) { Text("Annuler") }
                Button(onClick = onSave, enabled = canSave && validationError == null) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (saving) "Enregistrement…" else if (isNew) "Créer" else "Enregistrer")
                }
            }
        }
    }
}

@Composable
private fun GroupAccountRow(account: EmailAccount, draft: MailboxGroupDraft, saving: Boolean, onChange: (MailboxGroupDraft) -> Unit) {
    val selectedIndex = draft.memberAccountIds.indexOf(account.id)
    val selected = selectedIndex >= 0
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = { onChange(draft.toggleAccount(account.id)) }, enabled = !saving,
            modifier = Modifier.semantics { contentDescription = "${if (selected) "Retirer" else "Ajouter"} ${account.email}" })
        Column(Modifier.weight(1f)) {
            Text(account.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account.label != account.email) Text(account.email, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!account.isActive) Text("En pause", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected && draft.memberAccountIds.size > 1) {
            IconButton(onClick = { onChange(draft.moveAccount(account.id, -1)) }, enabled = !saving && selectedIndex > 0) {
                Icon(Icons.Outlined.ExpandLess, "Monter ${account.email}", Modifier.size(20.dp))
            }
            IconButton(onClick = { onChange(draft.moveAccount(account.id, 1)) }, enabled = !saving && selectedIndex < draft.memberAccountIds.lastIndex) {
                Icon(Icons.Outlined.ExpandMore, "Descendre ${account.email}", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun GroupActionError(error: String) {
    Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
