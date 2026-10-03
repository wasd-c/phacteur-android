package app.phacteur.android.notifications

import java.time.Instant

/** Only metadata authenticated as an owned, incoming, unread notification candidate enters this ledger. */
internal data class BadgeEmail(
    val owner: String,
    val id: Int,
    val receivedAtMillis: Long?,
    val eligible: Boolean = true,
)

internal data class EmailBadgeState(
    val owner: String,
    val lastOpenedAtMillis: Long,
    val count: Int = 0,
    val emailIds: Set<Int> = emptySet(),
    val arrivalIds: Set<Int> = emailIds,
) {
    fun record(emails: Iterable<BadgeEmail>, enabled: Boolean, foreground: Boolean = false): EmailBadgeState {
        if (!enabled) return copy(count = 0, emailIds = emptySet(), arrivalIds = emptySet())
        if (owner.isBlank()) return this
        val newIds = emails.filter { email ->
            email.eligible && email.owner == owner && email.id > 0 &&
                email.receivedAtMillis != null && email.receivedAtMillis > lastOpenedAtMillis &&
                email.id !in arrivalIds
        }.map(BadgeEmail::id).toSet()
        if (newIds.isEmpty()) return this
        if (foreground) return copy(arrivalIds = arrivalIds + newIds)
        return copy(count = saturatingBadgeCount(count, newIds.size), emailIds = emailIds + newIds,
            arrivalIds = arrivalIds + newIds)
    }

    fun opened(atMillis: Long): EmailBadgeState = copy(
        lastOpenedAtMillis = atMillis.coerceAtLeast(0), count = 0, emailIds = emptySet(), arrivalIds = emptySet(),
    )

    fun remove(ids: Collection<Int>): EmailBadgeState {
        val removed = emailIds.intersect(ids.toSet())
        if (removed.isEmpty()) return this
        return copy(count = (count.toLong() - removed.size).coerceAtLeast(0).toInt(), emailIds = emailIds - removed)
    }

    fun statusChanged(ids: Collection<Int>, status: String): EmailBadgeState =
        if (status in setOf("READ", "ARCHIVED", "DELETED")) remove(ids) else this
}

internal fun saturatingBadgeCount(count: Int, additions: Int): Int =
    (count.coerceAtLeast(0).toLong() + additions.coerceAtLeast(0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun badgeReceivedAtMillis(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
