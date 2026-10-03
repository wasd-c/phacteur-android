package app.phacteur.android.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.IDN
import java.net.URI
import java.time.Instant
import java.util.Locale

sealed interface EmailQuickAction {
    data class CopyCode(val code: String) : EmailQuickAction
    data class FollowLink(val url: String, val label: String) : EmailQuickAction
    data class JoinMeeting(val url: String) : EmailQuickAction
}

/** Local inference only: codes and authentication links must never enter activity logs. */
fun emailQuickActions(
    subject: String,
    body: String,
    htmlBody: String?,
    receivedAt: String? = null,
    direction: String? = null,
    now: Instant = Instant.now(),
): List<EmailQuickAction> {
    if (direction.equals("SENT", ignoreCase = true)) return emptyList()
    val received = receivedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return emptyList()
    val age = runCatching { Math.subtractExact(now.toEpochMilli(), received.toEpochMilli()) }
        .getOrNull() ?: return emptyList()
    if (age < -5 * 60_000L) return emptyList()
    val html = actionableHtml(htmlBody?.takeIf(String::isNotBlank)
        ?: body.takeIf { htmlTag.containsMatchIn(it) }.orEmpty())
    val text = mainContent(if (body.isNotBlank() && !htmlTag.containsMatchIn(body)) body else messageText(html))
    val context = "${subject.take(10_000)}\n$text"
    val expiryMinutes = expiry.find(context)?.groupValues?.get(1)?.toIntOrNull()
    val lifetime = (expiryMinutes?.coerceIn(1, 1_440)?.toLong()?.times(60_000L)) ?: 24 * 60 * 60_000L

    val actions = mutableListOf<EmailQuickAction>()
    if (age <= lifetime && authContext.containsMatchIn(context)) {
        val codes = linkedSetOf<String>()
        for (match in codeLabel.findAll(context)) {
            val raw = match.groupValues[1]
            val code = raw.replace(" ", "").replace("-", "")
            if (asciiCode.matches(code) && !misleadingNumber(context, match.range, raw)) codes += code
        }
        if (codes.isEmpty()) {
            for (match in standaloneCode.findAll(text)) {
                if (!misleadingNumber(text, match.range, match.groupValues[1], inspectPreviousLine = true)) codes += match.groupValues[1]
            }
        }
        if (codes.size == 1) actions += EmailQuickAction.CopyCode(codes.single())

        val links = linkedSetOf<String>()
        val document = Jsoup.parseBodyFragment(html)
        for (anchor in document.select("a[href]")) {
            val url = safeEmailActionUrl(anchor.attr("href")) ?: continue
            if (authContext.containsMatchIn(anchor.text()) || authenticationPath.containsMatchIn(URI(url).path)) {
                links += url
            }
        }
        for (match in textUrl.findAll(text)) {
            val url = safeEmailActionUrl(match.value.trimEnd('.', ',', ';', ')')) ?: continue
            if (authenticationPath.containsMatchIn(URI(url).path)) links += url
        }
        if (links.size == 1) actions += EmailQuickAction.FollowLink(links.single(), "Ouvrir le lien")
    }
    if (age <= 7 * 24 * 60 * 60_000L) {
        // Authentication may already have expired while the meeting invitation is still current.
        for (match in textUrl.findAll("$text\n$html")) {
            val url = safeEmailActionUrl(match.value.trimEnd('.', ',', ';', ')')) ?: continue
            val parsed = URI(url)
            val host = parsed.host ?: continue
            val path = parsed.rawPath.orEmpty()
            val meeting = host == "meet.google.com" && googleMeetPath.matches(path) ||
                (host == "zoom.us" || host.endsWith(".zoom.us")) && zoomMeetingPath.containsMatchIn(path) ||
                host == "teams.microsoft.com" && path.startsWith("/l/meetup-join/")
            if (meeting) {
                actions += EmailQuickAction.JoinMeeting(url)
                break
            }
        }
    }
    return actions.take(3)
}

