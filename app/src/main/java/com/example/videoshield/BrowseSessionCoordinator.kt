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
    private var lastRouteKey = ""
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
        val target = webView
        val routeKey = route.resourceKey()
        val routeChanged = lastRouteKey.isNotBlank() && lastRouteKey != routeKey

        if (route.destination != YouTubeDestination.SHORTS || route.videoId.isBlank()) {
            if (lastShortsVideoId.isNotBlank()) {
                // Release the last recycler media reference as soon as the SPA leaves Shorts.
                // Keeping even one detached <video> alive can retain native decoder buffers.
                target?.evaluateJavascript(ShortsResourceGuardScript.release(), null)
            }
            // Home/search/subscriptions can create transient media previews. Pause/downgrade
            // them on a real SPA route transition so old decoder buffers do not accumulate.
            if (routeChanged) target?.evaluateJavascript(BrowseResourceGuardScript.transition(), null)
            lastShortsVideoId = ""
            shortsTransitionCount = 0
            lastRouteKey = routeKey
            return
        }

        lastRouteKey = routeKey
        if (route.videoId == lastShortsVideoId) return
        lastShortsVideoId = route.videoId
        shortsTransitionCount++
        if (target == null) return

        // Keep the recycler's warm window bounded on every Short transition. This pass only
        // pauses/downgrades distant media in normal conditions; decoder reset is still reserved
        // for memory pressure, retained-video pressure, or the periodic hard pass.
        target.evaluateJavascript(ShortsResourceGuardScript.transition(), null)
        if (shortsTransitionCount % SHORTS_HARD_TRIM_EVERY == 0) {
            target.evaluateJavascript(ShortsResourceGuardScript.hardTrimDeferred(trimImages = false), null)
        }
    }

    private fun YouTubeRoute.resourceKey(): String = when (destination) {
        YouTubeDestination.SHORTS -> "SHORTS:$videoId"
        YouTubeDestination.SEARCH -> "SEARCH:$query"
        YouTubeDestination.WATCH -> "WATCH:$videoId"
        else -> "${destination.name}:${url.substringBefore('#')}"
    }

    override fun close() {
        if (closed) return
        flushBrowseUrl()
        closed = true
        main.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val SHORTS_PERSIST_DEBOUNCE_MS = 3_500L
        private const val SHORTS_HARD_TRIM_EVERY = 36
    }
}
