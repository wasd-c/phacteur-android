package app.phacteur.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EmailQuickActionsTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val received = "2026-10-03T09:59:00Z"

    @Test
    fun `numeric OTP retains leading zeroes and normalizes presentation separators`() {
        assertEquals(listOf(EmailQuickAction.CopyCode("001234")), detect("Your verification code is: 001234."))
        assertEquals(listOf(EmailQuickAction.CopyCode("001234")), detect("Code de vérification : 001 234"))
        assertEquals(listOf(EmailQuickAction.CopyCode("001234")), detect("One-time code: 001-234"))
    }

    @Test
    fun `unique standalone code is supported and repeated same code is deduplicated`() {
        assertEquals(listOf(EmailQuickAction.CopyCode("012345")), detect("Use this code to sign in:\n012345\n"))
        assertEquals(listOf(EmailQuickAction.CopyCode("012345")), detect("Verification code: 012345\nYour code is 012345"))
        assertTrue(detect("Verification code: 012345\nSecurity code: 543210").isEmpty())
        assertTrue(detect("Use this code to sign in:\n012345\n543210").isEmpty())
    }

    @Test
    fun `ordinary numbers are never inferred as authentication codes`() {
        val messages = listOf(
            "Your order number is 123456", "Tracking code: 123456", "Invoice code: 123456",
            "Price code: 1234.50 €", "Code: 2026-10-03", "Téléphone code: 06123456",
            "Code: 06 12 34 56", "Your code: 1234.50", "Order code: 123456",
            "Security notification\nAmount\n1234", "Security notification\nTéléphone\n06123456",
        )
        messages.forEach { body -> assertTrue(body, detect(body).isEmpty()) }
        assertTrue(emailQuickActions("Order 123456", "Your code: 123456", null, received, "RECEIVED", now).isEmpty())
    }

    @Test
    fun `outgoing invalid future and expired mail has no one click authentication action`() {
        assertTrue(emailQuickActions("Sign in", "Verification code: 123456", null, received, "SENT", now).isEmpty())
        assertTrue(emailQuickActions("Sign in", "Verification code: 123456", null, "invalid", "RECEIVED", now).isEmpty())
        assertTrue(emailQuickActions("Sign in", "Verification code: 123456", null, "2026-10-03T10:06:00Z", "RECEIVED", now).isEmpty())
        assertTrue(emailQuickActions("Sign in", "Verification code: 123456", null, "2026-10-02T09:59:00Z", "RECEIVED", now).isEmpty())
        assertTrue(emailQuickActions("Sign in", "Verification code: 123456. Expires in 5 minutes.", null, "2026-10-03T09:54:00Z", "RECEIVED", now).isEmpty())
        assertEquals(listOf(EmailQuickAction.CopyCode("123456")), emailQuickActions("Sign in", "Verification code: 123456. Valid for 5 minutes.", null, "2026-10-03T09:56:00Z", "RECEIVED", now))
    }

    @Test
    fun `decoded HTML supplies a code and a single authentication link without leaking footer links`() {
        val html = "<p>Code de v&#233;rification : <strong>001234</strong></p>" +
            "<a href='https://accounts.example.com/auth?token=private&amp;v=1'>Sign in</a>" +
            "<footer><a href='https://accounts.example.com/unsubscribe'>Unsubscribe</a></footer>"
        assertEquals(
            listOf(EmailQuickAction.CopyCode("001234"), EmailQuickAction.FollowLink("https://accounts.example.com/auth?token=private&v=1", "Ouvrir le lien")),
            detect("", html),
        )
    }

    @Test
    fun `legacy HTML in body is decoded safely too`() {
        assertEquals(listOf(EmailQuickAction.CopyCode("001234")), detect("<p>Code de v&#233;rification : <b>001234</b></p>"))
    }

    @Test
    fun `quoted scripts style footers and plain text replies do not supply actions`() {
        val html = "<p>Nothing actionable</p><blockquote><blockquote>Old mail</blockquote>" +
            "<p>Verification code: 123456</p><a href='https://accounts.example.com/auth'>Sign in</a></blockquote>" +
            "<script>Verification code: 123456</script><style>Verification code: 123456</style>" +
            "<footer><a href='https://accounts.example.com/auth'>Sign in</a></footer>"
        assertTrue(detect("", html).isEmpty())
        assertTrue(detect("Nothing actionable\nOn someone wrote:\nVerification code: 123456").isEmpty())
        assertTrue(detect("Nothing actionable\n--\nVerification code: 123456").isEmpty())
    }

    @Test
    fun `auth link can come from label or specific path and duplicates stay unique`() {
        val url = "https://accounts.example.com/verify?token=private"
        assertEquals(listOf(EmailQuickAction.FollowLink(url, "Ouvrir le lien")), detect("Sign in: $url."))
        assertEquals(listOf(EmailQuickAction.FollowLink(url, "Ouvrir le lien")), detect("Sign in: $url", "<a href='$url'>Verify account</a>"))
        assertEquals(listOf(EmailQuickAction.FollowLink("https://accounts.example.com/session", "Ouvrir le lien")), detect("Sign in below", "<a href='https://accounts.example.com/session'>Sign in</a>"))
        assertTrue(detect("Sign in below", "<a href='https://accounts.example.com/help'>Help</a>").isEmpty())
        assertTrue(detect("Sign in: https://accounts.example.com/verify?a=1\nhttps://accounts.example.com/verify?a=2").isEmpty())
    }

    @Test
    fun `only https public hostname URLs without embedded credentials are accepted`() {
        assertEquals("https://accounts.example.com/auth?token=private&v=1", safeEmailActionUrl("https://ACCOUNTS.EXAMPLE.COM:443/auth?token=private&amp;v=1"))
        assertEquals("https://xn--bcher-kva.example/auth", safeEmailActionUrl("https://bücher.example/auth"))
        val unsafe = listOf(
            "javascript:alert(1)", "file:///etc/passwd", "intent://example.com/auth", "http://example.com/auth",
            "https://user:password@example.com/auth", "https://user@example.com/auth", "https://example.com:444/auth",
            "https://localhost/auth", "https://service.localhost/auth", "https://service.local/auth",
            "https://service.internal/auth", "https://service.lan/auth", "https://router.home.arpa/auth",
            "https://intranet/auth", "https://127.0.0.1/auth", "https://10.0.0.1/auth", "https://192.168.1.1/auth",
            "https://[::1]/auth", "https://0x7f.0.0.1/auth", "https://2130706433/auth", "https://example.com./auth",
            "https://example.com\\@localhost/auth", "https://example.com/\n/auth", "https://example.com:443:443/auth",
        )
        unsafe.forEach { url -> assertNull(url, safeEmailActionUrl(url)) }
    }

    @Test
    fun `unsubscribe and image tracking URLs cannot become follow link buttons`() {
        val urls = listOf(
            "https://example.com/unsubscribe", "https://example.com/opt_out",
            "https://example.com/%75nsubscribe", "https://example.com/verify?action=unsubscribe",
            "https://example.com/pixel.gif", "https://example.com/tracking/open", "https://example.com/beacon",
        )
        urls.forEach { url ->
            assertNull(url, safeEmailActionUrl(url))
            assertTrue(url, detect("Sign in below", "<a href='$url'>Sign in</a>").isEmpty())
        }
        assertTrue(detect("Sign in below", "<img src='https://example.com/auth?pixel=1' width='1' height='1'>").isEmpty())
    }

    @Test
    fun `actions are recomputed at execution time to avoid copying an expired code`() {
        val body = "Verification code: 001234. Expires in 5 minutes."
        val first = emailQuickActions("Sign in", body, null, received, "RECEIVED", now)
        assertEquals(listOf(EmailQuickAction.CopyCode("001234")), first)
        assertTrue(emailQuickActions("Sign in", body, null, received, "RECEIVED", now.plusSeconds(300)).isEmpty())
    }

    @Test
    fun `meeting links support exact Meet Zoom and Teams destinations without authentication context`() {
        val urls = listOf(
            "https://meet.google.com/abc-defg-hij",
            "https://zoom.us/j/123456789?pwd=fixture",
            "https://company.zoom.us/j/123456789",
            "https://company.zoom.us/my/room",
            "https://teams.microsoft.com/l/meetup-join/fixture?context=%7B%7D",
        )
        urls.forEach { url -> assertEquals(listOf(EmailQuickAction.JoinMeeting(url)), meeting(url)) }
    }

    @Test
    fun `meeting domain spoofing wrong paths and unsafe links never create a meeting action`() {
        val urls = listOf(
            "https://meet.google.com.evil.example/abc-defg-hij",
            "https://evilmeet.google.com/abc-defg-hij",
            "https://meet.google.com@evil.example/abc-defg-hij",
            "https://zoom.us.evil.example/j/123456789",
            "https://evilzoom.us/j/123456789",
            "https://teams.microsoft.com.evil.example/l/meetup-join/fixture",
            "https://meet.google.com/abc-defg-hij/",
            "https://meet.google.com/ABC-DEFG-HIJ",
            "https://meet.google.com/%61bc-defg-hij",
            "https://zoom.us/J/123456789",
            "https://zoom.us/rooms/123456789",
            "https://teams.microsoft.com/l/meetup-join",
            "http://meet.google.com/abc-defg-hij",
            "https://zoom.us:444/j/123456789",
        )
        urls.forEach { url -> assertTrue(url, meeting(url).isEmpty()) }
    }

    @Test
    fun `a valid message date is mandatory for authentication and meeting actions`() {
        val body = "Verification code: 001234\nhttps://meet.google.com/abc-defg-hij"
        listOf(null, "", "invalid", "2026-10-03T10:06:00Z").forEach { date ->
            assertTrue(emailQuickActions("Sign in", body, null, date, "RECEIVED", now).isEmpty())
        }
        assertTrue(emailQuickActions("Réunion", body, null, received, "SENT", now).isEmpty())
    }

    @Test
    fun `meetings expire after seven days and are revalidated using the current time`() {
        val url = "https://meet.google.com/abc-defg-hij"
        val atBoundary = now.minusSeconds(7 * 86_400L).toString()
        assertEquals(listOf(EmailQuickAction.JoinMeeting(url)), meeting(url, receivedAt = atBoundary))
        assertTrue(meeting(url, receivedAt = atBoundary, at = now.plusMillis(1)).isEmpty())
        assertTrue(meeting(url, receivedAt = now.minusSeconds(8 * 86_400L).toString()).isEmpty())
    }

    @Test
    fun `expired authentication does not suppress a meeting with its longer lifetime`() {
        val url = "https://meet.google.com/abc-defg-hij"
        val body = "Verification code: 001234. Sign in: https://accounts.example.com/auth\n$url"
        assertEquals(listOf(EmailQuickAction.JoinMeeting(url)), emailQuickActions(
            "Sign in", body, null, now.minusSeconds(2 * 86_400L).toString(), "RECEIVED", now,
        ))
        assertEquals(listOf(EmailQuickAction.JoinMeeting(url)), emailQuickActions(
            "Sign in", "$body\nExpires in 1 minute.", null, now.minusSeconds(120).toString(), "RECEIVED", now,
        ))
    }

    @Test
    fun `quoted and footer meeting URLs are ignored before scanning HTML links`() {
        val stale = "https://meet.google.com/abc-defg-hij"
        val current = "https://company.zoom.us/j/123456789?pwd=fixture&v=1"
        val quotes = "<blockquote><blockquote>Ancien message</blockquote><a href='$stale'>Réunion</a></blockquote>" +
            "<footer><a href='$stale'>Réunion</a></footer><script>$stale</script><style>$stale</style>"
        assertTrue(meeting("", html = "<p>Bonjour</p>$quotes").isEmpty())
        assertEquals(listOf(EmailQuickAction.JoinMeeting(current)), meeting("", html =
            "<p><a href='https://company.zoom.us/j/123456789?pwd=fixture&amp;v=1'>Rejoindre</a></p>$quotes",
        ))
        assertTrue(meeting("Bonjour\nOn Alice wrote:\n$stale").isEmpty())
    }

    @Test
    fun `a message offers at most code authentication and one meeting in this order`() {
        val auth = "https://accounts.example.com/auth"
        val firstMeeting = "https://meet.google.com/abc-defg-hij"
        val body = "Verification code: 001234\nSign in: $auth\n$firstMeeting\nhttps://zoom.us/j/123456789"
        assertEquals(listOf(
            EmailQuickAction.CopyCode("001234"),
            EmailQuickAction.FollowLink(auth, "Ouvrir le lien"),
            EmailQuickAction.JoinMeeting(firstMeeting),
        ), detect(body))
    }

    private fun meeting(body: String, html: String? = null, receivedAt: String = received, at: Instant = now) =
        emailQuickActions("Invitation à une réunion", body, html, receivedAt, "RECEIVED", at)

    private fun detect(body: String, htmlBody: String? = null) =
        emailQuickActions("Sign in", body, htmlBody, received, "RECEIVED", now)
}
