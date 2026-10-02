package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class CalendarModelsTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `October calendar includes neighboring days as complete Monday to Sunday weeks`() {
        val days = calendarDays(YearMonth.of(2026, 10))

        assertEquals(LocalDate.of(2026, 9, 28), days.first())
        assertEquals(LocalDate.of(2026, 11, 1), days.last())
        assertEquals(35, days.size)
        assertEquals(days.size, days.distinct().size)
    }

    @Test
    fun `range includes the final local day across the daylight saving transition`() {
        val parameters = CalendarRange.forMonth(YearMonth.of(2026, 10), paris).parameters()

        assertEquals("2026-09-27T22:00:00Z", parameters["start"])
        assertEquals("2026-11-01T22:59:59.999Z", parameters["end"])
    }

    @Test
    fun `leap day is included in a February calendar`() {
        assertTrue(LocalDate.of(2028, 2, 29) in calendarDays(YearMonth.of(2028, 2)))
    }

    @Test
    fun `events are grouped by local day instead of UTC day`() {
        val event = event(startsAt = "2026-10-02T23:30:00Z")

        assertEquals(LocalDate.of(2026, 10, 3), event.date(paris))
        assertEquals(LocalDate.of(2026, 10, 2), event.date(ZoneId.of("UTC")))
    }

    @Test
    fun `all day follows local midnight and timed events display their end`() {
        assertEquals("Toute la journée", event("2026-10-01T22:00:00Z").timeLabel(paris, Locale.FRANCE))
        assertEquals("09:00 – 11:00", event("2026-10-02T07:00:00Z", "2026-10-02T09:00:00Z").timeLabel(paris, Locale.FRANCE))
    }

    @Test
    fun `invalid dates cannot crash the calendar`() {
        val event = event("invalid")

        assertNull(event.date(paris))
        assertEquals("Date indisponible", event.timeLabel(paris))
    }

    @Test
    fun `external details permit only absolute web URLs`() {
        val event = event("2026-10-02T09:00:00Z")

        assertEquals("https://carrier.example/item/42", event.copy(externalUrl = "https://carrier.example/item/42").safeExternalUrl)
        assertEquals("http://carrier.example/item/42", event.copy(externalUrl = "http://carrier.example/item/42").safeExternalUrl)
        listOf("javascript:alert(1)", "file:///tmp/details", "intent://details", "/details", "https://", "https://carrier.example/with space").forEach { url ->
            assertNull(url, event.copy(externalUrl = url).safeExternalUrl)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range rejects intervals above server maximum`() {
        CalendarRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(401L * 24 * 60 * 60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `range rejects backwards intervals`() {
        CalendarRange(Instant.parse("2026-10-03T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"))
    }

    private fun event(startsAt: String, endsAt: String? = null) = MailCalendarEvent(
        id = "tracking:test",
        kind = "flight",
        title = "Vol pour Paris",
        description = null,
        status = "confirmed",
        startsAt = startsAt,
        endsAt = endsAt,
        location = "Lyon → Paris",
        source = "Compagnie",
        confirmationCode = null,
        externalUrl = null,
        relatedEmailId = 42,
        relatedThreadId = null,
        relatedSubject = "Confirmation de vol",
    )
}
