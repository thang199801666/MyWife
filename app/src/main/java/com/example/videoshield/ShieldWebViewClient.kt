package com.example.videoshield

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import java.io.ByteArrayInputStream

class ShieldWebViewClient(
    private val filterEngine: FilterEngine,
    private val scriptProvider: () -> String,
    private val onBlocked: (Uri) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val onNavigationStarted: (String) -> Unit,
    private val onPageReady: (String) -> Unit,
    private val onMainFrameError: (String) -> Unit,
    private val onNavigationBlocked: (target: String, reason: String) -> Unit,
    private val onRendererGone: (didCrash: Boolean) -> Unit,
    private val navigationInterceptor: (String) -> Boolean = { false }
) : WebViewClient() {
    private var injectedUrl: String? = null

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        // A search term or video ID may contain an ad-rule fragment. Keep trusted
        // YouTube documents navigable; their ad/tracking subresources are still filtered.
        if (request.isForMainFrame && YouTubeAdapter.isTrustedBridgeUrl(uri.toString())) return null
        return if (filterEngine.shouldBlock(uri)) {
            onBlocked(uri)
            emptyResponse()
        } else null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) {
            injectedUrl = null
            onNavigationStarted(url)
            onUrlChanged(url)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) {
            onUrlChanged(url)
            onPageReady(url)
        }
        injectShield(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        // pushState/replaceState navigation does not trigger onPageStarted.
        if (url != null && url == view?.url) onUrlChanged(url)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        injectShield(view, url)
    }

    private fun injectShield(view: WebView?, url: String?) {
        val current = view?.url ?: return
        if (!YouTubeAdapter.isTrustedBridgeUrl(url) || !YouTubeAdapter.isTrustedBridgeUrl(current)) return
        if (injectedUrl == current) return
        injectedUrl = current
        view.evaluateJavascript(scriptProvider(), null)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onMainFrameError(error?.description?.toString().orEmpty())
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
            onMainFrameError("HTTP ${errorResponse?.statusCode ?: 0}")
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val didCrash = detail?.didCrash() == true
        // Android requires the dead WebView instance to be removed and destroyed
        // before the app continues after renderer termination.
        try {
            view?.let {
                (it.parent as? ViewGroup)?.removeView(it)
                it.destroy()
            }
        } catch (_: Exception) {}
        onRendererGone(didCrash)
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val rewrite = filterEngine.rewriteNavigation(uri)
        if (rewrite != null) {
            view?.loadUrl(rewrite.toString())
            return true
        }

        val target = uri.toString()
        val decision = NavigationSecurityPolicy.decide(
            targetUrl = target,
            sourceTrusted = YouTubeAdapter.isTrustedBridgeUrl(view?.url)
        )
        return when (decision.action) {
            NavigationAction.ALLOW_IN_WEBVIEW -> navigationInterceptor(target)
            NavigationAction.BLOCK -> {
                onNavigationBlocked(target, decision.reason)
                true
            }
            NavigationAction.OPEN_EXTERNAL -> openExternalNavigation(view, uri, decision.reason)
        }
    }

    private fun openExternalNavigation(view: WebView?, uri: Uri, policyReason: String): Boolean {
        val context = view?.context ?: run {
            onNavigationBlocked(uri.toString(), "missing WebView context")
            return true
        }

        return try {
            if (uri.scheme.equals("intent", true)) {
                val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                    `package` = null
                }
                val fallback = intent.getStringExtra("browser_fallback_url")
                intent.removeExtra("browser_fallback_url")

                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                } else if (fallback != null && NavigationSecurityPolicy.decide(fallback, sourceTrusted = true).action == NavigationAction.ALLOW_IN_WEBVIEW) {
                    view.loadUrl(fallback)
                } else {
                    onNavigationBlocked(uri.toString(), "no safe external handler")
                }
            } else {
                val external = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                    selector = null
                    `package` = null
                }
                if (context.packageManager.resolveActivity(external, 0) != null) {
                    context.startActivity(external)
                } else {
                    onNavigationBlocked(uri.toString(), "no external handler for ${uri.scheme}")
                }
            }
            true
        } catch (_: Exception) {
            onNavigationBlocked(uri.toString(), policyReason.ifBlank { "external launch failed" })
            true
        }
    }

    private fun emptyResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
    )
}
