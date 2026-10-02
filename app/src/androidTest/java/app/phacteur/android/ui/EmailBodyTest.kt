package app.phacteur.android.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.phacteur.android.ui.components.EmailBody
import app.phacteur.android.ui.theme.PhacteurTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailBodyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun plainTextKeepsLiteralComparisonsAndEmailAddresses() {
        val message = "Contact Alice <a@example.com> when 2 < 3 and 5 > 4."
        compose.setContent {
            ScrollingEmail(messageId = 1, body = message, htmlBody = null)
        }

        compose.onNodeWithText(message).assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
    }

    @Test
    fun emptyMessageShowsThePlainTextPlaceholder() {
        compose.setContent {
            ScrollingEmail(messageId = 1, body = "", htmlBody = null)
        }

        compose.onNodeWithText("Ce message ne contient pas de contenu.").assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
    }

    @Test
    fun htmlOnlyMessageHasVisibleContentInsideTheScrollColumnAndBlocksActiveAccess() {
        val html = "<html><body><h1>Bonjour</h1><table><tr><td>Message HTML</td></tr></table></body></html>"
        compose.setContent {
            ScrollingEmail(messageId = 1, body = "", htmlBody = html)
        }

        val webView = awaitVisibleHtml()
        compose.onNodeWithText(html).assertDoesNotExist()
        compose.onNodeWithText("Ce message ne contient pas de contenu.").assertDoesNotExist()
        compose.runOnIdle {
            with(webView.settings) {
                assertFalse(javaScriptEnabled)
                assertFalse(javaScriptCanOpenWindowsAutomatically)
                assertFalse(allowFileAccess)
                assertFalse(allowContentAccess)
                assertFalse(domStorageEnabled)
                assertTrue(blockNetworkLoads)
                assertTrue(blockNetworkImage)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, mixedContentMode)
            }
            assertNull(webView.webViewClient.shouldInterceptRequest(webView, ImageRequest("data:image/png;base64,AA==")))
            for (url in listOf("https://example.com/tracker.png", "file:///data/local/image.png", "content://mail/1")) {
                assertNotNull(webView.webViewClient.shouldInterceptRequest(webView, ImageRequest(url)))
            }
        }
    }

    @Test
    fun switchingToAPlainMessageRemovesThePreviousHtmlView() {
        var showHtml by mutableStateOf(true)
        val plainMessage = "Le nouveau message est en texte simple."
        compose.setContent {
            ScrollingEmail(
                messageId = if (showHtml) 1 else 2,
                body = if (showHtml) "" else plainMessage,
                htmlBody = if (showHtml) "<p>Ancien message HTML</p>" else null,
            )
        }

        val previousWebView = awaitVisibleHtml()
        compose.runOnIdle { showHtml = false }

        compose.onNodeWithText(plainMessage).assertIsDisplayed()
        onView(isAssignableFrom(WebView::class.java)).check(doesNotExist())
        compose.runOnIdle {
            assertFalse("The previous email must no longer be attached", previousWebView.isAttachedToWindow)
        }
    }

    private fun awaitVisibleHtml(): WebView {
        lateinit var webView: WebView
        onView(isAssignableFrom(WebView::class.java)).check { view, error ->
            if (error != null) throw error
            webView = view as WebView
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.runOnIdle { webView.contentHeight > 0 && webView.measuredHeight > 0 }
        }
        onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
        return webView
    }
}

private class ImageRequest(private val url: String) : WebResourceRequest {
    override fun getUrl(): Uri = Uri.parse(url)
    override fun isForMainFrame(): Boolean = false
    override fun isRedirect(): Boolean = false
    override fun hasGesture(): Boolean = false
    override fun getMethod(): String = "GET"
    override fun getRequestHeaders(): Map<String, String> = emptyMap()
}

@Composable
private fun ScrollingEmail(messageId: Int, body: String, htmlBody: String?) {
    PhacteurTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EmailBody(messageId = messageId, body = body, htmlBody = htmlBody)
        }
    }
}
