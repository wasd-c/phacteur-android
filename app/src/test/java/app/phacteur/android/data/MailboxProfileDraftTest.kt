package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MailboxProfileDraftTest {
    @Test fun blankFieldsClearOptionalIdentityWithoutChangingTheMailboxAddress() {
        val draft = MailboxProfileDraft("  ", "  ").normalized()
        assertEquals(MailboxProfileDraft(), draft)
        assertNull(draft.validationError())
    }

    @Test fun replyAddressAndUnicodeSenderNameAreRetainedExactlyAfterTrimming() {
        val draft = MailboxProfileDraft("  Léa – Équipe  ", "  reply+support@example.org  ").normalized()
        assertEquals("Léa – Équipe", draft.displayName)
        assertEquals("reply+support@example.org", draft.replyTo)
        assertNull(draft.validationError())
    }

    @Test fun headerInjectionAndMultipleAddressesAreRejected() {
        assertNotNull(MailboxProfileDraft("Name\r\nBcc: other@example.org").validationError())
        assertNotNull(MailboxProfileDraft(replyTo = "one@example.org, two@example.org").validationError())
        assertNotNull(MailboxProfileDraft(replyTo = "one@exam\nple.org").validationError())
    }

    @Test fun profileLimitsMatchTheServer() {
        assertNull(MailboxProfileDraft("a".repeat(200)).validationError())
        assertNotNull(MailboxProfileDraft("a".repeat(201)).validationError())
        assertNotNull(MailboxProfileDraft(replyTo = "a".repeat(315) + "@example.org").validationError())
        assertNotNull(MailboxProfileDraft(replyTo = "name@localhost").validationError())
    }
}
