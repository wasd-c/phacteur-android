package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MailboxGroupDraftTest {
    @Test
    fun `editing preserves paused mailboxes and server membership order`() {
        val group = MailboxGroup(
            id = "4", name = "Travail", color = null,
            members = listOf(
                MailboxGroupMember(9, "paused@example.com", null, false),
                MailboxGroupMember(2, "work@example.com", "Travail", true),
                MailboxGroupMember(18, "removed@example.com", null, true),
            ),
        )

        val draft = MailboxGroupDraft.fromGroup(group, ownedAccountIds = setOf(2, 9))

        assertEquals(listOf(9, 2), draft.memberAccountIds)
        assertNull(draft.color)
        assertNull(draft.validationError(setOf(2, 9)))
    }

    @Test
    fun `moving a mailbox swaps neighbours without altering group membership`() {
        val draft = MailboxGroupDraft(name = "Travail", memberAccountIds = listOf(5, 7, 2))

        val earlier = draft.moveAccount(7, -1)
        val later = earlier.moveAccount(7, 1)

        assertEquals(listOf(7, 5, 2), earlier.memberAccountIds)
        assertEquals(draft, later)
        assertEquals(draft, draft.moveAccount(5, -1))
        assertEquals(draft, draft.moveAccount(2, 1))
        assertEquals(draft, draft.moveAccount(99, -1))
    }

    @Test
    fun `reselected mailbox is appended and cannot duplicate existing members`() {
        val draft = MailboxGroupDraft(name = "Travail", memberAccountIds = listOf(5, 7, 2))

        val changed = draft.toggleAccount(7).toggleAccount(7)

        assertEquals(listOf(5, 2, 7), changed.memberAccountIds)
        assertEquals(changed, changed.toggleAccount(0))
    }

    @Test
    fun `empty duplicate invalid and unavailable memberships block saving`() {
        val draft = MailboxGroupDraft(name = "Travail", memberAccountIds = listOf(2))

        assertNotNull(draft.copy(memberAccountIds = emptyList()).validationError())
        assertNotNull(draft.copy(memberAccountIds = listOf(2, 2)).validationError())
        assertNotNull(draft.copy(memberAccountIds = listOf(-1)).validationError())
        assertNotNull(draft.copy(memberAccountIds = (1..101).toList()).validationError())
        assertNotNull(draft.validationError(ownedAccountIds = setOf(8)))
        assertNull(draft.validationError(ownedAccountIds = setOf(2)))
    }

    @Test
    fun `payload names are NFC normalized and hex colors canonicalized`() {
        val draft = MailboxGroupDraft(name = "  Cafe\u0301  ", color = "#ABC123", memberAccountIds = listOf(2, 9))

        assertEquals("Café", draft.normalized().name)
        assertEquals("#abc123", draft.normalized().color)
        assertEquals(listOf(2, 9), draft.normalized().memberAccountIds)
        assertNull(draft.validationError())
    }

    @Test
    fun `name length is checked after normalization and controls are rejected`() {
        val draft = MailboxGroupDraft(name = "e\u0301".repeat(80), memberAccountIds = listOf(2))

        assertNull(draft.validationError())
        assertNotNull(draft.copy(name = "a".repeat(81)).validationError())
        assertNotNull(draft.copy(name = "Travail\nPersonnel").validationError())
        assertNotNull(draft.copy(name = "Travail\u0085Personnel").validationError())
        assertNotNull(draft.copy(name = "   ").validationError())
        assertNotNull(draft.copy(color = "red").validationError())
    }
}
