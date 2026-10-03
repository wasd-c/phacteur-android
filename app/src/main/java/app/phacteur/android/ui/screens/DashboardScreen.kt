package app.phacteur.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.presentation
import app.phacteur.android.ui.components.AccountProviderMark
import app.phacteur.android.ui.components.accountStatusColor
import app.phacteur.android.ui.components.emailPreview
import app.phacteur.android.data.Dashboard
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.ui.components.EmptyPane
import app.phacteur.android.ui.components.SenderAvatar
import app.phacteur.android.ui.components.formatTimestamp

@Composable
fun DashboardScreen(
    dashboard: Dashboard?,
    userName: String,
    onEmailClick: (MailboxEmail) -> Unit,
    onAccountClick: (EmailAccount) -> Unit,
    modifier: Modifier = Modifier,
    accounts: List<EmailAccount> = emptyList(),
) {
    if (dashboard == null) {
        EmptyPane(
            title = "Tableau de bord indisponible",
            description = "Actualisez pour charger votre activité.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Bonjour $userName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Voici l’essentiel de votre messagerie.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Non lus", dashboard.counts.unread, Icons.Outlined.MarkEmailUnread, Modifier.weight(1f))
                    MetricCard("Emails", dashboard.counts.emails, Icons.Outlined.Mail, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Conversations", dashboard.counts.conversations, Icons.Outlined.Forum, Modifier.weight(1f))
                    MetricCard("Contacts", dashboard.counts.contacts, Icons.Outlined.Groups, Modifier.weight(1f))
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("Emails récents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (dashboard.recentEmails.isEmpty()) {
            item {
                Text(
                    "Aucun email récent.",
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(dashboard.recentEmails, key = MailboxEmail::id) { email ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEmailClick(email) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SenderAvatar(email.displaySender)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                email.displaySender,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (email.status == "UNREAD") FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(email.subject, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                remember(email.body, email.htmlBody) { emailPreview(email.body, email.htmlBody) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            formatTimestamp(email.receivedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Text("Comptes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        val identities = accounts.associateBy(EmailAccount::id)
        items(dashboard.accounts.map { identities[it.id] ?: it }, key = { it.id }) { account ->
            val info = account.presentation()
            Surface(
                onClick = { onAccountClick(account) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountProviderMark(account)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(account.email, fontWeight = FontWeight.SemiBold)
                        Text(
                            info.providerLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        info.statusLabel,
                        color = accountStatusColor(info.statusTone),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
