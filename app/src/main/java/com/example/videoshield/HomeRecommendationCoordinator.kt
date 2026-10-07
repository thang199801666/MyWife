package com.example.videoshield

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView

/**
 * Owns Home discovery, ranking and rendering. Keeping this out of MainActivity makes one place
 * responsible for request generations and stale-result suppression across SPA navigation.
 *
 * Cold-start ranking can be prepared before Chromium's first visual commit. The finished script is
 * held on the main thread and published only after the browse document is visible, allowing local
 * I/O/ranking to overlap network load without competing with the first renderer frame.
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
    private val presentationContext = context.applicationContext.createConfigurationContext(context.resources.configuration)
    private var requestGeneration = 0L
    private var pageOffset = 0
    private var closed = false
    private var discoveryBridge: DiscoveryBridge? = null
    private var documentVisualCommitted = false
    private var publishedForCurrentHome = false
    private var firstPaintPreparationActive = false
    private var preparedScript: String? = null
    private var preparedRequestGeneration = -1L
    private val main = Handler(Looper.getMainLooper())
    private var lastFeedInteractionAtMs = 0L
    private var pendingPublish: Runnable? = null

    /** Called from the native WebView scroll callback; no work is executed on the hot path. */
    fun noteFeedInteraction() {
        if (closed) return
        lastFeedInteractionAtMs = SystemClock.uptimeMillis()
        if (preparedScript != null && documentVisualCommitted) schedulePreparedPublish()
    }

    private fun cancelPendingPublish() {
        pendingPublish?.let(main::removeCallbacks)
        pendingPublish = null
    }

    private fun schedulePreparedPublish() {
        if (closed || preparedScript == null || !documentVisualCommitted) return
        cancelPendingPublish()
        val now = SystemClock.uptimeMillis()
        val remaining = FeedInteractionBudgetPolicy.remainingQuietMs(
            nowMs = now,
            lastInteractionMs = lastFeedInteractionAtMs,
            quietWindowMs = FeedInteractionBudgetPolicy.HOME_PUBLISH_QUIET_WINDOW_MS
        )
        val task = Runnable {
            pendingPublish = null
            publishPreparedIfPossible()
        }
        pendingPublish = task
        if (remaining <= 0L) main.post(task) else main.postDelayed(task, remaining)
    }

    fun installBridge(view: WebView) {
        discoveryBridge?.close()
        val bridge = DiscoveryBridge(
            allowed = {
                !closed && alive() &&
                    preferences.personalizedSuggestions && preferences.rememberHistory &&
                    YouTubeAdapter.isTrustedBridgeUrl(view.url) &&
                    route().destination == YouTubeDestination.HOME
            },
            accept = { payload -> acceptDiscovery(payload) }
        )
        discoveryBridge = bridge
        view.addJavascriptInterface(bridge, "YouTooBeeDiscovery")
    }

    /** Background-prepares Home content without touching the DOM before first visual commit. */
    fun prepareForFirstPaint() {
        if (firstPaintPreparationActive || closed || !alive() || tasks.isShutdown ||
            route().destination != YouTubeDestination.HOME) return
        firstPaintPreparationActive = true
        enqueueRefresh(showLoading = false, holdUntilVisualCommit = true)
    }

    /** Full-document navigation invalidates the visual gate; SPA navigation does not call this. */
    fun onNavigationStarted() {
        documentVisualCommitted = false
        publishedForCurrentHome = false
        firstPaintPreparationActive = false
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
    }

    /** Called from WebView.onPageCommitVisible, earlier than onPageFinished. */
    fun onFirstVisualCommit(url: String) {
        if (closed || !alive() || !YouTubeAdapter.isTrustedBridgeUrl(url)) return
        documentVisualCommitted = true
        if (preparedScript != null) schedulePreparedPublish()
    }

    fun onDestinationChanged(destination: YouTubeDestination) {
        if (destination == YouTubeDestination.HOME) return
        publishedForCurrentHome = false
        firstPaintPreparationActive = false
        ++requestGeneration
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
        tasks.cancelPending(RANKING_TASK)
    }

    /** Page-finished fallback: publish prepared work, otherwise refresh without adding a skeleton. */
    fun publishPreparedOrRefresh() {
        if (route().destination != YouTubeDestination.HOME || publishedForCurrentHome) return
        if (!publishPreparedIfPossible()) refresh(showLoading = false)
    }

    fun refresh(showLoading: Boolean = true) {
        enqueueRefresh(showLoading = showLoading, holdUntilVisualCommit = false)
    }

    private fun enqueueRefresh(showLoading: Boolean, holdUntilVisualCommit: Boolean) {
        if (closed || !alive() || tasks.isShutdown) {
            if (holdUntilVisualCommit) firstPaintPreparationActive = false
            return
        }
        val view = webView() ?: run {
            if (holdUntilVisualCommit) firstPaintPreparationActive = false
            return
        }
        val currentRoute = route()
        val request = ++requestGeneration
        if (currentRoute.destination != YouTubeDestination.HOME) {
            if (holdUntilVisualCommit) firstPaintPreparationActive = false
            preparedScript = null
            preparedRequestGeneration = -1L
            tasks.cancelPending(RANKING_TASK)
            return
        }

        val historyEnabled = preferences.rememberHistory
        val recommendationsEnabled = preferences.personalizedSuggestions && historyEnabled
        val renderEnabled = historyEnabled
        val since = preferences.recommendationsSince
        val offset = pageOffset
        val searches = if (recommendationsEnabled) {
            searchHistory.recent(24).filter { it.usedAt >= since }.map { it.query }
        } else emptyList()
        val lightTheme = AppTheme.isLight(presentationContext)

        if (showLoading && renderEnabled && YouTubeAdapter.isTrustedBridgeUrl(view.url)) {
            view.evaluateJavascript(HomeRecommendationsScript.loading(true, lightTheme), null)
        }

        val accepted = tasks.executeLatest(RANKING_TASK) {
            val continueRows = if (historyEnabled) {
                runCatching { store.continueWatching(MAX_CONTINUE_ROWS) }.getOrDefault(emptyList())
            } else emptyList()
            val continueIds = continueRows.mapTo(HashSet()) { it.videoId }

            val ranked = if (recommendationsEnabled) {
                runCatching { store.recommendations(since, searchQueries = searches, maxPerChannel = 6) }
                    .getOrDefault(emptyList())
                    .filterNot { it.video.videoId in continueIds }
            } else emptyList()

            val rows = if (ranked.size <= PAGE_SIZE) {
                ranked
            } else {
                val start = offset.mod(ranked.size)
                (ranked.drop(start) + ranked.take(start)).take(PAGE_SIZE)
            }
            val script = HomeRecommendationsScript.build(
                enabled = renderEnabled,
                rows = rows.map { it.copy(reason = LocalizedPresentation.recommendation(presentationContext, it.reason)) },
                continueRows = continueRows,
                continueHeading = presentationContext.getString(R.string.ui_continue_watching),
                heading = "",
                hint = "",
                lightTheme = lightTheme
            )
            view.post {
                if (holdUntilVisualCommit) firstPaintPreparationActive = false
                if (!isRequestStillValid(
                        request = request,
                        historyEnabled = historyEnabled,
                        recommendationsEnabled = recommendationsEnabled,
                        since = since,
                        offset = offset,
                        view = view
                    )
                ) return@post

                val quietRemaining = FeedInteractionBudgetPolicy.remainingQuietMs(
                    nowMs = SystemClock.uptimeMillis(),
                    lastInteractionMs = lastFeedInteractionAtMs,
                    quietWindowMs = FeedInteractionBudgetPolicy.HOME_PUBLISH_QUIET_WINDOW_MS
                )
                if ((holdUntilVisualCommit && !documentVisualCommitted) || quietRemaining > 0L) {
                    preparedScript = script
                    preparedRequestGeneration = request
                    if (documentVisualCommitted) schedulePreparedPublish()
                } else {
                    cancelPendingPublish()
                    preparedScript = null
                    preparedRequestGeneration = -1L
                    publishedForCurrentHome = true
                    view.evaluateJavascript(script, null)
                }
            }
        }
        if (!accepted && holdUntilVisualCommit) firstPaintPreparationActive = false
    }

    private fun publishPreparedIfPossible(): Boolean {
        val script = preparedScript ?: return false
        val request = preparedRequestGeneration
        if (!documentVisualCommitted || request != requestGeneration) return false
        val view = webView() ?: return false
        if (!alive() || !YouTubeAdapter.isTrustedBridgeUrl(view.url) || route().destination != YouTubeDestination.HOME) {
            return false
        }
        val remaining = FeedInteractionBudgetPolicy.remainingQuietMs(
            nowMs = SystemClock.uptimeMillis(),
            lastInteractionMs = lastFeedInteractionAtMs,
            quietWindowMs = FeedInteractionBudgetPolicy.HOME_PUBLISH_QUIET_WINDOW_MS
        )
        if (remaining > 0L) {
            schedulePreparedPublish()
            return true
        }
        cancelPendingPublish()
        preparedScript = null
        preparedRequestGeneration = -1L
        publishedForCurrentHome = true
        view.evaluateJavascript(script, null)
        return true
    }

    private fun isRequestStillValid(
        request: Long,
        historyEnabled: Boolean,
        recommendationsEnabled: Boolean,
        since: Long,
        offset: Int,
        view: WebView
    ): Boolean = !closed && alive() && request == requestGeneration &&
        historyEnabled == preferences.rememberHistory &&
        recommendationsEnabled == (preferences.personalizedSuggestions && preferences.rememberHistory) &&
        since == preferences.recommendationsSince && offset == pageOffset &&
        YouTubeAdapter.isTrustedBridgeUrl(view.url) && route().destination == YouTubeDestination.HOME

    fun rotatePage() {
        pageOffset += PAGE_SIZE
        ++requestGeneration
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
        tasks.cancelPending(RANKING_TASK)
    }

    fun resetPage() {
        if (pageOffset == 0) return
        pageOffset = 0
        ++requestGeneration
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
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
            webView()?.post { if (!closed && alive()) refresh(showLoading = false) }
        }
    }

    fun trimMemory(tier: MemoryPressureTier) {
        if (closed || !tier.atLeast(MemoryPressureTier.MODERATE)) return
        ++requestGeneration
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
        tasks.cancelPending(RANKING_TASK)
        if (tier.atLeast(MemoryPressureTier.LOW)) pageOffset = 0
    }

    fun onRendererGone() {
        if (closed) return
        ++requestGeneration
        documentVisualCommitted = false
        publishedForCurrentHome = false
        firstPaintPreparationActive = false
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
        tasks.cancelPending(RANKING_TASK)
        discoveryBridge?.close()
        discoveryBridge = null
    }

    override fun close() {
        if (closed) return
        closed = true
        ++requestGeneration
        documentVisualCommitted = false
        publishedForCurrentHome = false
        firstPaintPreparationActive = false
        preparedScript = null
        preparedRequestGeneration = -1L
        cancelPendingPublish()
        tasks.cancelPending(RANKING_TASK)
        discoveryBridge?.close()
        discoveryBridge = null
        runCatching { webView()?.removeJavascriptInterface("YouTooBeeDiscovery") }
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val MAX_CONTINUE_ROWS = 8
        private const val RANKING_TASK = "home-ranking"
    }
}
