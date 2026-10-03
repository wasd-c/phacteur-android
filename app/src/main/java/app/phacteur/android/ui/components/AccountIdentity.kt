package app.phacteur.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.EmailProvider
import app.phacteur.android.data.AccountStatusTone
import app.phacteur.android.data.presentation

@Composable
fun AccountProviderMark(account: EmailAccount, modifier: Modifier = Modifier) {
    val provider = account.presentation().provider
    if (provider == EmailProvider.PHACTEUR || provider == EmailProvider.RELAY) {
        PhacteurMark(modifier.size(32.dp))
        return
    }
    val color = when (provider) {
        EmailProvider.GMAIL -> Color(0xFFB3261E)
        EmailProvider.OUTLOOK -> Color(0xFF0067B8)
        EmailProvider.YAHOO -> Color(0xFF6001D2)
        EmailProvider.ICLOUD -> Color(0xFF007AFF)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Provider monograms identify external services without imitating their logos.
    Surface(modifier.size(32.dp), shape = RoundedCornerShape(9.dp), color = color.copy(alpha = 0.10f)) {
        Box(contentAlignment = Alignment.Center) {
            when (provider) {
                EmailProvider.GMAIL -> Text("G", color = color, fontWeight = FontWeight.Bold)
                EmailProvider.OUTLOOK -> Text("O", color = color, fontWeight = FontWeight.Bold)
                EmailProvider.YAHOO -> Text("Y!", color = color, fontWeight = FontWeight.Bold)
                EmailProvider.ICLOUD -> Icon(Icons.Outlined.Cloud, null, Modifier.size(22.dp), tint = color)
                else -> Icon(Icons.Outlined.MailOutline, null, Modifier.size(22.dp), tint = color)
            }
        }
    }
}

@Composable
fun accountStatusColor(tone: AccountStatusTone): Color = when (tone) {
    AccountStatusTone.WARNING -> MaterialTheme.colorScheme.error
    AccountStatusTone.CONNECTED, AccountStatusTone.PROGRESS -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
