package app.phacteur.android.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailQuickAction
import app.phacteur.android.data.emailQuickActions
import app.phacteur.android.data.safeEmailActionUrl
import kotlinx.coroutines.delay
import java.net.URI
import java.time.Instant

/** Small actions beside a message; button taps are handled independently of its enclosing row. */
@Composable
fun EmailQuickActionBar(
    subject: String,
    body: String,
    htmlBody: String? = null,
    receivedAt: String = "",
    direction: String = "UNKNOWN",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    var now by remember(subject, body, htmlBody, receivedAt, direction) { mutableStateOf(Instant.now()) }
    var feedback by remember(subject, body, htmlBody, receivedAt, direction) { mutableStateOf<QuickActionFeedback?>(null) }
    val actions = remember(subject, body, htmlBody, receivedAt, direction, now) {
        emailQuickActions(subject, body, htmlBody, receivedAt, direction, now).take(3)
    }
    if (actions.isEmpty() && feedback == null) return

    if (actions.isNotEmpty()) {
        LaunchedEffect(subject, body, htmlBody, receivedAt, direction) {
            while (true) {
                delay(30_000)
                now = Instant.now()
            }
        }
    }

    fun performAction(action: EmailQuickAction) {
        val tappedAt = Instant.now()
        val currentActions = emailQuickActions(subject, body, htmlBody, receivedAt, direction, tappedAt)
        now = tappedAt
        if (action !in currentActions) {
            feedback = QuickActionFeedback("Cette action a expiré.", error = true)
            return
        }
        feedback = when (action) {
            is EmailQuickAction.CopyCode -> {
                if (copyVerificationCode(context, action.code)) QuickActionFeedback("Code copié", error = false)
                else QuickActionFeedback("Impossible de copier le code.", error = true)
            }
            is EmailQuickAction.FollowLink, is EmailQuickAction.JoinMeeting -> {
                val url = quickActionUrl(action)?.let(::safeEmailActionUrl)
                when {
                    url == null -> QuickActionFeedback("Ce lien n’est plus disponible.", error = true)
                    !openActionInBrowser(context, url) -> QuickActionFeedback("Impossible d’ouvrir ce lien dans un navigateur.", error = true)
                    else -> null
                }
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (actions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                itemsIndexed(actions) { _, action ->
                    OutlinedButton(
                        onClick = { performAction(action) },
                        modifier = Modifier.heightIn(min = 48.dp).widthIn(max = if (compact) 220.dp else 260.dp),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            when (action) {
                                is EmailQuickAction.CopyCode -> Icons.Outlined.ContentCopy
                                is EmailQuickAction.FollowLink -> Icons.Outlined.OpenInNew
                                is EmailQuickAction.JoinMeeting -> Icons.Outlined.Videocam
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                when (action) {
                                    is EmailQuickAction.CopyCode -> "Copier le code"
                                    is EmailQuickAction.FollowLink -> "Suivre le lien"
                                    is EmailQuickAction.JoinMeeting -> "Rejoindre la réunion"
                                },
                                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                            val actionUrl = quickActionUrl(action)
                            if (actionUrl != null) {
                                val domain = remember(actionUrl) { runCatching { URI(actionUrl).host }.getOrNull() }
                                Text(
                                    domain ?: "Lien HTTPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        feedback?.let { result ->
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (result.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Icon(
                    if (result.error) Icons.Outlined.ErrorOutline else Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = color,
                )
                Text(result.message, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

private data class QuickActionFeedback(val message: String, val error: Boolean)

private fun quickActionUrl(action: EmailQuickAction): String? = when (action) {
    is EmailQuickAction.CopyCode -> null
    is EmailQuickAction.FollowLink -> action.url
    is EmailQuickAction.JoinMeeting -> action.url
}

private fun copyVerificationCode(context: Context, code: String): Boolean = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) false else {
        val clip = ClipData.newPlainText("Code de vérification", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        true
    }
} catch (_: SecurityException) {
    false
} catch (_: IllegalStateException) {
    false
}

/** Only the verified URL enters the intent: no Phacteur cookie, token, header or message content. */
private fun openActionInBrowser(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
}
