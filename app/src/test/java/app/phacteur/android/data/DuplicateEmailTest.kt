package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateEmailTest {
    @Test
    fun `actions include every scoped copy once and retain their representative`() {
        val row = email().copy(duplicateIds = listOf(41, 42, 41, 43), duplicateCount = 3)

        assertEquals(listOf(42, 41, 43), row.statusUpdateIds)
    }

    @Test
    fun `missing metadata on mobile detail responses keeps single email actions`() {
        assertEquals(listOf(42), email().statusUpdateIds)
        assertEquals(1, email().duplicateCount)
    }

    @Test
    fun `malformed duplicate IDs cannot remove the source row or produce invalid mutations`() {
        val row = email().copy(duplicateIds = listOf(0, -1, 41, 41))

        assertEquals(listOf(42, 41), row.statusUpdateIds)
    }

    @Test
    fun `large duplicate groups fit the server batch limit without losing any copy`() {
        val ids = (1..1_001).toList()
        val batches = emailStatusBatches(ids + listOf(42, 501))

        assertEquals(ids, batches.flatten())
        assertTrue(batches.all { it.size <= 500 })
        assertEquals(listOf(500, 500, 1), batches.map(List<Int>::size))
    }

    @Test
    fun `an exact source copy recovers scoped metadata without replacing its content or identity`() {
        val source = email().copy(id = 41, body = "Corps de la source exacte", emailAccountId = 8)
        val row = email().copy(duplicateIds = listOf(42, 41, 43), duplicateCount = 3)

        val merged = source.withDuplicateMetadata(listOf(row))

        assertEquals(41, merged.id)
        assertEquals(source.body, merged.body)
        assertEquals(source.emailAccountId, merged.emailAccountId)
        assertEquals(listOf(41, 42, 43), merged.statusUpdateIds)
        assertEquals(3, merged.duplicateCount)
    }

    @Test
    fun `source mail outside the currently loaded scope keeps its original metadata`() {
        val source = email().copy(id = 99, duplicateIds = listOf(98, 99), duplicateCount = 2)
        val unrelated = email().copy(duplicateIds = listOf(42, 41), duplicateCount = 2)

        assertEquals(source, source.withDuplicateMetadata(listOf(unrelated)))
        assertEquals(source, source.withDuplicateMetadata(emptyList()))
    }

    @Test
    fun `recovered metadata stays within the server scope of the matching row`() {
        val source = email().copy(duplicateIds = listOf(42, 41, 99), duplicateCount = 3)
        val scopedRow = email().copy(duplicateIds = listOf(42, 41), duplicateCount = 2)

        val merged = source.withDuplicateMetadata(listOf(scopedRow))

        assertEquals(listOf(42, 41), merged.statusUpdateIds)
        assertEquals(2, merged.duplicateCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty mutation cannot broaden or silently skip its scope`() {
        emailStatusBatches(emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid mutation IDs fail before a partial batch can run`() {
        emailStatusBatches((1..500).toList() + 0)
    }

    private fun email() = MailboxEmail(
        id = 42,
        sender = "sender@example.com",
        senderName = null,
        subject = "Même message dans plusieurs boîtes",
        body = "Message",
        emailAccountId = 7,
        receivedAt = "2026-10-02T12:00:00Z",
        status = "UNREAD",
        hasAttachments = false,
        threadId = null,
        isStarred = false,
        attachments = emptyList(),
    )
}
