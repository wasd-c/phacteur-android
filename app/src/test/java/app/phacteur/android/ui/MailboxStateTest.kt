package app.phacteur.android.ui

import app.phacteur.android.data.EmailAccount
import app.phacteur.android.data.MailboxEmail
import app.phacteur.android.data.MailboxGroup
import app.phacteur.android.data.MailboxGroupMember
import app.phacteur.android.data.MailboxScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxStateTest {
    @Test
    fun `new compose uses the selected mailbox identity`() {
        val state = PhacteurUiState(
            accounts = listOf(account(1, primary = true), account(2)),
            mailboxScope = MailboxScope.Account(2),
        )
        assertEquals(2, state.defaultSendingAccountId())
    }

    @Test
    fun `group compose chooses an active sending member`() {
        val state = PhacteurUiState(
            accounts = listOf(account(1, primary = true), account(2, canSend = false), account(3)),
            mailboxScope = MailboxScope.Group("work"),
            mailboxGroups = listOf(MailboxGroup("work", "Travail", null, listOf(
                MailboxGroupMember(2, "2@example.com", null, true),
                MailboxGroupMember(3, "3@example.com", null, true),
            ))),
        )
        assertEquals(3, state.defaultSendingAccountId())
    }

    @Test
    fun `receive only scope falls back to the primary sender`() {
        val state = PhacteurUiState(
            accounts = listOf(account(1), account(2, canSend = false), account(3, primary = true)),
            mailboxScope = MailboxScope.Account(2),
        )
        assertEquals(3, state.defaultSendingAccountId())
        assertNull(state.copy(accounts = emptyList()).defaultSendingAccountId())
    }

    @Test
    fun `reading an unread result removes it from unread filter but keeps detail open`() {
        val email = email("UNREAD")
        val state = PhacteurUiState(
            emails = listOf(email), mailboxStatus = "UNREAD", mailboxTotalCount = 7, selectedEmail = email,
        ).applyEmailStatus(email.id, "READ", closeAfter = false)

        assertTrue(state.emails.isEmpty())
        assertEquals(6, state.mailboxTotalCount)
        assertEquals("READ", state.selectedEmail?.status)
    }

    @Test
    fun `archived filter retains an already archived message`() {
        val email = email("ARCHIVED")
        val state = PhacteurUiState(emails = listOf(email), mailboxStatus = "ARCHIVED", mailboxTotalCount = 1)
            .applyEmailStatus(email.id, "ARCHIVED", closeAfter = true)

        assertEquals(listOf(email), state.emails)
        assertEquals(1, state.mailboxTotalCount)
    }

    @Test
    fun `removing a result disables offset pagination until first page reloads`() {
        val messages = (1..50).map { email("UNREAD").copy(id = it) }
        val state = PhacteurUiState(
            emails = messages, mailboxStatus = "UNREAD", mailboxPage = 1,
            mailboxTotalCount = 51, mailboxHasMore = true,
        ).applyEmailStatus(1, "READ", closeAfter = false)

        assertEquals(49, state.emails.size)
        assertEquals(50, state.mailboxTotalCount)
        assertFalse(state.mailboxHasMore)
        assertEquals(1, state.mailboxPage)
    }

    @Test
    fun `late archive response does not close a different selected message`() {
        val archived = email("READ")
        val selected = email("UNREAD").copy(id = 11)
        val state = PhacteurUiState(
            emails = listOf(selected), mailboxTotalCount = 1, selectedEmail = selected,
            mailboxPage = 2, mailboxHasMore = true, mailboxScope = MailboxScope.Account(2),
        ).applyEmailStatus(archived.id, "ARCHIVED", closeAfter = true)

        assertEquals(selected, state.selectedEmail)
        assertEquals(listOf(selected), state.emails)
        assertEquals(1, state.mailboxTotalCount)
        assertEquals(2, state.mailboxPage)
        assertTrue(state.mailboxHasMore)
    }

    @Test
    fun `reading duplicate copies updates a different representative and the open source mail`() {
        val collapsed = email("UNREAD").copy(duplicateIds = listOf(10, 11), duplicateCount = 2)
        val unrelated = email("UNREAD").copy(id = 20)
        val source = email("UNREAD").copy(id = 11)
        val state = PhacteurUiState(
            emails = listOf(collapsed, unrelated), selectedEmail = source, mailboxTotalCount = 2,
        ).applyEmailStatus(listOf(11), "READ", closeAfter = false)

        assertEquals("READ", state.emails.first().status)
        assertEquals("UNREAD", state.emails.last().status)
        assertEquals("READ", state.selectedEmail?.status)
        assertEquals(2, state.mailboxTotalCount)
    }

    @Test
    fun `archiving duplicate copies removes one visible result and closes its source mail`() {
        val collapsed = email("READ").copy(duplicateIds = listOf(10, 11), duplicateCount = 2)
        val unrelated = email("READ").copy(id = 20)
        val state = PhacteurUiState(
            emails = listOf(collapsed, unrelated), selectedEmail = collapsed.copy(id = 11),
            mailboxTotalCount = 2, mailboxHasMore = true,
        ).applyEmailStatus(collapsed.statusUpdateIds, "ARCHIVED", closeAfter = true)

        assertEquals(listOf(unrelated), state.emails)
        assertEquals(1, state.mailboxTotalCount)
        assertFalse(state.mailboxHasMore)
        assertNull(state.selectedEmail)
    }

    private fun account(id: Int, primary: Boolean = false, canSend: Boolean = true) = EmailAccount(
        id = id, email = "$id@example.com", provider = "Phacteur", isPrimary = primary,
        isActive = true, canSend = canSend, syncStatus = "IDLE",
    )

    private fun email(status: String) = MailboxEmail(
        id = 10, sender = "sender@example.com", senderName = null, subject = "Message", body = "Body",
        emailAccountId = 1, receivedAt = "2026-09-20T12:00:00Z", status = status,
        hasAttachments = false, threadId = null, isStarred = false, attachments = emptyList(),
    )
}
