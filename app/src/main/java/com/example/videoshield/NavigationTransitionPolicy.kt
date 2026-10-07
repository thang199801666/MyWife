package com.example.videoshield

/**
 * Pure decisions for cross-surface navigation.
 *
 * YouTube SPA navigation can surface the same transition through a JS bridge,
 * shouldOverrideUrlLoading, onPageStarted and visited-history callbacks. Keeping the
 * decision separate makes those callbacks idempotent instead of repeatedly relaying out
 * the player or refreshing native chrome.
 */
enum class PlayerNavigationAction {
    NO_OP,
    EXPAND_EXISTING,
    MINIMIZE_EXISTING,
    LOAD_TARGET
}

object NavigationTransitionPolicy {
    fun playerAction(
        requestedVideoId: String,
        activeRouteVideoId: String,
        loadedVideoId: String?,
        surfaceVisible: Boolean,
        surfaceExpanded: Boolean,
        expandRequested: Boolean
    ): PlayerNavigationAction {
        if (requestedVideoId.isBlank()) return PlayerNavigationAction.NO_OP
        val sameVideo = requestedVideoId == activeRouteVideoId || requestedVideoId == loadedVideoId
        if (!sameVideo || !surfaceVisible) return PlayerNavigationAction.LOAD_TARGET
        return when {
            expandRequested && !surfaceExpanded -> PlayerNavigationAction.EXPAND_EXISTING
            !expandRequested && surfaceExpanded -> PlayerNavigationAction.MINIMIZE_EXISTING
            else -> PlayerNavigationAction.NO_OP
        }
    }

    /**
     * Native chrome only depends on the browse destination, except Search where the query is
     * user-visible. Shorts media-id changes deliberately share one key so vertical swipes do
     * not cause an Android UI redraw.
     */
    fun browseUiKey(destination: String, query: String, detail: String = ""): String =
        if (destination == "SEARCH") "SEARCH:${query.trim()}:${detail.trim()}" else destination
}
