package app.phacteur.android.ui.components

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailContentTest {
    @Test
    fun `explicit HTML alternative takes precedence over plain body`() {
        assertEquals("<p>Formatted message</p>", emailHtml("Plain message", "<p>Formatted message</p>"))
        assertEquals("Tom &amp; Jerry", emailHtml("Plain message", "Tom &amp; Jerry"))
    }

    @Test
    fun `legacy messages with HTML in body are detected`() {
        val html = "<html><body><table><tr><td>Message</td></tr></table></body></html>"

        assertEquals(html, emailHtml(html, null))
        assertEquals(html, emailHtml(html, " \n\t"))
        assertEquals("<p>Message</p>", emailHtml("<p>Message</p>", null))
        assertEquals("<DIV>Message</DIV>", emailHtml("<DIV>Message</DIV>", null))
    }

    @Test
    fun `plain comparisons and angle bracket email addresses stay literal`() {
        val messages = listOf(
            "2 < 3 and 5 > 4",
            "a < b and c > d",
            "Contact Alice <alice@example.com>",
            "Contact Alice <a@example.com>",
            "Write to <b@example.com> for help",
        )

        for (message in messages) {
            assertNull(message, emailHtml(message, null))
            assertEquals(message, emailPreview(message))
        }
    }

    @Test
    fun `preview prefers plain text and normalizes whitespace`() {
        assertEquals(
            "Plain text & literal &amp;",
            emailPreview("  Plain\n text\t&\u00a0literal &amp;  ", "<p>HTML alternative</p>"),
        )
    }

    @Test
    fun `preview falls back to HTML and decodes entities`() {
        assertEquals(
            "Bonjour & bienvenue à tous <3",
            emailPreview(" \n", "<p>Bonjour &amp; bienvenue&nbsp;&#224; tous &lt;3</p>"),
        )
        assertEquals("Tom & Jerry", emailPreview("", "Tom &amp; Jerry"))
        assertEquals("", emailPreview("", null))
    }

    @Test
    fun `HTML preview keeps visible content and excludes document metadata and code`() {
        val html = """
            <html><head><title>Hidden title</title><style>p { color: red; }</style></head>
            <body><script>hiddenScript()</script><p>First &amp; second</p>
            <template>Hidden template</template><table><tr><td>Third</td><td>Fourth</td></tr></table>
            </body></html>
        """.trimIndent()

        assertEquals("First & second Third Fourth", emailPreview(html))
        assertEquals("First & second Third Fourth", emailPreview("", html))
    }

    @Test
    fun `HTML document retains email text tables inline styles and embedded CSS`() {
        val document = Jsoup.parse(emailHtmlDocument("""
            <html><head><style>.offer { color: red; }</style></head><body>
            <table class="offer" style="background: #f0f0f0"><tr><td><strong>Bonjour &amp; merci</strong></td></tr></table>
            <img src="https://example.com/logo.png" alt="Logo">
            </body></html>
        """.trimIndent()))

        assertEquals("Bonjour & merci", document.select("table td strong").text())
        assertEquals("background: #f0f0f0", document.select("table.offer").attr("style"))
        assertTrue(document.select("head style").any { it.data().contains(".offer { color: red; }") })
        assertEquals("https://example.com/logo.png", document.select("img").attr("src"))
        assertEquals("Logo", document.select("img").attr("alt"))
    }

    @Test
    fun `HTML document removes active elements and sender supplied navigation metadata`() {
        val document = Jsoup.parse(emailHtmlDocument("""
            <html><head><base href="https://evil.example/"><meta http-equiv="refresh" content="0;url=https://evil.example/">
            <meta http-equiv="Content-Security-Policy" content="default-src *"><link rel="stylesheet" href="https://evil.example/style.css"></head>
            <body><p>Visible message</p><script>alert(1)</script><iframe src="https://evil.example/"></iframe>
            <object data="https://evil.example/"></object><embed src="https://evil.example/">
            <form action="https://evil.example/"><input value="secret"><button>Send</button><textarea>Draft</textarea><select><option>Option</option></select></form>
            <video src="https://evil.example/video"><source src="https://evil.example/source"><track src="https://evil.example/track"></video>
            <audio src="https://evil.example/audio"></audio><template>Hidden</template></body></html>
        """.trimIndent()))

        assertEquals("Visible message", document.body().text())
        assertTrue(document.select("script, iframe, object, embed, form, input, button, textarea, select, video, audio, source, track, base, link, template").isEmpty())
        assertTrue(document.select("meta[http-equiv=refresh]").isEmpty())
        assertEquals(1, document.select("meta[http-equiv=Content-Security-Policy]").size)
    }

    @Test
    fun `HTML document removes event handlers and active link attributes`() {
        val document = Jsoup.parse(emailHtmlDocument("""
            <p onclick="alert(1)">Message</p><img src="data:image/png;base64,AA==" onerror="alert(2)">
            <a href="https://example.com/page" target="_blank" download="file" ping="https://evil.example/" onmouseover="alert(3)" srcdoc="active">Open</a>
        """.trimIndent()))

        assertEquals("https://example.com/page", document.select("a").attr("href"))
        for (element in document.allElements) {
            assertFalse(element.attributes().any { it.key.startsWith("on", ignoreCase = true) })
            for (attribute in listOf("target", "download", "ping", "srcdoc")) {
                assertFalse(attribute, element.hasAttr(attribute))
            }
        }
    }

    @Test
    fun `HTML document removes unsafe links and preserves supported external links`() {
        val document = Jsoup.parse(emailHtmlDocument("""
            <a id="script" href="javascript:alert(1)">Script</a><a id="encoded" href="java&#x73;cript:alert(1)">Encoded</a>
            <a id="file" href="file:///etc/passwd">File</a><a id="data" href="data:text/html,test">Data</a>
            <a id="relative" href="/settings">Relative</a><a id="intent" href="intent://scan">Intent</a>
            <a id="https" href="https://example.com/page?q=1&amp;x=2">Secure</a>
            <a id="http" href="http://example.com/">Web</a><a id="email" href="mailto:hello@example.com">Email</a>
        """.trimIndent()))

        for (id in listOf("script", "encoded", "file", "data", "relative", "intent")) {
            assertFalse(id, document.getElementById(id)!!.hasAttr("href"))
        }
        assertEquals("https://example.com/page?q=1&x=2", document.getElementById("https")!!.attr("href"))
        assertEquals("http://example.com/", document.getElementById("http")!!.attr("href"))
        assertEquals("mailto:hello@example.com", document.getElementById("email")!!.attr("href"))
    }

    @Test
    fun `external links require supported schemes and valid destinations`() {
        for (url in listOf("https://example.com/path", "http://example.com/", "mailto:hello@example.com")) {
            assertTrue(url, isExternalEmailLink(url))
        }
        for (url in listOf("", "/relative", "https:", "https:///missing-host", "mailto:", "javascript:alert(1)", "data:text/html,hello", "file:///tmp/mail", "content://mail/1", "intent://scan")) {
            assertFalse(url, isExternalEmailLink(url))
        }
    }

    @Test
    fun `document policy permits embedded images and inline styles while blocking remote resources`() {
        val document = Jsoup.parse(emailHtmlDocument(
            "<style>@import url('https://evil.example/style.css');</style><img src='https://example.com/logo.png'>",
        ))
        val policy = document.select("meta[http-equiv=Content-Security-Policy]").single()
        val directives = policy.attr("content").split(';').map(String::trim)

        assertTrue(directives.contains("default-src 'none'"))
        assertTrue(directives.contains("style-src 'unsafe-inline'"))
        assertTrue(directives.contains("base-uri 'none'"))
        assertTrue(directives.contains("form-action 'none'"))
        assertTrue(directives.contains("frame-src 'none'"))
        assertTrue(directives.contains("img-src data:"))
        assertTrue(document.head().children().indexOf(policy) < document.head().children().indexOf(document.selectFirst("style")))
    }
}
