package app.phacteur.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProtocolTest {
    @Test
    fun acceptsOnlyIncomingEmailMessagesWithPositiveIntegerIds() {
        assertEquals(42, newEmailId(mapOf("type" to "new_email", "emailId" to "42")))
        assertEquals(Int.MAX_VALUE, newEmailId(mapOf("type" to "new_email", "emailId" to "2147483647")))
        for (raw in listOf("", "0", "-1", "+1", "1.5", "2147483648", "99999999999", " 1")) {
            assertNull(newEmailId(mapOf("type" to "new_email", "emailId" to raw)))
        }
        assertNull(newEmailId(mapOf("type" to "other", "emailId" to "1")))
        assertNull(newEmailId(emptyMap()))
    }

    @Test
    fun retriesTemporaryFailuresWhileRejectingMissingOrInvalidRoutes() {
        for (status in listOf(408, 429, 500, 503, 599)) assertTrue(isRetryableNotificationStatus(status))
        for (status in listOf(400, 401, 403, 404, 410, 422)) assertFalse(isRetryableNotificationStatus(status))
    }
}
