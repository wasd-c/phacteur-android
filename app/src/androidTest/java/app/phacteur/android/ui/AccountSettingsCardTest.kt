package app.phacteur.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.data.EmailAccount
import app.phacteur.android.ui.screens.AccountSettingsCard
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSettingsCardTest {
    @get:Rule val compose = createComposeRule()

    private val native = EmailAccount(1, "hello@phacteur.app", "Phacteur", true, true, true, "IDLE",
        accountType = "LOCAL", isLocal = true, canSync = true)

    @Test fun nativeAccountHasReadableStatusAndItsOwnActions() {
        var webPath: String? = null
        compose.setContent {
            PhacteurTheme {
                AccountSettingsCard(native, false, true, null, {}, {}, {}, {}, { webPath = it })
            }
        }
        compose.onNodeWithText("Connecté").assertIsDisplayed()
        compose.onNodeWithText("Phacteur · Principale").assertIsDisplayed()
        compose.onNodeWithText("IDLE").assertDoesNotExist()
        compose.onNodeWithText("Relever les messages").assertDoesNotExist()
        compose.onNodeWithText("Réactiver la réception Gmail").assertDoesNotExist()
        compose.onNodeWithText("Adresses privées").performClick()
        compose.runOnIdle { assertEquals("my/private-relays", webPath) }
    }

    @Test fun gmailWatchAndImapFetchingAreDistinctActions() {
        var renewals = 0
        val gmail = native.copy(provider = "Gmail", email = "hello@gmail.com", accountType = "GMAIL", isLocal = false, isPrimary = false)
        compose.setContent {
            PhacteurTheme {
                AccountSettingsCard(gmail, false, true, null, {}, {}, {}, { renewals++ }, {})
            }
        }
        compose.onNodeWithText("Gmail").assertIsDisplayed()
        compose.onNodeWithText("Adresses privées").assertDoesNotExist()
        compose.onNodeWithText("Relever les messages").assertDoesNotExist()
        compose.onNodeWithText("Réactiver la réception Gmail").performClick()
        compose.runOnIdle { assertEquals(1, renewals) }
    }
}
