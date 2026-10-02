package app.phacteur.android.ui

import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxGroupMember

/** Synthetic data exclusively for instrumented UI tests. */
internal object MailboxFixtures {
    val personal = account(11, "Personnel", "personal@example.test")
    val work = account(22, "Travail", "work@example.test")
    val inactive = account(33, "Ancienne boîte", "inactive@example.test").copy(isActive = false)
    val accounts = listOf(personal, work, inactive)

    val project = MailboxGroup(
        id = "project",
        name = "Projet Orion",
        color = "#14b8a6",
        members = listOf(
            MailboxGroupMember(
                emailAccountId = work.id,
                email = work.email,
                displayName = work.displayName,
                isActive = true,
            ),
        ),
    )
    val emptyGroup = MailboxGroup(
        id = "empty-group",
        name = "Groupe vide",
        color = null,
        members = emptyList(),
    )
    val groups = listOf(project, emptyGroup)

    val email = MailboxEmail(
        id = 101,
        sender = "sender@example.test",
        senderName = "Camille",
        subject = "Documents du projet",
        body = "Les documents sont prêts pour la réunion.",
        emailAccountId = work.id,
        receivedAt = "2026-09-20T08:30:00Z",
        status = "UNREAD",
        hasAttachments = false,
        threadId = null,
        isStarred = false,
        attachments = emptyList(),
    )

    private fun account(id: Int, name: String, email: String) = EmailAccount(
        id = id,
        email = email,
        provider = "PHACTEUR",
        isPrimary = id == 11,
        isActive = true,
        canSend = true,
        syncStatus = "IDLE",
        displayName = name,
    )
}
