package app.phacteur.android.data

import java.util.Locale

enum class EmailProvider { PHACTEUR, GMAIL, OUTLOOK, YAHOO, ICLOUD, OTHER, RELAY }

/** Provider branding never changes the transport or grants account capabilities. */
enum class AccountConnection { NATIVE, GMAIL_OAUTH, OUTLOOK, IMAP, RELAY, UNKNOWN }

enum class AccountStatusTone { CONNECTED, PROGRESS, WARNING, INACTIVE, INFO, UNKNOWN }

enum class EmailAccountAction { SYNC, EDIT_PROFILE, OPEN_RELAYS, OPEN_VAULT, RENEW_GMAIL_RECEPTION }

data class EmailAccountPresentation(
    val provider: EmailProvider,
    val providerLabel: String,
    val connection: AccountConnection,
    val description: String,
    val statusLabel: String,
    val statusDescription: String,
    val statusTone: AccountStatusTone,
    val actions: Set<EmailAccountAction>,
    val isNativePhacteur: Boolean,
)

fun EmailAccount.presentation(): EmailAccountPresentation {
    val type = accountType.trim().uppercase(Locale.ROOT)
    val connection = when {
        type == "RELAY" -> AccountConnection.RELAY
        isLocal || type == "LOCAL" -> AccountConnection.NATIVE
        type == "GMAIL" || type == "GMAIL_OAUTH" -> AccountConnection.GMAIL_OAUTH
        type == "OUTLOOK" -> AccountConnection.OUTLOOK
        type == "IMAP" -> AccountConnection.IMAP
        else -> AccountConnection.UNKNOWN
    }
    val providerBrand = when (connection) {
        AccountConnection.RELAY -> EmailProvider.RELAY
        AccountConnection.NATIVE -> EmailProvider.PHACTEUR
        AccountConnection.GMAIL_OAUTH -> EmailProvider.GMAIL
        AccountConnection.OUTLOOK -> EmailProvider.OUTLOOK
        else -> declaredProvider(provider) ?: domainProvider(email) ?: EmailProvider.OTHER
    }
    val providerLabel = when (providerBrand) {
        EmailProvider.PHACTEUR -> "Phacteur"
        EmailProvider.GMAIL -> "Gmail"
        EmailProvider.OUTLOOK -> "Outlook / Microsoft 365"
        EmailProvider.YAHOO -> "Yahoo Mail"
        EmailProvider.ICLOUD -> "iCloud Mail"
        EmailProvider.RELAY -> "Adresse relais Phacteur"
        EmailProvider.OTHER -> if (connection == AccountConnection.IMAP) "Boîte IMAP" else "Autre fournisseur"
    }
    val encrypted = syncStatus.trim().uppercase(Locale.ROOT) == "E2EE_REQUIRED"
    val status = accountStatus(encrypted, connection)
    val actions = buildSet {
        if (id <= 0) return@buildSet
        if (connection == AccountConnection.RELAY) {
            add(EmailAccountAction.OPEN_RELAYS)
        } else {
            // PATCH /email-accounts/{id} edits identity fields independently of transport.
            add(EmailAccountAction.EDIT_PROFILE)
        }
        if (encrypted) add(EmailAccountAction.OPEN_VAULT)
        if (connection == AccountConnection.NATIVE) add(EmailAccountAction.OPEN_RELAYS)
        // This authenticated route renews Gmail reception; it does not report
        // watch health and must not change the displayed connection status.
        if (connection == AccountConnection.GMAIL_OAUTH && isActive && canSync && !encrypted) {
            add(EmailAccountAction.RENEW_GMAIL_RECEPTION)
        }
        // Only external IMAP POST /sync actually imports messages. Local/Gmail
        // endpoints merely update metadata and must not acquire a Sync action.
        if (connection == AccountConnection.IMAP && isActive && canSync && !encrypted &&
            syncStatus.trim().uppercase(Locale.ROOT) != "SYNCING") {
            add(EmailAccountAction.SYNC)
        }
    }
    return EmailAccountPresentation(
        provider = providerBrand,
        providerLabel = providerLabel,
        connection = connection,
        description = when (connection) {
            AccountConnection.NATIVE -> if (customDomain.isNullOrBlank()) "Votre boîte native Phacteur."
                else "Votre boîte native Phacteur sur votre domaine personnalisé."
            AccountConnection.GMAIL_OAUTH -> "Votre boîte Gmail reliée à Phacteur par Google."
            AccountConnection.OUTLOOK -> "Votre connexion Microsoft existante, gérée sur le site."
            AccountConnection.IMAP -> if (providerBrand == EmailProvider.OTHER) "Une boîte externe connectée en IMAP."
                else "Votre boîte $providerLabel connectée en IMAP."
            AccountConnection.RELAY -> "Une adresse relais privée, gérée séparément de vos boîtes mail."
            AccountConnection.UNKNOWN -> "Les informations de connexion de cette boîte sont indisponibles."
        },
        statusLabel = status.label,
        statusDescription = status.description,
        statusTone = status.tone,
        actions = actions,
        isNativePhacteur = connection == AccountConnection.NATIVE,
    )
}

