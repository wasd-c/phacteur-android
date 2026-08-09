package app.phacteur.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.phacteur.android.notifications.NotificationLaunchStore
import app.phacteur.android.ui.PhacteurApp
import app.phacteur.android.ui.PhacteurViewModel
import app.phacteur.android.ui.theme.PhacteurTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PhacteurViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            PhacteurTheme {
                PhacteurApp(activity = this, viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent) {
        intent.data?.takeIf {
            it.scheme == "phacteur" && it.host == "auth" && it.path == "/callback"
        }?.let { callback ->
            viewModel.completeMobileAuthorization(callback)
            intent.data = null
        }
        NotificationLaunchStore.consume()?.let { target ->
            viewModel.handleNotification(target.emailId, target.threadId)
        }
    }
}
