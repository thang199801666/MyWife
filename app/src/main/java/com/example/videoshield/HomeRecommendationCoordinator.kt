package com.example.videoshield

import android.content.Context
import android.webkit.WebView

/**
 * Owns Home discovery, ranking and rendering. Keeping this out of MainActivity makes one place
 * responsible for request generations and stale-result suppression across SPA navigation.
 */
class HomeRecommendationCoordinator(
    context: Context,
    private val preferences: ShieldPreferences,
    private val store: LibraryStore,
    private val searchHistory: SearchHistoryStore,
    private val tasks: SerialTaskQueue,
    private val webView: () -> WebView?,
    private val route: () -> YouTubeRoute,
    private val alive: () -> Boolean
) : AutoCloseable {
    private val presentationContext = context
    private var requestGeneration = 0L
    private var pageOffset = 0
    private var closed = false

    fun installBridge(view: WebView) {
        view.addJavascriptInterface(
            DiscoveryBridge(
                allowed = {
                    !closed && alive() &&
                        preferences.personalizedSuggestions && preferences.rememberHistory &&
                        YouTubeAdapter.isTrustedBridgeUrl(view.url) &&
                        route().destination == YouTubeDestination.HOME
                },
                accept = { payload -> acceptDiscovery(payload) }
            ),
            "YouTooBeeDiscovery"
        )
    }

    fun refresh() {
        if (closed || !alive() || tasks.isShutdown) return
        val view = webView() ?: return
        val currentRoute = route()
        val request = ++requestGeneration
        if (currentRoute.destination != YouTubeDestination.HOME) {
            tasks.cancelPending(RANKING_TASK)
            return
        }

        val enabled = preferences.personalizedSuggestions && preferences.rememberHistory
        val since = preferences.recommendationsSince
        val offset = pageOffset
        val searches = searchHistory.recent(24).filter { it.usedAt >= since }.map { it.query }
        val lightTheme = AppTheme.isLight(presentationContext)

        tasks.executeLatest(RANKING_TASK) {
            val ranked = if (enabled) {
                runCatching { store.recommendations(since, searchQueries = searches, maxPerChannel = 6) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val rows = if (ranked.size <= PAGE_SIZE) {
                ranked
            } else {
                val start = offset.mod(ranked.size)
                (ranked.drop(start) + ranked.take(start)).take(PAGE_SIZE)
            }
            val script = HomeRecommendationsScript.build(
                enabled,
                rows.map { it.copy(reason = LocalizedPresentation.recommendation(presentationContext, it.reason)) },
                "",
                "",
                lightTheme
            )
            view.post {
                if (closed || !alive() || request != requestGeneration ||
                    enabled != (preferences.personalizedSuggestions && preferences.rememberHistory) ||
                    since != preferences.recommendationsSince || offset != pageOffset ||
                    !YouTubeAdapter.isTrustedBridgeUrl(view.url) || route().destination != YouTubeDestination.HOME
                ) return@post
                view.evaluateJavascript(script, null)
            }
        }
    }

    fun rotatePage() {
        pageOffset += PAGE_SIZE
        ++requestGeneration
        tasks.cancelPending(RANKING_TASK)
    }

    fun resetPage() {
        if (pageOffset == 0) return
        pageOffset = 0
        ++requestGeneration
        tasks.cancelPending(RANKING_TASK)
    }

    private fun acceptDiscovery(payload: String) {
        if (closed || !alive() || tasks.isShutdown) return
        val capturedAt = System.currentTimeMillis()
        tasks.execute {
            if (!closed && preferences.personalizedSuggestions && preferences.rememberHistory &&
                capturedAt >= preferences.recommendationsSince
            ) {
                store.saveCandidates(DiscoveryBridge.parse(payload, capturedAt))
            }
            webView()?.post { if (!closed && alive()) refresh() }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ++requestGeneration
        tasks.cancelPending(RANKING_TASK)
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val RANKING_TASK = "home-ranking"
    }
}