private data class AccountStatus(val label: String, val description: String, val tone: AccountStatusTone)

private fun EmailAccount.accountStatus(encrypted: Boolean, connection: AccountConnection): AccountStatus {
    if (!isActive) return AccountStatus(
        "Désactivé",
        if (encrypted) "Cette boîte est désactivée. L’accès aux messages chiffrés nécessite le coffre sur le site."
        else "Cette boîte est désactivée dans Phacteur.",
        AccountStatusTone.INACTIVE,
    )
    return when (syncStatus.trim().uppercase(Locale.ROOT)) {
        "E2EE_REQUIRED" -> AccountStatus(
            "Coffre requis",
            "Ouvrez le coffre de messagerie sur le site pour accéder aux messages chiffrés.",
            AccountStatusTone.INFO,
        )
        "SYNCING" -> AccountStatus("Synchronisation en cours", "Phacteur relève les messages de cette boîte.", AccountStatusTone.PROGRESS)
        "ERROR" -> AccountStatus("Connexion à vérifier", "Vérifiez l’accès au fournisseur et les réglages de la boîte.", AccountStatusTone.WARNING)
        "IDLE", "COMPLETED" -> AccountStatus(
            "Connecté",
            when {
                connection == AccountConnection.NATIVE && customDomainReady == false ->
                    "La boîte est connectée. L’envoi attend la validation de votre domaine."
                !canSend && connection != AccountConnection.RELAY && connection != AccountConnection.UNKNOWN ->
                    "Cette boîte est connectée pour la réception. L’envoi est indisponible."
                else -> "Cette boîte est connectée à Phacteur."
            },
            AccountStatusTone.CONNECTED,
        )
        else -> AccountStatus("État indisponible", "L’état de connexion de cette boîte n’a pas été communiqué.", AccountStatusTone.UNKNOWN)
    }
}

private fun declaredProvider(value: String): EmailProvider? = when (value.trim().lowercase(Locale.ROOT)) {
    "phacteur", "phacteur custom domain" -> EmailProvider.PHACTEUR
    "gmail", "google", "google mail", "google workspace" -> EmailProvider.GMAIL
    "outlook", "microsoft", "microsoft 365", "microsoft365", "office 365", "office365", "hotmail" -> EmailProvider.OUTLOOK
    "yahoo", "yahoo mail" -> EmailProvider.YAHOO
    "icloud", "icloud mail", "apple icloud" -> EmailProvider.ICLOUD
    else -> null
}

/** Exact mailbox domains only: neither suffixes nor subdomains identify a provider. */
private fun domainProvider(address: String): EmailProvider? {
    val normalized = address.trim().lowercase(Locale.ROOT)
    if (normalized.count { it == '@' } != 1 || normalized.any { it.isWhitespace() || it in "<>,;" }) return null
    val localPart = normalized.substringBefore('@')
    if (localPart.isBlank()) return null
    return when (normalized.substringAfter('@')) {
        "gmail.com", "googlemail.com" -> EmailProvider.GMAIL
        "outlook.com", "hotmail.com", "hotmail.fr", "live.com", "live.fr", "msn.com" -> EmailProvider.OUTLOOK
        "yahoo.com", "yahoo.fr", "yahoo.co.uk", "yahoo.de", "yahoo.es", "yahoo.it", "yahoo.ca", "yahoo.com.au", "ymail.com", "rocketmail.com" -> EmailProvider.YAHOO
        "icloud.com", "me.com", "mac.com" -> EmailProvider.ICLOUD
        else -> null
    }
}
