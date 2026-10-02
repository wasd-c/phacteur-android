package app.phacteur.android.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailParsingTest {
    @Test
    fun mailboxKeepsHtmlAlongsidePlainText() {
        val html = "<table><tr><td><strong>Bonjour</strong> &amp; bienvenue</td></tr></table>"
        val email = parseEmail(
            JSONObject().put("id", 42)
                .put("body", "Bonjour & bienvenue")
                .put("htmlBody", html),
        )

        assertEquals("Bonjour & bienvenue", email.body)
        assertEquals(html, email.htmlBody)
    }

    @Test
    fun conversationKeepsHtmlAlongsidePlainText() {
        val html = "<p>Réponse avec un <a href=\"https://example.com\">lien</a>.</p>"
        val message = parseThreadMessage(
            JSONObject().put("id", 43)
                .put("body", "Réponse avec un lien.")
                .put("htmlBody", html),
        )

        assertEquals("Réponse avec un lien.", message.body)
        assertEquals(html, message.htmlBody)
    }

    @Test
    fun htmlOnlyMessagesDoNotShowJsonNullAsText() {
        val payload = JSONObject().put("id", 44)
            .put("body", JSONObject.NULL)
            .put("htmlBody", "<p>Contenu HTML seul</p>")

        val email = parseEmail(payload)
        val message = parseThreadMessage(payload)

        assertEquals("", email.body)
        assertEquals("<p>Contenu HTML seul</p>", email.htmlBody)
        assertEquals("", message.body)
        assertEquals(email.htmlBody, message.htmlBody)
    }

    @Test
    fun missingNullAndBlankHtmlKeepPlainTextMessagesCompatible() {
        val payloads = listOf(
            JSONObject().put("id", 45).put("body", "Version texte"),
            JSONObject().put("id", 46).put("body", "Version texte").put("htmlBody", JSONObject.NULL),
            JSONObject().put("id", 47).put("body", "Version texte").put("htmlBody", " \n "),
        )

        payloads.forEach { payload ->
            assertEquals("Version texte", parseEmail(payload).body)
            assertNull(parseEmail(payload).htmlBody)
            assertEquals("Version texte", parseThreadMessage(payload).body)
            assertNull(parseThreadMessage(payload).htmlBody)
        }
    }
}