/** HTTPS browser destinations only; this performs no DNS lookup or network request. */
fun safeEmailActionUrl(value: String): String? = runCatching {
    val decoded = Parser.unescapeEntities(value.trim(), false)
    if (decoded.length > 8_192 || decoded.any { it.isWhitespace() || it.code < 32 || it.code == 127 || it == '\\' }) {
        return null
    }
    val uri = URI(decoded)
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val authority = uri.rawAuthority ?: return null
    if (authority.contains('@') || authority.contains('[') || authority.contains(']') || authority.contains('%')) return null
    val pieces = authority.split(':')
    if (pieces.size > 2 || pieces.size == 2 && pieces[1] != "443") return null
    val host = IDN.toASCII(pieces[0], IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    if (host.length > 253 || host.endsWith('.') || !host.contains('.') ||
        host.split('.').any { it.isEmpty() || it.length > 63 } ||
        host == "localhost" || host.endsWith(".localhost") ||
        listOf(".local", ".internal", ".lan", ".home", ".home.arpa").any(host::endsWith) ||
        numericHost.matches(host) || host.split('.').all { numericAddressPart.matches(it) }) return null
    val path = uri.path.orEmpty()
    if (unwantedPath.containsMatchIn(path) || imagePath.containsMatchIn(path) ||
        unsubscribe.containsMatchIn(uri.query.orEmpty())) return null
    buildString {
        append("https://")
        append(host)
        append(uri.rawPath?.takeIf(String::isNotBlank) ?: "/")
        uri.rawQuery?.let { append('?'); append(it) }
        uri.rawFragment?.let { append('#'); append(it) }
    }.let { URI(it).normalize().toASCIIString() }
}.getOrNull()

private fun actionableHtml(value: String): String {
    val document = Jsoup.parseBodyFragment(value.take(100_000))
    document.outputSettings().prettyPrint(false)
    document.select("blockquote, footer, script, style, head, template, noscript, iframe, object, svg").remove()
    val html = document.body().html()
    val cutoff = htmlFooter.find(html)?.range?.first ?: html.length
    return html.take(cutoff)
}

private fun messageText(html: String): String = Parser.unescapeEntities(
    html.replace(lineBreak, "\n").replace(anyTag, " "), false,
)

private fun mainContent(value: String): String = value.take(mainFooter.find(value)?.range?.first ?: value.length)
    .take(30_000)

private fun misleadingNumber(text: String, range: IntRange, raw: String, inspectPreviousLine: Boolean = false): Boolean {
    if (formattedDate.matches(raw.trim()) || phoneGroups.matches(raw.trim())) return true
    val lineStart = text.lastIndexOf('\n', range.first).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', range.last + 1).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    if (ordinaryNumberContext.containsMatchIn(line)) return true
    if (inspectPreviousLine && lineStart > 0) {
        val previousStart = text.lastIndexOf('\n', lineStart - 2).let { if (it < 0) 0 else it + 1 }
        if (ordinaryNumberContext.containsMatchIn(text.substring(previousStart, lineStart))) return true
    }
    val tail = text.substring((range.last + 1).coerceAtMost(text.length), lineEnd)
    return decimalTail.containsMatchIn(tail)
}

private val options = setOf(RegexOption.IGNORE_CASE)
private val htmlTag = Regex("</?(?:html|body|div|p|br|table|a|span|blockquote|h[1-6])(?=[\\s/>])", options)
private val anyTag = Regex("<[^>]*>")
private val lineBreak = Regex("<(?:br|/p|/div|/tr|/h[1-6])\\b[^>]*>", options)
private val htmlFooter = Regex("unsubscribe|se désabonner|registered office|siège social|privacy[- ]policy|politique de confidentialité", options)
private val mainFooter = Regex("(?:^|\\n)\\s*(?:unsubscribe|se désabonner|désabonnement|registered office|siège social|privacy policy|politique de confidentialité|on .+ wrote:|le .+ a écrit\\s*:|--\\s*$)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
private val authContext = Regex("verification|security|authentication|confirmation|one[- ]time|sign[- ]?in|log[- ]?in|connexion|vérification|authentification|confirmer|otp|passcode|magic link|reset (?:your )?password|réinitialiser", options)
private val codeLabel = Regex("(?:verification code|security code|confirmation code|authentication code|one[- ]time (?:code|password)|sign[- ]?in code|login code|code (?:de |d[’'])(?:vérification|sécurité|confirmation|connexion|authentification)|passcode|otp|your code|votre code|code)\\s*(?:(?:is|est)\\s*)?(?::|=|-)?\\s*([0-9](?:[0-9 -]{2,12})[0-9])\\b", options)
private val asciiCode = Regex("[0-9]{4,8}")
private val standaloneCode = Regex("(?:^|\\n)[ \\t]*([0-9]{4,8})[ \\t]*(?=\\n|$)")
private val expiry = Regex("(?:expire[sd]?|valid|valable|expire)\\s*(?:in|for|dans|pendant|après)?\\s*([0-9]{1,3})\\s*(?:minutes?|mins?|mn)\\b", options)
private val textUrl = Regex("https://[^\\s<>\"']+", options)
private val authenticationPath = Regex("/(?:verify|confirm|magic[-_]?link|login|signin|sign-in|auth|reset-password)(?:[/?#_-]|$)", options)
private val googleMeetPath = Regex("/[a-z]{3}-[a-z]{4}-[a-z]{3}")
private val zoomMeetingPath = Regex("^/(?:j|my)/")
private val numericHost = Regex("[0-9.]+")
private val numericAddressPart = Regex("(?:[0-9]+|0x[0-9a-f]+)", options)
private val unsubscribe = Regex("unsubscribe|opt[-_]?out", options)
private val unwantedPath = Regex("unsubscribe|opt[-_]?out|/(?:track(?:ing)?|pixel|beacon|open)(?:[/_.-]|$)", options)
private val imagePath = Regex("\\.(?:gif|png|jpe?g|webp|svg|ico)(?:$|/)", options)
private val formattedDate = Regex("(?:[0-9]{4}[- ][0-9]{1,2}[- ][0-9]{1,2}|[0-9]{1,2}[- ][0-9]{1,2}[- ][0-9]{4})")
private val phoneGroups = Regex("[0-9]{2}(?: [0-9]{2}){3,}")
private val ordinaryNumberContext = Regex("tracking|shipment|suivi|order (?:code|number|id)|commande|invoice|facture|siret|siren|téléphone|telephone|phone|(?:^|\\W)tel\\b|price|prix|total|amount|montant|(?:€|\\$|£)", options)
private val decimalTail = Regex("^[.,][0-9]")
