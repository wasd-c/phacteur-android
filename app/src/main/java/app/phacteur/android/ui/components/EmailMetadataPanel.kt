package app.phacteur.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailMetadata
import app.phacteur.android.data.EmailPublicEncryptionKey
import app.phacteur.android.data.parseEmailPublicEncryptionKey

/** The full public key is composed only after explicitly opening its dialog. */
@Composable
fun EmailMetadataPanel(
    metadata: EmailMetadata,
    modifier: Modifier = Modifier,
    messageId: Int? = null,
) {
    val identity = messageId ?: "${metadata.sender}|${metadata.receivedAt}"
    var expanded by remember(identity) { mutableStateOf(false) }
    var showingKey by remember(identity) { mutableStateOf(false) }
    val publicKey = remember(metadata.publicEncryptionKey) {
        metadata.publicEncryptionKey?.let { parseEmailPublicEncryptionKey(it.value, it.format, it.source) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = { expanded = !expanded; if (!expanded) showingKey = false },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(if (expanded) "Voir moins" else "Voir plus")
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetadataField("Expéditeur", metadata.senderName?.takeIf(String::isNotBlank) ?: metadata.sender)
                    MetadataField("De", metadata.sender)
                    MetadataField("Répondre à", metadata.replyTo)
                    MetadataField("Boîte destinataire", metadata.recipient)
                    MetadataField("Date du message", metadata.receivedAt?.let(::formatTimestamp))
                    metadata.recordedAt?.takeIf(String::isNotBlank)?.let {
                        MetadataField("Enregistré le", formatTimestamp(it))
                    }
                    MetadataField("Sens", when (metadata.direction) {
                        "RECEIVED" -> "Reçu"
                        "SENT" -> "Envoyé"
                        else -> null
                    })
                    MetadataField("Envoyé par", metadata.mailedBy)
                    MetadataField("Signé par", metadata.signedBy)
                    Text(
                        "Données déclarées par les en-têtes, identité non vérifiée.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (metadata.authenticationHeaders.isNotEmpty()) {
                        Text("En-têtes d’authentification", style = MaterialTheme.typography.labelLarge)
                        metadata.authenticationHeaders.forEach { header ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MetadataField(header.name, header.value, monospace = true)
                                Text(
                                    when (header.source) {
                                        "provided_header" -> "En-tête fourni avec le message"
                                        "trusted_reception" -> "En-tête enregistré à la réception"
                                        else -> "Source non communiquée"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (publicKey == null) {
                        MetadataField("Clé publique de chiffrement", "Non communiquée")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Clé publique de chiffrement", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(publicKeyDescription(publicKey), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(
                                onClick = { showingKey = true },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Afficher la clé publique", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        publicKey.value.take(64) + if (publicKey.value.length > 64) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (expanded && showingKey && publicKey != null) {
        PublicEncryptionKeyDialog(publicKey, onDismiss = { showingKey = false })
    }
}

@Composable
private fun MetadataField(label: String, value: String?, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value?.takeIf(String::isNotBlank) ?: "Non communiqué",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun PublicEncryptionKeyDialog(key: EmailPublicEncryptionKey, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copyMessage by remember(key) { mutableStateOf<String?>(null) }
    var copyError by remember(key) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clé publique de chiffrement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(publicKeyDescription(key), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Cette clé accompagne le message. Sa présence ne confirme pas l’identité de l’expéditeur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        key.value,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                copyMessage?.let { message ->
                    Row(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = if (copyError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        if (!copyError) Icon(Icons.Outlined.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyError = !copyPublicEncryptionKey(context, key)
                    copyMessage = if (copyError) "Impossible de copier la clé publique." else "Clé publique copiée"
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Fermer") }
        },
    )
}

private fun publicKeyDescription(key: EmailPublicEncryptionKey): String {
    val format = if (key.format == "OPENPGP") "OpenPGP" else "S/MIME"
    val source = if (key.source == "sender_attachment") "pièce jointe de l’expéditeur" else "en-tête de l’expéditeur"
    return "$format · $source"
}

private fun copyPublicEncryptionKey(context: Context, key: EmailPublicEncryptionKey): Boolean = try {
    val validated = parseEmailPublicEncryptionKey(key.value, key.format, key.source)
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (validated == null || clipboard == null) false else {
        clipboard.setPrimaryClip(ClipData.newPlainText("Clé publique de chiffrement", validated.value))
        true
    }
} catch (_: SecurityException) {
    false
} catch (_: IllegalStateException) {
    false
}
