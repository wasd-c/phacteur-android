package app.phacteur.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxScope
import app.phacteur.android.ui.theme.mailboxGroupColor
import app.phacteur.android.ui.theme.mailboxIdentityColor

/** One persistent entry point to the same identities and groups as the web sidebar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailboxScopeSelector(
    scope: MailboxScope,
    accounts: List<EmailAccount>,
    groups: List<MailboxGroup>,
    onScopeChange: (MailboxScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSelector by rememberSaveable { mutableStateOf(false) }
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
    val selectedAccount = (scope as? MailboxScope.Account)?.let { selection ->
        activeAccounts.firstOrNull { it.id == selection.id }
    }
    val selectedGroup = (scope as? MailboxScope.Group)?.let { selection ->
        groups.firstOrNull { it.id == selection.id }
    }
    val title = when (scope) {
        MailboxScope.All -> "Toutes les boîtes"
        is MailboxScope.Account -> selectedAccount?.let(::mailboxTitle) ?: "Boîte indisponible"
        is MailboxScope.Group -> selectedGroup?.name ?: "Groupe indisponible"
    }
    val subtitle = when (scope) {
        MailboxScope.All -> mailboxCountLabel(activeAccounts.size)
        is MailboxScope.Account -> selectedAccount?.email ?: "Choisissez une autre boîte"
        is MailboxScope.Group -> selectedGroup?.let(::groupMembersLabel) ?: "Choisissez un autre groupe"
    }
    val selectedColor = when (scope) {
        MailboxScope.All -> MaterialTheme.colorScheme.primary
        is MailboxScope.Account -> mailboxIdentityColor(activeAccounts.indexOfFirst { it.id == scope.id }.coerceAtLeast(0))
        is MailboxScope.Group -> mailboxGroupColor(selectedGroup?.color)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Changer de boîte ou de groupe",
                    onClick = { showSelector = true },
                )
                .heightIn(min = 68.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScopeMark(scope, selectedColor, prominent = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    if (showSelector) {
        ModalBottomSheet(
            onDismissRequest = { showSelector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            MailboxScopeChoices(
                scope = scope,
                accounts = activeAccounts,
                groups = groups,
                onSelect = { selected ->
                    showSelector = false
                    if (selected != scope) onScopeChange(selected)
                },
                onClose = { showSelector = false },
            )
        }
    }
}

@Composable
private fun MailboxScopeChoices(
    scope: MailboxScope,
    accounts: List<EmailAccount>,
    groups: List<MailboxGroup>,
    onSelect: (MailboxScope) -> Unit,
    onClose: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()
    val matchingAccounts = accounts.filter {
        it.email.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true)
    }
    val matchingGroups = groups.filter { group ->
        group.name.contains(query, ignoreCase = true) || group.members.any {
            it.email.contains(query, ignoreCase = true) ||
                it.displayName.orEmpty().contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Vos boîtes & groupes",
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Fermer le sélecteur de boîtes")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { contentDescription = "Rechercher une boîte ou un groupe" },
            placeholder = { Text("Rechercher une boîte ou un groupe", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = if (search.isNotEmpty()) {
                {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Effacer la recherche")
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.weight(1f).selectableGroup(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "all") {
                ScopeChoiceRow(
                    scope = MailboxScope.All,
                    title = "Toutes les boîtes",
                    subtitle = mailboxCountLabel(accounts.size),
                    color = MaterialTheme.colorScheme.primary,
                    selected = scope == MailboxScope.All,
                    onSelect = onSelect,
                )
            }
            item(key = "accounts-header") { ScopeSectionTitle("Mes boîtes", matchingAccounts.size) }
            if (matchingAccounts.isEmpty()) {
                item(key = "accounts-empty") {
                    ScopeEmptyLabel(if (query.isBlank()) "Aucune boîte active" else "Aucune boîte correspondante")
                }
            }
            items(matchingAccounts, key = { "account-${it.id}" }) { account ->
                ScopeChoiceRow(
                    scope = MailboxScope.Account(account.id),
                    title = mailboxTitle(account),
                    subtitle = account.email,
                    color = mailboxIdentityColor(accounts.indexOf(account)),
                    selected = scope == MailboxScope.Account(account.id),
                    needsAttention = account.syncStatus == "ERROR",
                    onSelect = onSelect,
                )
            }
            item(key = "groups-divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            item(key = "groups-header") { ScopeSectionTitle("Mes groupes", matchingGroups.size) }
            if (matchingGroups.isEmpty()) {
                item(key = "groups-empty") {
                    ScopeEmptyLabel(if (query.isBlank()) "Aucun groupe pour le moment" else "Aucun groupe correspondant")
                }
            }
            items(matchingGroups, key = { "group-${it.id}" }) { group ->
                ScopeChoiceRow(
                    scope = MailboxScope.Group(group.id),
                    title = group.name,
                    subtitle = groupMembersLabel(group),
                    color = mailboxGroupColor(group.color),
                    selected = scope == MailboxScope.Group(group.id),
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun ScopeChoiceRow(
    scope: MailboxScope,
    title: String,
    subtitle: String,
    color: Color,
    selected: Boolean,
    onSelect: (MailboxScope) -> Unit,
    needsAttention: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(scope) })
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScopeMark(scope, color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (scope is MailboxScope.Group) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (needsAttention) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = "Synchronisation de cette boîte à vérifier",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Done,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ScopeMark(scope: MailboxScope, color: Color, prominent: Boolean = false) {
    Box(
        modifier = Modifier
            .size(if (prominent) 38.dp else 28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (prominent) color.copy(alpha = 0.10f) else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        when (scope) {
            MailboxScope.All -> Icon(Icons.Outlined.AllInbox, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            is MailboxScope.Account -> Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            is MailboxScope.Group -> if (prominent) {
                Icon(Icons.Outlined.Layers, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            } else {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
    }
}

@Composable
private fun ScopeSectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScopeEmptyLabel(label: String) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun mailboxTitle(account: EmailAccount): String =
    account.displayName?.trim()?.takeIf(String::isNotBlank)
        ?: account.email.substringBefore('@').ifBlank { account.provider }

private fun mailboxCountLabel(count: Int): String = when (count) {
    0 -> "Aucune boîte active"
    1 -> "1 boîte active"
    else -> "$count boîtes réunies"
}

private fun groupMembersLabel(group: MailboxGroup): String =
    group.members.filter { it.isActive }.distinctBy { it.emailAccountId }
        .joinToString(" · ") { it.displayName?.trim()?.takeIf(String::isNotBlank) ?: it.email }
        .ifBlank { "Aucune boîte active dans ce groupe" }
