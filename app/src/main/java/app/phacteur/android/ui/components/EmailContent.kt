package app.phacteur.android.ui.components

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.parser.Tag
import java.net.URI

/** Prefer the explicit MIME alternative, but support older messages with HTML in body. */
internal fun emailHtml(body: String, htmlBody: String?): String? =
    htmlBody?.takeIf(String::isNotBlank) ?: body.takeIf { source ->
        htmlTag.findAll(source).any { Tag.isKnownTag(it.groupValues[1].lowercase()) }
    }

private val htmlTag = Regex("</?([a-zA-Z][a-zA-Z0-9]*)(?=[\\s/>])[^>]*>")
private val whitespace = Regex("[\\s\\u00a0]+")

internal fun emailPreview(body: String, htmlBody: String? = null): String {
    val source = body.takeIf(String::isNotBlank) ?: htmlBody.orEmpty()
    val text = if (body.isBlank() && !htmlBody.isNullOrBlank() || emailHtml(source, null) != null) {
        Jsoup.parse(source).apply { select("head, script, style, template").remove() }.body().text()
    } else source
    return text.replace(whitespace, " ").trim()
}

internal fun isExternalEmailLink(url: String): Boolean = runCatching {
    val uri = URI(url)
    when (uri.scheme?.lowercase()) {
        "https", "http" -> !uri.host.isNullOrBlank()
        "mailto" -> !uri.rawSchemeSpecificPart.isNullOrBlank()
        else -> false
    }
}.getOrDefault(false)

internal const val EMAIL_BASE_URL = "https://email.invalid/"

/** Keep email layout/CSS, while the WebView separately enforces an inert document. */
internal fun emailHtmlDocument(html: String): String {
    val document = Jsoup.parse(html)
    document.outputSettings().prettyPrint(false)
    document.select("script, iframe, frame, frameset, object, embed, applet, " +
        "form, input, button, textarea, select, video, audio, source, track, " +
        "base, meta, link, template").remove()
    for (element in document.allElements) {
        for (attribute in element.attributes().asList()) {
            if (attribute.key.startsWith("on", ignoreCase = true) ||
                attribute.key in setOf("srcdoc", "ping", "download", "target")) {
                element.removeAttr(attribute.key)
            }
        }
        if (element.hasAttr("href") && !isExternalEmailLink(element.attr("href"))) {
            element.removeAttr("href")
        }
    }
    // Prepend our policy before any sender-provided CSS or image can load.
    document.head().prependElement("meta")
        .attr("http-equiv", "Content-Security-Policy")
        .attr("content", "default-src 'none'; style-src 'unsafe-inline'; " +
            "img-src data:; base-uri 'none'; form-action 'none'; frame-src 'none'")
    document.head().prependElement("meta").attr("charset", "utf-8")
    document.head().appendElement("meta").attr("name", "viewport")
        .attr("content", "width=device-width, initial-scale=1")
    document.head().appendElement("style").appendChild(DataNode("""
        html { color-scheme: light; background: white; color: #202124; }
        body { margin: 0; padding: 8px; font-family: sans-serif; font-size: 16px; overflow-wrap: anywhere; }
        img { max-width: 100% !important; height: auto !important; }
        table { max-width: 100% !important; }
        .remote-image-blocked::after { content: attr(aria-label); color: #666; }
        pre { white-space: pre-wrap; overflow-wrap: anywhere; }
    """.trimIndent()))
    return document.outerHtml()
}
