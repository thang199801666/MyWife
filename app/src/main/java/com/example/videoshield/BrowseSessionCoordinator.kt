package com.example.videoshield

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/** Owns browse-only session bookkeeping so MainActivity does not carry delayed persistence and
 * Shorts memory cadence state. */
class BrowseSessionCoordinator(
    private val preferences: ShieldPreferences
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var pendingBrowseUrl: String? = null
    private var lastShortsVideoId = ""
    private var shortsTransitionCount = 0
    private var closed = false
    private val persistTask = Runnable { flushBrowseUrl() }

    fun persistBrowseUrl(url: String, route: YouTubeRoute, immediate: Boolean = false) {
        if (closed || url.isBlank()) return
        if (route.destination == YouTubeDestination.SHORTS && !immediate) {
            pendingBrowseUrl = url
            main.removeCallbacks(persistTask)
            main.postDelayed(persistTask, SHORTS_PERSIST_DEBOUNCE_MS)
            return
        }
        flushBrowseUrl()
        if (preferences.lastBrowseUrl != url) preferences.lastBrowseUrl = url
    }

    fun flushBrowseUrl() {
        val pending = pendingBrowseUrl ?: return
        pendingBrowseUrl = null
        if (preferences.lastBrowseUrl != pending) preferences.lastBrowseUrl = pending
    }

    fun onBrowseRoute(route: YouTubeRoute, webView: WebView?) {
        if (route.destination != YouTubeDestination.SHORTS || route.videoId.isBlank()) {
            lastShortsVideoId = ""
            shortsTransitionCount = 0
            return
        }
        if (route.videoId == lastShortsVideoId) return
        lastShortsVideoId = route.videoId
        shortsTransitionCount++
        val target = webView ?: return
        when {
            shortsTransitionCount % SHORTS_HARD_TRIM_EVERY == 0 ->
                target.evaluateJavascript(ShortsResourceGuardScript.trim(true, trimImages = false), null)
            shortsTransitionCount % SHORTS_SOFT_TRIM_EVERY == 0 ->
                target.evaluateJavascript(ShortsResourceGuardScript.trim(false), null)
        }
    }

    override fun close() {
        if (closed) return
        flushBrowseUrl()
        closed = true
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val SHORTS_PERSIST_DEBOUNCE_MS = 3_500L
        private const val SHORTS_SOFT_TRIM_EVERY = 6
        private const val SHORTS_HARD_TRIM_EVERY = 30
    }
}
