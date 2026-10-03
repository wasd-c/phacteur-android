package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAccountPresentationTest {
    @Test
    fun `idle native account has Phacteur identity and no artificial sync action`() {
        val presentation = account(type = "LOCAL", provider = "IMAP", isLocal = true, canSync = true).presentation()

        assertEquals(EmailProvider.PHACTEUR, presentation.provider)
        assertEquals("Phacteur", presentation.providerLabel)
        assertEquals(AccountConnection.NATIVE, presentation.connection)
        assertTrue(presentation.isNativePhacteur)
        assertEquals("Connecté", presentation.statusLabel)
        assertEquals(AccountStatusTone.CONNECTED, presentation.statusTone)
        assertEquals(setOf(EmailAccountAction.EDIT_PROFILE, EmailAccountAction.OPEN_RELAYS), presentation.actions)
        assertFalse(presentation.description.contains("temps réel"))
    }

    @Test
    fun `native custom domain is identified from server metadata rather than address`() {
        val presentation = account(type = "IMAP", isLocal = true, email = "contact@company.example")
            .copy(customDomain = "company.example", customDomainReady = false, canSend = false).presentation()

        assertEquals(AccountConnection.NATIVE, presentation.connection)
        assertEquals(EmailProvider.PHACTEUR, presentation.provider)
        assertTrue(presentation.isNativePhacteur)
        assertTrue(presentation.description.contains("domaine personnalisé"))
        assertTrue(presentation.statusDescription.contains("validation"))
        assertFalse(EmailAccountAction.SYNC in presentation.actions)
    }

    @Test
    fun `Phacteur domain and label alone cannot grant native capabilities`() {
        val byDomain = account(type = "UNKNOWN", email = "alice@phacteur.app", canSync = true).presentation()
        val byLabel = account(type = "UNKNOWN", provider = "Phacteur", canSync = true).presentation()

        assertFalse(byDomain.isNativePhacteur)
        assertFalse(byLabel.isNativePhacteur)
        assertEquals(AccountConnection.UNKNOWN, byDomain.connection)
        assertEquals(AccountConnection.UNKNOWN, byLabel.connection)
        assertFalse(EmailAccountAction.SYNC in byDomain.actions)
        assertFalse(EmailAccountAction.SYNC in byLabel.actions)
    }

    @Test
    fun `Gmail OAuth and Gmail over IMAP keep distinct connections and actions`() {
        val oauth = account(type = "GMAIL", provider = "Gmail", email = "alice@gmail.com", canSync = true).presentation()
        val imap = account(type = "IMAP", provider = "IMAP", email = "alice@gmail.com", canSync = true).presentation()

        assertEquals(EmailProvider.GMAIL, oauth.provider)
        assertEquals(EmailProvider.GMAIL, imap.provider)
        assertEquals(AccountConnection.GMAIL_OAUTH, oauth.connection)
        assertEquals(AccountConnection.IMAP, imap.connection)
        assertEquals("Connecté", oauth.statusLabel)
        assertEquals("Connecté", imap.statusLabel)
        assertFalse(EmailAccountAction.SYNC in oauth.actions)
        assertTrue(EmailAccountAction.RENEW_GMAIL_RECEPTION in oauth.actions)
        assertFalse(EmailAccountAction.RENEW_GMAIL_RECEPTION in imap.actions)
        assertTrue(EmailAccountAction.SYNC in imap.actions)
        assertTrue(imap.description.contains("IMAP"))
    }

    @Test
    fun `known provider brands work over IMAP without gaining unsupported actions`() {
        val providers = mapOf(
            "alice@googlemail.com" to EmailProvider.GMAIL,
            "alice@hotmail.fr" to EmailProvider.OUTLOOK,
            "alice@outlook.com" to EmailProvider.OUTLOOK,
            "alice@ymail.com" to EmailProvider.YAHOO,
            "alice@yahoo.fr" to EmailProvider.YAHOO,
            "alice@icloud.com" to EmailProvider.ICLOUD,
            "alice@me.com" to EmailProvider.ICLOUD,
            "alice@mac.com" to EmailProvider.ICLOUD,
        )

        providers.forEach { (email, provider) ->
            val presentation = account(email = email, canSync = true).presentation()
            assertEquals(email, provider, presentation.provider)
            assertEquals(email, AccountConnection.IMAP, presentation.connection)
            assertEquals(email, setOf(EmailAccountAction.EDIT_PROFILE, EmailAccountAction.SYNC), presentation.actions)
        }
    }

    @Test
    fun `provider recognition accepts exact domains only and rejects spoofed or malformed addresses`() {
        val addresses = listOf(
            "alice@gmail.com.evil.example", "alice@sub.gmail.com", "alice@notgmail.com",
            "alice@yahoo.fr.evil.example", "alice@sub.icloud.com", "alice@sub.outlook.com",
            "alice@phacteur.app.evil.example", "alice@sub.phacteur.app", "alice@@gmail.com",
            "<alice@gmail.com>", "@gmail.com", "alice @gmail.com", "alice@gmail.com.",
        )

        addresses.forEach { email ->
            val presentation = account(email = email).presentation()
            assertEquals(email, EmailProvider.OTHER, presentation.provider)
            assertFalse(email, presentation.isNativePhacteur)
        }
        assertEquals(EmailProvider.GMAIL, account(email = "  Alice@GMAIL.COM  ").presentation().provider)
    }

    @Test
    fun `Microsoft 365 brand does not assert an unimplemented reconnect or sync path`() {
        val presentation = account(type = "OUTLOOK", provider = "Microsoft 365", canSync = true).presentation()

        assertEquals(EmailProvider.OUTLOOK, presentation.provider)
        assertEquals("Outlook / Microsoft 365", presentation.providerLabel)
        assertEquals(AccountConnection.OUTLOOK, presentation.connection)
        assertEquals(setOf(EmailAccountAction.EDIT_PROFILE), presentation.actions)
    }

    @Test
    fun `Gmail reception renewal requires a supported active Google connection`() {
        val gmail = account(type = "GMAIL", canSync = true)
        val disallowed = listOf(
            gmail.copy(isActive = false),
            gmail.copy(canSync = false),
            gmail.copy(syncStatus = "E2EE_REQUIRED"),
            gmail.copy(accountType = "IMAP", provider = "Gmail", email = "alice@gmail.com"),
            gmail.copy(accountType = "UNKNOWN", provider = "Gmail"),
        )

        assertTrue(EmailAccountAction.RENEW_GMAIL_RECEPTION in gmail.presentation().actions)
        assertTrue(EmailAccountAction.RENEW_GMAIL_RECEPTION in gmail.copy(accountType = "GMAIL_OAUTH").presentation().actions)
        disallowed.forEach { value ->
            assertFalse(value.toString(), EmailAccountAction.RENEW_GMAIL_RECEPTION in value.presentation().actions)
        }
    }

    @Test
    fun `disabled state overrides old sync status and removes sync action`() {
        val presentation = account(status = "ERROR", canSync = true).copy(isActive = false).presentation()

        assertEquals("Désactivé", presentation.statusLabel)
        assertEquals(AccountStatusTone.INACTIVE, presentation.statusTone)
        assertFalse(EmailAccountAction.SYNC in presentation.actions)
    }

    @Test
    fun `connection errors remain actionable and ongoing imports cannot be duplicated`() {
        val error = account(status = "ERROR", canSync = true).presentation()
        val syncing = account(status = "SYNCING", canSync = true).presentation()

        assertEquals("Connexion à vérifier", error.statusLabel)
        assertEquals(AccountStatusTone.WARNING, error.statusTone)
        assertTrue(EmailAccountAction.SYNC in error.actions)
        assertEquals("Synchronisation en cours", syncing.statusLabel)
        assertEquals(AccountStatusTone.PROGRESS, syncing.statusTone)
        assertFalse(EmailAccountAction.SYNC in syncing.actions)
    }

    @Test
    fun `encrypted accounts explain the vault requirement without offering legacy sync`() {
        val presentation = account(status = "E2EE_REQUIRED", canSync = true).presentation()

        assertEquals("Coffre requis", presentation.statusLabel)
        assertEquals(AccountStatusTone.INFO, presentation.statusTone)
        assertTrue(presentation.statusDescription.contains("coffre de messagerie"))
        assertTrue(EmailAccountAction.OPEN_VAULT in presentation.actions)
        assertFalse(EmailAccountAction.SYNC in presentation.actions)
        assertFalse(presentation.statusLabel.contains("E2EE"))
    }

    @Test
    fun `relay metadata stays separate even when marked local`() {
        val presentation = account(type = "RELAY", isLocal = true, canSync = true)
            .copy(customDomainReady = false, canSend = false).presentation()

        assertEquals(EmailProvider.RELAY, presentation.provider)
        assertEquals(AccountConnection.RELAY, presentation.connection)
        assertFalse(presentation.isNativePhacteur)
        assertEquals(setOf(EmailAccountAction.OPEN_RELAYS), presentation.actions)
        assertFalse(presentation.statusDescription.contains("validation"))
    }

    @Test
    fun `unknown status and provider have readable fallback and no invented capabilities`() {
        val presentation = account(type = "UNKNOWN", provider = "OTHER_CUSTOM", status = "WAITING_V2", canSync = true).presentation()
        val invalidId = account(canSync = true).copy(id = 0).presentation()
        val incompleteMetadata = account(type = "UNKNOWN", status = "IDLE").copy(canSend = false).presentation()

        assertEquals("Autre fournisseur", presentation.providerLabel)
        assertEquals("État indisponible", presentation.statusLabel)
        assertEquals(AccountStatusTone.UNKNOWN, presentation.statusTone)
        assertEquals(setOf(EmailAccountAction.EDIT_PROFILE), presentation.actions)
        assertFalse(presentation.statusDescription.contains("L’envoi"))
        assertFalse(incompleteMetadata.statusDescription.contains("L’envoi"))
        assertTrue(invalidId.actions.isEmpty())
        assertEquals("Connecté", account(status = "COMPLETED").presentation().statusLabel)
    }

    private fun account(
        type: String = "IMAP",
        provider: String = "IMAP",
        email: String = "alice@example.test",
        isLocal: Boolean = false,
        canSync: Boolean = false,
        status: String = "IDLE",
    ) = EmailAccount(
        id = 1,
        email = email,
        provider = provider,
        isPrimary = false,
        isActive = true,
        canSend = true,
        syncStatus = status,
        accountType = type,
        isLocal = isLocal,
        canSync = canSync,
    )
}
