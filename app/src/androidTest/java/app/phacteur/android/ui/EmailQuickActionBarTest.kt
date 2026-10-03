package app.phacteur.android.ui

import android.content.ActivityNotFoundException
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.phacteur.android.ui.components.EmailQuickActionBar
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class EmailQuickActionBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clearFixtureClipboard() {
        context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip()
    }

    @Test
    fun copyPreservesLeadingZerosMarksSensitiveAndDoesNotOpenParentMessage() {
        var openedMessages = 0
        val receivedAt = Instant.now().toString()
        compose.setContent {
            PhacteurTheme {
                Box(Modifier.width(320.dp).clickable { openedMessages++ }) {
                    EmailQuickActionBar(
                        subject = "Code de connexion",
                        body = "Votre code de connexion est 00-62-81.",
                        receivedAt = receivedAt,
                        direction = "RECEIVED",
                        compact = true,
                    )
                }
            }
        }

        compose.onNodeWithText("Copier le code").performTouchInput { click() }

        compose.onNodeWithText("Code copié").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
        compose.runOnIdle {
            assertEquals(0, openedMessages)
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            assertEquals("006281", clip?.getItemAt(0)?.text?.toString())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                assertTrue(clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
            }
        }
    }

    @Test
    fun linkShowsDestinationAndLaunchesAnExternalBrowsableIntentWithoutHeaders() {
        val browser = BrowserContext(context)
        showLink(browser)

        compose.onNodeWithText("accounts.example.com").assertExists()
        compose.onNodeWithText("Suivre le lien").performClick()

        compose.runOnIdle {
            val intent = browser.launched.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("https://accounts.example.com/verify?token=fixture", intent.dataString)
            assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
            assertNull(intent.extras)
            assertNull(intent.component)
        }
    }

    @Test
    fun missingBrowserShowsAnAccessibleErrorWithoutCrashing() {
        val browser = BrowserContext(context, missingBrowser = true)
        showLink(browser)

        compose.onNodeWithText("Suivre le lien").performClick()

        compose.onNodeWithText("Impossible d’ouvrir ce lien dans un navigateur.").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
        compose.runOnIdle { assertTrue(browser.launched.isEmpty()) }
    }

    @Test
    fun changingToSentOrAnExpiredMessageRemovesActionsAndPriorFeedback() {
        var direction by mutableStateOf("RECEIVED")
        var receivedAt by mutableStateOf(Instant.now().toString())
        compose.setContent {
            PhacteurTheme {
                EmailQuickActionBar(
                    subject = "Code de connexion",
                    body = "Votre code de connexion est 006281.",
                    receivedAt = receivedAt,
                    direction = direction,
                )
            }
        }

        compose.onNodeWithText("Copier le code").performClick()
        compose.onNodeWithText("Code copié").assertExists()
        compose.runOnIdle { direction = "SENT" }
        compose.onNodeWithText("Copier le code").assertDoesNotExist()
        compose.onNodeWithText("Code copié").assertDoesNotExist()

        compose.runOnIdle {
            direction = "RECEIVED"
            receivedAt = Instant.now().minusSeconds(2 * 86_400L).toString()
        }
        compose.onNodeWithText("Copier le code").assertDoesNotExist()
    }

    @Test
    fun meetingShowsItsDomainAndOpensOnlyTheExternalBrowsableIntent() {
        val browser = BrowserContext(context)
        val receivedAt = Instant.now().minusSeconds(2 * 86_400L).toString()
        val meetingUrl = "https://meet.google.com/abc-defg-hij"
        compose.setContent {
            CompositionLocalProvider(LocalContext provides browser) {
                PhacteurTheme {
                    Box(Modifier.width(280.dp)) {
                        EmailQuickActionBar(
                            subject = "Invitation à une réunion",
                            body = "Rejoignez-nous : $meetingUrl",
                            receivedAt = receivedAt,
                            direction = "RECEIVED",
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("meet.google.com").assertExists()
        compose.onNodeWithText("Rejoindre la réunion").performClick()

        compose.runOnIdle {
            val intent = browser.launched.single()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(meetingUrl, intent.dataString)
            assertTrue(intent.categories.orEmpty().contains(Intent.CATEGORY_BROWSABLE))
            assertNull(intent.extras)
            assertNull(intent.component)
        }
    }

    private fun showLink(browser: BrowserContext) {
        val receivedAt = Instant.now().toString()
        compose.setContent {
            CompositionLocalProvider(LocalContext provides browser) {
                PhacteurTheme {
                    Box(Modifier.width(280.dp)) {
                        EmailQuickActionBar(
                            subject = "Confirmation de connexion",
                            body = "Confirmez votre connexion : https://accounts.example.com/verify?token=fixture",
                            receivedAt = receivedAt,
                            direction = "RECEIVED",
                        )
                    }
                }
            }
        }
    }
}

private class BrowserContext(base: Context, private val missingBrowser: Boolean = false) : ContextWrapper(base) {
    val launched = mutableListOf<Intent>()

    override fun startActivity(intent: Intent) {
        if (missingBrowser) throw ActivityNotFoundException("Fixture has no browser")
        launched += Intent(intent)
    }
}
