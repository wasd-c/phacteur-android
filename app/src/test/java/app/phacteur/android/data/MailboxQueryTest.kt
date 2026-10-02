package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxQueryTest {
    @Test
    fun `default mailbox is the received inbox sorted by priority`() {
        val parameters = MailboxQuery().parameters(page = 1, limit = 50)

        assertEquals("true", parameters["inbox"])
        assertEquals("priority", parameters["sort"])
        assertNull(parameters["accountIds"])
        assertNull(parameters["status"])
    }

    @Test
    fun `pagination keeps group search status and category on server`() {
        val query = MailboxQuery(
            status = "UNREAD",
            search = "  from:alice@example.com  ",
            accountIds = listOf(4, 19, 4),
            category = "important",
        )
        val first = query.parameters(page = 1, limit = 50)
        val next = query.parameters(page = 2, limit = 50)

        assertEquals("4,19", next["accountIds"])
        assertEquals("UNREAD", next["status"])
        assertEquals("important", next["category"])
        assertEquals("from:alice@example.com", next["search"])
        assertEquals(first - "page", next - "page")
        assertEquals("2", next["page"])
    }

    @Test
    fun `archived messages are not constrained to received inbox`() {
        val parameters = MailboxQuery(status = "ARCHIVED", accountIds = listOf(42))
            .parameters(page = 1, limit = 50)

        assertEquals("42", parameters["accountIds"])
        assertEquals("ARCHIVED", parameters["status"])
        assertFalse(parameters.containsKey("inbox"))
    }

    @Test
    fun `group scope only includes its active members`() {
        val group = MailboxGroup(
            id = "work", name = "Travail", color = "#446644",
            members = listOf(
                MailboxGroupMember(7, "work@example.com", "Travail", true),
                MailboxGroupMember(9, "old@example.com", null, false),
            ),
        )
        assertEquals(listOf(7), MailboxScope.Group("work").accountIds(listOf(group)))
        assertEquals(listOf(9), MailboxScope.Account(9).accountIds(listOf(group)))
        assertNull(MailboxScope.All.accountIds(listOf(group)))
    }

    @Test
    fun `empty and removed groups never become the all account scope`() {
        val group = MailboxGroup("empty", "Vide", null, emptyList())
        val empty = MailboxQuery(accountIds = MailboxScope.Group("empty").accountIds(listOf(group)))
        val removed = MailboxQuery(accountIds = MailboxScope.Group("removed").accountIds(listOf(group)))

        assertTrue(empty.isEmptyScope)
        assertTrue(removed.isEmptyScope)
        assertFalse(MailboxQuery().isEmptyScope)
    }

    @Test(expected = IllegalStateException::class)
    fun `empty group cannot be encoded as an unscoped HTTP request`() {
        MailboxQuery(accountIds = emptyList()).parameters(page = 1, limit = 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid account identifier cannot broaden mailbox selection`() {
        MailboxQuery(accountIds = listOf(0)).parameters(page = 1, limit = 50)
    }
}
