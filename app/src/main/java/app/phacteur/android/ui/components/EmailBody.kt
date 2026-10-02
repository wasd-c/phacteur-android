package app.phacteur.android.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@Composable
internal fun EmailBody(messageId: Int, body: String, htmlBody: String?, modifier: Modifier = Modifier) {
    val html = remember(body, htmlBody) { emailHtml(body, htmlBody) }
    if (html == null) {
        SelectionContainer(modifier) {
            Text(body.ifBlank { "Ce message ne contient pas de contenu." }, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    key(messageId, html) {
        val document = remember(html) { emailHtmlDocument(html) }
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    setBackgroundColor(Color.WHITE)
                    settings.apply {
                        javaScriptEnabled = false
                        javaScriptCanOpenWindowsAutomatically = false
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = false
                        databaseEnabled = false
                        setGeolocationEnabled(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        blockNetworkLoads = true
                        blockNetworkImage = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = EmailWebViewClient()
                }
            },
            update = { view ->
                if (view.tag != document) {
                    view.tag = document
                    view.loadDataWithBaseURL(EMAIL_BASE_URL, document, "text/html", "UTF-8", null)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.destroy()
            },
        )
    }
}

private class EmailWebViewClient : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        // EmailBody lives in the mailbox scroll column or a conversation's lazy item.
        view.requestLayout()
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // Only self-contained images may load; no network, file or content URLs.
        if (!request.isForMainFrame && request.url.toString().startsWith("data:image/", ignoreCase = true)) return null
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.isForMainFrame && request.hasGesture() &&
            request.url.host != "email.invalid" && isExternalEmailLink(request.url.toString())) {
            try {
                view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: ActivityNotFoundException) {
                // Some devices have no handler for mailto links.
            } catch (_: SecurityException) {
                // A device policy may prevent opening an external application.
            }
        }
        return true
    }
}
