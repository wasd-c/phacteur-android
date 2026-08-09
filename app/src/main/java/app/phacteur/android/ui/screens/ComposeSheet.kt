package app.phacteur.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.ui.ComposeDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSheet(
    draft: ComposeDraft,
    accounts: List<EmailAccount>,
    sending: Boolean,
    onDraftChange: (ComposeDraft) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val account = accounts.firstOrNull { it.id == draft.emailAccountId } ?: accounts.firstOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nouveau message", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { accountMenuExpanded = true },
                    enabled = accounts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.AlternateEmail, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        account?.email ?: "Aucun compte capable d’envoyer",
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choisir l’expéditeur")
                }
                DropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false },
                ) {
                    accounts.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.email) },
                            onClick = {
                                accountMenuExpanded = false
                                onDraftChange(draft.copy(emailAccountId = candidate.id))
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            OutlinedTextField(
                value = draft.recipients,
                onValueChange = { onDraftChange(draft.copy(recipients = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Destinataires") },
                supportingText = { Text("Séparez plusieurs adresses par une virgule.") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = draft.subject,
                onValueChange = { onDraftChange(draft.copy(subject = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Objet") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = draft.body,
                onValueChange = { onDraftChange(draft.copy(body = it)) },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                label = { Text("Message") },
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = onSend,
                enabled = !sending && account != null && draft.recipients.isNotBlank() && draft.body.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (sending) "Envoi…" else "Envoyer")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
