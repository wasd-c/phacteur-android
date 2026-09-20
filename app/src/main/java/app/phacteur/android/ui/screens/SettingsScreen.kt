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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.BuildConfig
import app.phacteur.android.data.Passkey
import app.phacteur.android.data.User
import app.phacteur.android.ui.components.formatTimestamp

@Composable
fun SettingsScreen(
    user: User,
    accounts: List<EmailAccount>,
    passkeys: List<Passkey>,
    notificationsEnabled: Boolean,
    firebaseConfigured: Boolean,
    notificationsAllowed: Boolean,
    onNotificationChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAddPasskey: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(user.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(user.email, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (user.twoFactorEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Double authentification active", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Notifications") }
        item {
            SettingRow(
                icon = Icons.Outlined.Notifications,
                title = "Nouveaux emails",
                subtitle = when {
                    notificationsEnabled && !notificationsAllowed -> "Bloquées dans les réglages Android"
                    notificationsEnabled && !firebaseConfigured -> "Vérification en arrière-plan activée"
                    notificationsEnabled -> "Alertes activées sur cet appareil"
                    else -> "Désactivées sur cet appareil"
                },
                trailing = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationChange,
                    )
                },
            )
        }
        if (!firebaseConfigured) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null)
                        Text(
                            "Cette version vérifie les nouveaux emails périodiquement, avec un délai d’environ 15 minutes ou plus selon Android. Les alertes instantanées ne sont pas disponibles dans cette version.",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!notificationsAllowed) {
            item {
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Autoriser les notifications dans Android")
                }
            }
        }

        item { SectionTitle("Comptes email") }
        items(accounts, key = EmailAccount::id) { account ->
            SettingRow(
                icon = Icons.Outlined.AlternateEmail,
                title = account.email,
                subtitle = "${account.provider} · ${if (account.isActive) account.syncStatus else "Inactif"}",
            )
        }
        item {
            OutlinedButton(
                onClick = { onOpenWeb("my/settings") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Gérer les fournisseurs sur le site")
            }
        }

        item { SectionTitle("Passkeys") }
        if (passkeys.isEmpty()) {
            item {
                Text(
                    "Aucune passkey enregistrée.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(passkeys, key = Passkey::id) { passkey ->
                SettingRow(
                    icon = Icons.Outlined.Key,
                    title = passkey.name,
                    subtitle = buildString {
                        append(if (passkey.backedUp) "Synchronisée" else "Cet appareil")
                        append(" · ajoutée ")
                        append(formatTimestamp(passkey.createdAt))
                    },
                )
            }
        }
        item {
            FilledTonalButton(
                onClick = onAddPasskey,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter une passkey Android")
            }
        }

        item { SectionTitle("Plus") }
        item {
            SettingLink(Icons.Outlined.Link, "Adresses privées", "my/private-relays", onOpenWeb)
        }
        item {
            SettingLink(Icons.Outlined.Fingerprint, "Sécurité et 2FA", "my/settings", onOpenWeb)
        }
        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Se déconnecter")
            }
        }
        item {
            Text(
                "Phacteur ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingLink(
    icon: ImageVector,
    title: String,
    path: String,
    onOpenWeb: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenWeb(path) },
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}
