package app.phacteur.android.ui

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.phacteur.android.data.EmailMetadata
import app.phacteur.android.data.EmailMetadataHeader
import app.phacteur.android.data.EmailPublicEncryptionKey
import app.phacteur.android.data.parseEmailPublicEncryptionKey
import app.phacteur.android.ui.components.EmailMetadataPanel
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailMetadataPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clearPublicKeyFixtureClipboard() {
        context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip()
    }

    @Test
    fun startsCollapsedAndShowsOnlyAnExcerptAfterExpansion() {
        showPanel(metadataFixture)

        compose.onNodeWithText("Voir plus").assertExists()
        compose.onNodeWithText("sender@example.com").assertDoesNotExist()
        compose.onNodeWithText(publicKeyFixture, useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("Voir plus").performClick()

        compose.onNodeWithText("sender@example.com").assertExists()
        compose.onNodeWithText("inbox@example.com").assertExists()
        compose.onNodeWithText(publicKeyFixture.take(64) + "…", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(publicKeyFixture, useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("Voir moins").performScrollTo().performClick()
        compose.onNodeWithText("sender@example.com").assertDoesNotExist()
        compose.onNodeWithText(publicKeyFixture.take(64) + "…", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun declaredAuthenticationHeadersAreShownWithoutClaimingVerifiedIdentity() {
        showPanel(metadataFixture)
        compose.onNodeWithText("Voir plus").performClick()

        compose.onNodeWithText("mail.example.com").assertExists()
        compose.onNodeWithText("sign.example.com").assertExists()
        compose.onNodeWithText("Données déclarées par les en-têtes, identité non vérifiée.").assertExists()
        compose.onNodeWithText("Authentication-Results").assertExists()
        compose.onNodeWithText("mx.example.com; dkim=pass header.d=sign.example.com").assertExists()
        compose.onNodeWithText("En-tête fourni avec le message").assertExists()
    }

    @Test
    fun publicKeyDialogShowsAndCopiesOnlyTheAcceptedPublicKey() {
        assertNotNull(parseEmailPublicEncryptionKey(publicKeyFixture, "OPENPGP", "sender_header"))
        showPanel(metadataFixture)
        compose.onNodeWithText("Voir plus").performClick()
        compose.onNodeWithText("Afficher la clé publique").performScrollTo().performClick()

        compose.onNodeWithText(publicKeyFixture, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Copier").performClick()

        compose.onNodeWithText("Clé publique copiée").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertExists()
        compose.runOnIdle {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            assertEquals(publicKeyFixture, clip?.getItemAt(0)?.text?.toString())
        }

        compose.onNodeWithText("Fermer").performClick()
        compose.onNodeWithText(publicKeyFixture, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun rejectsPrivateMaterialEvenWhenConstructedDirectlyAsAPublicKeyModel() {
        val privateFixture = "-----BEGIN PGP PRIVATE KEY BLOCK-----\nprivate-fixture\n-----END PGP PRIVATE KEY BLOCK-----"
        assertNull(parseEmailPublicEncryptionKey(privateFixture, "OPENPGP", "sender_header"))
        showPanel(metadataFixture.copy(publicEncryptionKey = EmailPublicEncryptionKey("OPENPGP", privateFixture, "sender_header")))
        compose.onNodeWithText("Voir plus").performClick()

        compose.onNodeWithText("Afficher la clé publique").assertDoesNotExist()
        compose.onNodeWithText("Non communiquée").assertExists()
        compose.onNodeWithText(privateFixture, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun switchingMessagesResetsTheExpandedPanel() {
        var metadata by mutableStateOf(metadataFixture)
        compose.setContent {
            PhacteurTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmailMetadataPanel(metadata)
                }
            }
        }
        compose.onNodeWithText("Voir plus").performClick()
        compose.onNodeWithText("sender@example.com").assertExists()

        compose.runOnIdle { metadata = EmailMetadata(sender = "next@example.com") }

        compose.onNodeWithText("Voir plus").assertExists()
        compose.onNodeWithText("sender@example.com").assertDoesNotExist()
        compose.onNodeWithText("next@example.com").assertDoesNotExist()
        compose.onNodeWithText(publicKeyFixture, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun showPanel(metadata: EmailMetadata) {
        compose.setContent {
            PhacteurTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    EmailMetadataPanel(metadata)
                }
            }
        }
    }
}

// Tiny public RSA packet for UI tests only; it represents no real identity or production key.
private const val publicKeyFixture = "-----BEGIN PGP PUBLIC KEY BLOCK-----\nxg0EAAAAAAEACQFDAAUR\n-----END PGP PUBLIC KEY BLOCK-----"

private val metadataFixture = EmailMetadata(
    sender = "sender@example.com",
    senderName = "Alice",
    replyTo = "reply@example.com",
    recipient = "inbox@example.com",
    receivedAt = "2026-10-02T12:00:00Z",
    recordedAt = "2026-10-02T12:01:00Z",
    direction = "RECEIVED",
    mailedBy = "mail.example.com",
    signedBy = "sign.example.com",
    authenticationHeaders = listOf(EmailMetadataHeader(
        "Authentication-Results",
        "mx.example.com; dkim=pass header.d=sign.example.com",
        "provided_header",
    )),
    publicEncryptionKey = EmailPublicEncryptionKey("OPENPGP", publicKeyFixture, "sender_header"),
)
