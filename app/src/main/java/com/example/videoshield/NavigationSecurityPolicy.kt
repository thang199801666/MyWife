package com.example.videoshield

import java.net.URI

enum class NavigationAction {
    ALLOW_IN_WEBVIEW,
    OPEN_EXTERNAL,
    BLOCK
}

data class NavigationDecision(
    val action: NavigationAction,
    val reason: String = ""
)

/**
 * Central policy for non-HTTP navigation emitted by WebView pages.
 * HTTP(S) stays inside WebView. External app launches are accepted only when the
 * current page is a trusted YouTube origin; executable/local-content schemes are always blocked.
 */
object NavigationSecurityPolicy {
    private val externalSchemes = setOf("mailto", "tel", "sms", "geo", "market", "vnd.youtube")
    private val forbiddenSchemes = setOf("javascript", "file", "content", "data", "blob")

    fun decide(targetUrl: String, sourceTrusted: Boolean): NavigationDecision {
        val scheme = schemeOf(targetUrl)
            ?: return NavigationDecision(NavigationAction.BLOCK, "missing or malformed scheme")

        return when {
            scheme == "http" || scheme == "https" -> NavigationDecision(NavigationAction.ALLOW_IN_WEBVIEW)
            scheme in forbiddenSchemes -> NavigationDecision(NavigationAction.BLOCK, "forbidden $scheme scheme")
            scheme == "intent" && sourceTrusted -> NavigationDecision(NavigationAction.OPEN_EXTERNAL)
            scheme == "intent" -> NavigationDecision(NavigationAction.BLOCK, "intent launch from untrusted page")
            scheme in externalSchemes && sourceTrusted -> NavigationDecision(NavigationAction.OPEN_EXTERNAL)
            scheme in externalSchemes -> NavigationDecision(NavigationAction.BLOCK, "$scheme launch from untrusted page")
            else -> NavigationDecision(NavigationAction.BLOCK, "unsupported $scheme scheme")
        }
    }

    private fun schemeOf(value: String): String? = try {
        URI(value.trim()).scheme?.lowercase()?.takeIf { it.matches(Regex("[a-z][a-z0-9+.-]*")) }
    } catch (_: Exception) {
        null
    }
}
