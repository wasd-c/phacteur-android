package app.phacteur.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailBadgeStateTest {
    private val owner = "session-a"
    private val openedAt = 1_000L

    private fun email(id: Int, receivedAt: Long? = 1_100L, eligible: Boolean = true,
        generation: String = owner) = BadgeEmail(generation, id, receivedAt, eligible)

    @Test
    fun fcmAndFallbackCountTheSameMailOnlyOnceInEitherDeliveryOrder() {
        val initial = EmailBadgeState(owner, openedAt)
        val fcmFirst = initial.record(listOf(email(42)), enabled = true)
            .record(listOf(email(42), email(43), email(43)), enabled = true)
        val fallbackFirst = initial.record(listOf(email(42), email(43)), enabled = true)
            .record(listOf(email(42)), enabled = true)

        assertEquals(2, fcmFirst.count)
        assertEquals(setOf(42, 43), fcmFirst.emailIds)
        assertEquals(fcmFirst, fallbackFirst)
    }

    @Test
    fun catchUpCountsEveryNewEligibleMailInTheBatch() {
        val state = EmailBadgeState(owner, openedAt).record(
            (1..30).map { email(it) } + email(31, receivedAt = 900L) + email(32, eligible = false),
            enabled = true,
        )

        assertEquals(30, state.count)
        assertEquals((1..30).toSet(), state.emailIds)
    }

    @Test
    fun persistentBadgeIdsDeduplicateEvenWhenAnOlderDeliveryLedgerHasExpired() {
        val beforeProcessDeath = EmailBadgeState(owner, openedAt)
            .record((1..600).map { email(it) }, enabled = true)
        val restored = EmailBadgeState(
            owner, beforeProcessDeath.lastOpenedAtMillis, beforeProcessDeath.count,
            beforeProcessDeath.emailIds.toSet(), beforeProcessDeath.arrivalIds.toSet(),
        )
        val redelivered = restored.record(listOf(email(1), email(600), email(601)), enabled = true)

        assertEquals(601, redelivered.count)
        assertEquals(601, redelivered.emailIds.size)
    }

    @Test
    fun appOpeningResetsTheCountAndRejectsDelayedMailFromBeforeTheOpening() {
        val state = EmailBadgeState(owner, openedAt)
            .record(listOf(email(1)), enabled = true)
            .opened(2_000L)
            .record(listOf(email(1), email(2, 2_000L), email(3, 2_001L)), enabled = true)

        assertEquals(2_000L, state.lastOpenedAtMillis)
        assertEquals(1, state.count)
        assertEquals(setOf(3), state.emailIds)
    }

    @Test
    fun disabledNotificationsClearAllBadgeIdsAndCount() {
        val state = EmailBadgeState(owner, openedAt)
            .record(listOf(email(1), email(2)), enabled = true)
            .record(listOf(email(3)), enabled = false)

        assertEquals(0, state.count)
        assertEquals(emptySet<Int>(), state.emailIds)
        assertEquals(emptySet<Int>(), state.arrivalIds)
        assertEquals(openedAt, state.lastOpenedAtMillis)
    }

    @Test
    fun foregroundMailDoesNotAlertAndItsDelayedDuplicateCannotCountAfterBackgrounding() {
        val opened = EmailBadgeState(owner, openedAt)
            .record((1..600).map { email(it) }, enabled = true, foreground = true)
        assertEquals(0, opened.count)
        assertEquals(emptySet<Int>(), opened.emailIds)
        assertEquals(600, opened.arrivalIds.size)

        val background = opened.record(listOf(email(1), email(601)), enabled = true, foreground = false)
        assertEquals(1, background.count)
        assertEquals(setOf(601), background.emailIds)
    }

    @Test
    fun otherOwnersAndExpiredSessionGenerationsCannotContribute() {
        val initial = EmailBadgeState(owner, openedAt)
        assertEquals(initial, initial.record(listOf(email(1, generation = "session-b")), enabled = true))

        val newSession = EmailBadgeState("session-b", openedAt)
            .record(listOf(email(1), email(2, generation = "session-b")), enabled = true)
        assertEquals(1, newSession.count)
        assertEquals(setOf(2), newSession.emailIds)

        val unbound = EmailBadgeState("", openedAt)
        assertEquals(unbound, unbound.record(listOf(email(3, generation = "")), enabled = true))
    }

    @Test
    fun readOutgoingAndHistoricalImportsMustBeRejectedBeforeCounting() {
        val state = EmailBadgeState(owner, openedAt).record(
            listOf(email(1, eligible = false), email(2, eligible = false),
                email(3, eligible = false), email(4)),
            enabled = true,
        )

        assertEquals(1, state.count)
        assertEquals(setOf(4), state.emailIds)
    }

    @Test
    fun invalidIdsAndUnknownArrivalTimesNeverEnterTheBadge() {
        val initial = EmailBadgeState(owner, openedAt)
        val result = initial.record(listOf(email(0), email(-1), email(1, null), email(2, -1L)), enabled = true)
        assertEquals(initial, result)
    }

    @Test
    fun readArchivedAndDeletedRemoveEachKnownMailOnlyOnce() {
        for (status in listOf("READ", "ARCHIVED", "DELETED")) {
            val state = EmailBadgeState(owner, openedAt)
                .record(listOf(email(1), email(2), email(3)), enabled = true)
                .statusChanged(listOf(1, 1, 99), status)
                .statusChanged(listOf(1), status)

            assertEquals(2, state.count)
            assertEquals(setOf(2, 3), state.emailIds)
        }
    }

    @Test
    fun markingMailUnreadOrChangingOtherStatusesDoesNotInventNewArrival() {
        val state = EmailBadgeState(owner, openedAt).record(listOf(email(1)), enabled = true)
        for (status in listOf("UNREAD", "STARRED", "SENT", "unknown", "")) {
            assertEquals(state, state.statusChanged(listOf(1, 2), status))
        }
    }

    @Test
    fun readingThenMarkingUnreadCannotRecountAnOldArrivalAfterTheSmallDeliveryLedgerExpires() {
        val state = EmailBadgeState(owner, openedAt)
            .record((1..600).map { email(it) }, enabled = true)
            .statusChanged(listOf(1), "READ")
            .statusChanged(listOf(1), "UNREAD")
        val restored = state.copy(emailIds = state.emailIds.toSet(), arrivalIds = state.arrivalIds.toSet())
        val redelivered = restored.record(listOf(email(1)), enabled = true)

        assertEquals(599, redelivered.count)
        assertEquals(restored, redelivered)
        assertEquals(600, redelivered.arrivalIds.size)
    }

    @Test
    fun countCannotOverflowOrBecomeNegative() {
        assertEquals(Int.MAX_VALUE, saturatingBadgeCount(Int.MAX_VALUE - 1, 3))
        assertEquals(Int.MAX_VALUE, saturatingBadgeCount(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(3, saturatingBadgeCount(-3, 3))
        assertEquals(5, saturatingBadgeCount(5, -1))
        val staleCounter = EmailBadgeState(owner, openedAt, count = 0, emailIds = setOf(1, 2))
        assertEquals(0, staleCounter.remove(listOf(1, 2, 3)).count)
    }

    @Test
    fun arrivalTimeUsesAuthenticatedIsoMetadataAndRejectsMalformedValues() {
        assertEquals(1_100L, badgeReceivedAtMillis("1970-01-01T00:00:01.100Z"))
        assertEquals(1_100L, badgeReceivedAtMillis("1970-01-01T01:00:01.100+01:00"))
        for (value in listOf("", "1100", "yesterday", "2026-10-03", "null")) {
            assertNull(badgeReceivedAtMillis(value))
        }
    }
}
