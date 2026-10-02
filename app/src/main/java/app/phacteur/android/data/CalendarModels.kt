package app.phacteur.android.data

import org.json.JSONObject
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Dates detected by Phacteur from delivery, travel, reservation and ticket mail. */
data class MailCalendarEvent(
    val id: String,
    val kind: String,
    val title: String,
    val description: String?,
    val status: String,
    val startsAt: String,
    val endsAt: String?,
    val location: String?,
    val source: String,
    val confirmationCode: String?,
    val externalUrl: String?,
    val relatedEmailId: Int?,
    val relatedThreadId: String?,
    val relatedSubject: String?,
) {
    fun date(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate? =
        parseCalendarInstant(startsAt)?.atZone(zoneId)?.toLocalDate()

    fun timeLabel(
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val start = parseCalendarInstant(startsAt)?.atZone(zoneId) ?: return "Date indisponible"
        // The web calendar uses a midnight start to represent an all-day event.
        if (start.hour == 0 && start.minute == 0) return "Toute la journée"
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val end = endsAt?.let(::parseCalendarInstant)?.atZone(zoneId)
        return if (end == null) formatter.format(start)
        else "${formatter.format(start)} – ${formatter.format(end)}"
    }

    /** The API also sanitizes URLs; retain that restriction before opening an Android intent. */
    val safeExternalUrl: String?
        get() = externalUrl?.takeIf { value ->
            runCatching {
                val uri = URI(value)
                uri.scheme?.lowercase(Locale.ROOT) in setOf("https", "http") && !uri.host.isNullOrBlank()
            }.getOrDefault(false)
        }

    val categoryLabel: String
        get() = when (kind.lowercase(Locale.ROOT)) {
            "flight" -> "Vol"
            "package" -> "Livraison"
            "train" -> "Train"
            "bus" -> "Autocar"
            "hotel" -> "Hôtel"
            "reservation" -> "Réservation"
            "concert" -> "Concert"
            "sports" -> "Sport"
            "theater" -> "Spectacle"
            "conference" -> "Conférence"
            else -> "Événement"
        }
}

/** The server includes both range boundaries and accepts at most 400 days. */
data class CalendarRange(val start: Instant, val end: Instant) {
    init {
        require(start < end && Duration.between(start, end) <= Duration.ofDays(400)) {
            "Plage de calendrier invalide"
        }
    }

    fun parameters(): Map<String, String> = mapOf("start" to start.toString(), "end" to end.toString())

    companion object {
        fun forMonth(month: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): CalendarRange {
            val days = calendarDays(month)
            return CalendarRange(
                start = days.first().atStartOfDay(zoneId).toInstant(),
                end = days.last().plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1),
            )
        }
    }
}

/** Complete Monday–Sunday weeks, including the neighboring month cells shown on the web. */
fun calendarDays(month: YearMonth): List<LocalDate> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val start = first.minusDays(first.dayOfWeek.value - 1L)
    val end = last.plusDays(7L - last.dayOfWeek.value)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
}

internal fun parseCalendarEvent(value: JSONObject) = MailCalendarEvent(
    id = value.getString("id"),
    kind = value.optString("kind", "event"),
    title = value.optionalString("title") ?: "Événement",
    description = value.optionalString("description"),
    status = value.optString("status"),
    startsAt = value.getString("startsAt"),
    endsAt = value.optionalString("endsAt"),
    location = value.optionalString("location"),
    source = value.optionalString("source") ?: "Détecté dans les mails",
    confirmationCode = value.optionalString("confirmationCode"),
    externalUrl = value.optionalString("externalUrl"),
    relatedEmailId = value.optInt("relatedEmailId").takeIf { it > 0 },
    relatedThreadId = value.optionalString("relatedThreadId"),
    relatedSubject = value.optionalString("relatedSubject"),
)

private fun parseCalendarInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
