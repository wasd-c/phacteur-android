package app.phacteur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.EmailAccountAction
import app.phacteur.android.data.MailboxProfileDraft
import app.phacteur.android.data.presentation
import app.phacteur.android.ui.components.AccountProviderMark
import app.phacteur.android.ui.components.accountStatusColor
import app.phacteur.android.ui.components.formatTimestamp

@Composable
internal fun AccountSettingsCard(
    account: EmailAccount,
    busy: Boolean,
    actionsEnabled: Boolean,
    error: String?,
    onOpenInbox: () -> Unit,
    onEditProfile: () -> Unit,
    onSync: () -> Unit,
    onRenewGmail: () -> Unit,
    onOpenWeb: (String) -> Unit,
) {
    val info = account.presentation()
    Card(
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountProviderMark(account)
                Column(Modifier.weight(1f)) {
                    Text(account.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (account.label != account.email) {
                        Text(account.email, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(info.providerLabel + if (account.isPrimary) " · Principale" else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(info.statusLabel, style = MaterialTheme.typography.labelLarge, color = accountStatusColor(info.statusTone))
            Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (info.statusTone != app.phacteur.android.data.AccountStatusTone.CONNECTED || !account.canSend) {
                Text(info.statusDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            account.lastSyncAt?.let { date ->
                Text("Dernière relève · ${formatTimestamp(date)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (account.isActive && account.accountType != "RELAY") TextButton(onClick = onOpenInbox, enabled = actionsEnabled) { Text("Voir les mails") }
                if (EmailAccountAction.EDIT_PROFILE in info.actions) {
                    TextButton(onClick = onEditProfile, enabled = actionsEnabled) { Text("Identité d’envoi") }
                }
                if (EmailAccountAction.SYNC in info.actions) {
                    TextButton(onClick = onSync, enabled = actionsEnabled) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                        Text("Relever les messages")
                    }
                }
                if (EmailAccountAction.RENEW_GMAIL_RECEPTION in info.actions) {
                    TextButton(onClick = onRenewGmail, enabled = actionsEnabled) { Text("Réactiver la réception Gmail") }
                }
                if (EmailAccountAction.OPEN_RELAYS in info.actions) {
                    TextButton(onClick = { onOpenWeb("my/private-relays") }, enabled = actionsEnabled) { Text("Adresses privées") }
                }
                if (EmailAccountAction.OPEN_VAULT in info.actions) {
                    TextButton(onClick = { onOpenWeb("my/mailbox") }, enabled = actionsEnabled) { Text("Ouvrir le coffre sur le site") }
                }
                if (info.isNativePhacteur && account.customDomain != null) {
                    TextButton(onClick = { onOpenWeb("my/settings#custom-domains") }, enabled = actionsEnabled) { Text("Gérer mon domaine") }
                }
                TextButton(onClick = { onOpenWeb("my/settings#mailboxes") }, enabled = actionsEnabled) { Text("Réglages sur le site") }
            }
        }
    }
}

@Composable
internal fun AccountProfileDialog(
    account: EmailAccount,
    saving: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSave: (MailboxProfileDraft) -> Unit,
) {
    var name by rememberSaveable(account.id) { mutableStateOf(account.displayName.orEmpty()) }
    var replyTo by rememberSaveable(account.id) { mutableStateOf(account.replyTo.orEmpty()) }
    val draft = MailboxProfileDraft(name, replyTo)
    val validationError = draft.validationError()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Identité d’envoi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(account.email, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Nom d’expéditeur") },
                    singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(replyTo, { replyTo = it }, label = { Text("Adresse de réponse") },
                    placeholder = { Text(account.email) }, singleLine = true, enabled = !saving,
                    modifier = Modifier.fillMaxWidth())
                Text("L’adresse de réponse peut rester vide pour utiliser celle de cette boîte.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (validationError ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.normalized()) }, enabled = !saving && validationError == null) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Enregistrer")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Annuler") } },
    )
}
